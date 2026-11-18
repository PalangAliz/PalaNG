package com.example.core

import android.net.Uri
import android.os.Build
import android.util.Base64
import com.example.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class AmirLocalProxy(
    private val serverConfig: ServerConfig,
    private val protectSocket: (Socket) -> Boolean = { true },
    private val onLog: (String) -> Unit = {}
) {
    private val proxyExecutor = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val running = AtomicBoolean(false)
    private val loggedCleanIpFallback = AtomicBoolean(false)
    private val cleanIpFailureCount = AtomicInteger(0)
    private val cleanIpCooldownUntilMs = AtomicLong(0L)

    @Volatile
    private var serverSocket: ServerSocket? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: 0

    private val workerUrl: String = ConfigParser.normalizeAmirWorkerUrl(
        serverConfig.extraParam1.ifBlank { serverConfig.address }
    )
    private val parsedWorkerUri: Uri = Uri.parse(workerUrl)
    private val isTls: Boolean = !workerUrl.startsWith("ws://", ignoreCase = true)
    private val workerHost: String = parsedWorkerUri.host.orEmpty().ifBlank { serverConfig.address.trim() }
    private val workerPort: Int = if (parsedWorkerUri.port > 0) {
        parsedWorkerUri.port
    } else if (isTls) {
        443
    } else {
        80
    }
    private val requestTarget: String = buildString {
        val path = parsedWorkerUri.encodedPath?.takeIf { it.isNotEmpty() } ?: "/"
        append(path)
        val query = parsedWorkerUri.encodedQuery
        if (!query.isNullOrEmpty()) {
            append('?')
            append(query)
        }
    }
    private val httpHostHeader: String = if ((isTls && workerPort == 443) || (!isTls && workerPort == 80)) {
        workerHost
    } else {
        "$workerHost:$workerPort"
    }
    private val cleanIp: String = serverConfig.extraParam2.trim().removePrefix("[").removeSuffix("]")
    private val uuidBytes: ByteArray = serverConfig.passwordOrId.trim().toByteArray(StandardCharsets.UTF_8)

    fun start(): Int {
        val ss = ServerSocket(0, 1024, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        running.set(true)
        val boundPort = ss.localPort
        onLog(
            "Amir local SOCKS5 listening on 127.0.0.1:$boundPort -> $workerHost" +
                if (cleanIp.isNotBlank()) " (CF Clean IP: $cleanIp)" else ""
        )

        proxyExecutor.execute {
            while (running.get() && !ss.isClosed) {
                val clientSocket = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                proxyExecutor.execute {
                    handleSocksClient(clientSocket)
                }
            }
        }
        return boundPort
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSockets.forEach { sock ->
            runCatching { sock.close() }
        }
        activeSockets.clear()
        runCatching { proxyExecutor.shutdownNow() }
    }

    private fun handleSocksClient(localSocket: Socket) {
        activeSockets.add(localSocket)
        var wsConn: DirectWsConnection? = null
        try {
            localSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
            localSocket.tcpNoDelay = true
            val localIn = BufferedInputStream(localSocket.getInputStream(), IO_BUFFER_SIZE)
            val localOut = localSocket.getOutputStream()

            // 1. SOCKS5 Greeting
            val header = readExactBytes(localIn, 2)
            if (header[0].toInt() != 5) return
            val nMethods = header[1].toInt() and 0xFF
            if (nMethods > 0) readExactBytes(localIn, nMethods)
            localOut.write(byteArrayOf(0x05, 0x00))
            localOut.flush()

            // 2. SOCKS5 Connect Request
            val req = readExactBytes(localIn, 4)
            if (req[0].toInt() != 5) return
            if (req[1].toInt() != 1) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }
            val atyp = req[3].toInt() and 0xFF
            val targetHost = when (atyp) {
                1 -> {
                    val addr = readExactBytes(localIn, 4)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                3 -> {
                    val len = readExactBytes(localIn, 1)[0].toInt() and 0xFF
                    String(readExactBytes(localIn, len), StandardCharsets.UTF_8)
                }
                4 -> {
                    val addr = readExactBytes(localIn, 16)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                else -> return
            }
            val portBytes = readExactBytes(localIn, 2)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // 3. Open direct TLS + RFC-6455 WebSocket to Cloudflare Worker (matches Cl4.py)
            wsConn = openWebSocket()
            if (wsConn == null) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }

            // 4. Send Amir init packet
            val (initPacket, key) = buildInitPacket(uuidBytes, targetHost, targetPort)
            wsConn.sendBinaryFrame(initPacket, 0, initPacket.size, mask = null)
            wsConn.out.flush()

            // 5. Receive and verify HMAC("OK") acknowledgement
            val ack = wsConn.readMessageBytes()
            if (ack == null || ack.size < 16) return
            val decryptedAck = xorBytes(ack, key)
            val expectedAck = hmacSha256(key, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (!decryptedAck.contentEquals(expectedAck)) return

            // Switch both sockets to infinite idle timeout for long-lived streams (e.g., Telegram MTProto)
            localSocket.soTimeout = 0
            wsConn.socket.soTimeout = 0

            // 6. Send SOCKS5 success response
            localOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            localOut.flush()

            // 7. Full-duplex zero-allocation XOR relay
            val upFuture = proxyExecutor.submit {
                relayUp(localIn, wsConn, key)
            }
            relayDown(wsConn, localOut, key)
            runCatching { upFuture.cancel(false) }
        } catch (_: Exception) {
        } finally {
            activeSockets.remove(localSocket)
            wsConn?.let { conn ->
                activeSockets.remove(conn.socket)
                runCatching { conn.socket.close() }
            }
            runCatching { localSocket.close() }
        }
    }

    private fun relayUp(
        localIn: InputStream,
        wsConn: DirectWsConnection,
        key: ByteArray
    ) {
        val frameBuf = ByteArray(WS_MAX_HEADER_BYTES + MAX_CHUNK)
        val combinedXor = ByteArray(16)
        val mask = ByteArray(4)
        var maskSeed = System.nanoTime() xor (Thread.currentThread().id shl 16) xor -0x61c8864680b583ebL
        var hadWriteError = false
        try {
            while (running.get() && !wsConn.socket.isClosed) {
                val read = localIn.read(frameBuf, WS_MAX_HEADER_BYTES, MAX_CHUNK)
                if (read <= 0) break

                // Fast PRNG 4-byte RFC-6455 masking key
                maskSeed = maskSeed xor (maskSeed shl 13)
                maskSeed = maskSeed xor (maskSeed ushr 7)
                maskSeed = maskSeed xor (maskSeed shl 17)
                mask[0] = (maskSeed and 0xFF).toByte()
                mask[1] = ((maskSeed ushr 8) and 0xFF).toByte()
                mask[2] = ((maskSeed ushr 16) and 0xFF).toByte()
                mask[3] = ((maskSeed ushr 24) and 0xFF).toByte()

                // Combine 16-byte XOR stream key and 4-byte WS mask in a single pass (16 is divisible by 4)
                for (i in 0 until 16) {
                    combinedXor[i] = (key[i].toInt() xor mask[i and 3].toInt()).toByte()
                }

                var k = 0
                val end = WS_MAX_HEADER_BYTES + read
                for (idx in WS_MAX_HEADER_BYTES until end) {
                    frameBuf[idx] = (frameBuf[idx].toInt() xor combinedXor[k].toInt()).toByte()
                    k = (k + 1) and 15
                }

                val headerStart: Int
                val headerLen: Int
                if (read <= 125) {
                    headerLen = 6
                    headerStart = WS_MAX_HEADER_BYTES - 6
                    frameBuf[headerStart] = 0x82.toByte()
                    frameBuf[headerStart + 1] = (0x80 or read).toByte()
                } else {
                    headerLen = 8
                    headerStart = WS_MAX_HEADER_BYTES - 8
                    frameBuf[headerStart] = 0x82.toByte()
                    frameBuf[headerStart + 1] = (0x80 or 126).toByte()
                    frameBuf[headerStart + 2] = ((read ushr 8) and 0xFF).toByte()
                    frameBuf[headerStart + 3] = (read and 0xFF).toByte()
                }
                val maskOffset = headerStart + headerLen - 4
                frameBuf[maskOffset] = mask[0]
                frameBuf[maskOffset + 1] = mask[1]
                frameBuf[maskOffset + 2] = mask[2]
                frameBuf[maskOffset + 3] = mask[3]

                synchronized(wsConn.writeLock) {
                    wsConn.out.write(frameBuf, headerStart, headerLen + read)
                    if (localIn.available() == 0) {
                        wsConn.out.flush()
                    }
                }
            }
        } catch (_: Exception) {
            hadWriteError = true
        } finally {
            if (hadWriteError) {
                runCatching { wsConn.socket.close() }
            }
        }
    }

    private fun relayDown(
        wsConn: DirectWsConnection,
        localOut: OutputStream,
        key: ByteArray
    ) {
        val chunkBuf = ByteArray(IO_BUFFER_SIZE)
        val ctrlBuf = ByteArray(128)
        val maskBuf = ByteArray(4)
        var msgKeyOffset = 0
        try {
            while (running.get() && !wsConn.socket.isClosed) {
                val b0 = wsConn.input.read()
                if (b0 < 0) break
                val b1 = wsConn.input.read()
                if (b1 < 0) break

                val fin = (b0 and 0x80) != 0
                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                val lenByte = b1 and 0x7F
                val payloadLen = when (lenByte) {
                    126 -> {
                        val hi = wsConn.input.read()
                        val lo = wsConn.input.read()
                        if (hi < 0 || lo < 0) break
                        ((hi shl 8) or lo).toLong()
                    }
                    127 -> {
                        var v = 0L
                        for (i in 0 until 8) {
                            val b = wsConn.input.read()
                            if (b < 0) return
                            v = (v shl 8) or (b.toLong() and 0xFFL)
                        }
                        v
                    }
                    else -> lenByte.toLong()
                }

                if (masked) {
                    readExactInto(wsConn.input, maskBuf, 0, 4)
                }

                // Control frames (Close=8, Ping=9, Pong=10)
                if (opcode >= 8) {
                    val ctrlLen = payloadLen.toInt().coerceIn(0, ctrlBuf.size)
                    if (ctrlLen > 0) {
                        readExactInto(wsConn.input, ctrlBuf, 0, ctrlLen)
                        if (masked) {
                            for (i in 0 until ctrlLen) {
                                ctrlBuf[i] = (ctrlBuf[i].toInt() xor maskBuf[i and 3].toInt()).toByte()
                            }
                        }
                    }
                    var extra = payloadLen - ctrlLen
                    while (extra > 0) {
                        val skipped = wsConn.input.skip(extra)
                        if (skipped <= 0) break
                        extra -= skipped
                    }
                    when (opcode) {
                        8 -> break // WebSocket Close
                        9 -> {
                            // Reply Pong immediately
                            synchronized(wsConn.writeLock) {
                                wsConn.sendControlPong(ctrlBuf, ctrlLen)
                                wsConn.out.flush()
                            }
                        }
                        else -> Unit // Pong / unknown control
                    }
                    continue
                }

                if (opcode != 0) {
                    msgKeyOffset = 0
                }

                var remaining = payloadLen
                var frameOffset = 0
                while (remaining > 0L) {
                    val toRead = minOf(remaining, chunkBuf.size.toLong()).toInt()
                    readExactInto(wsConn.input, chunkBuf, 0, toRead)
                    var k = msgKeyOffset and 15
                    if (masked) {
                        for (i in 0 until toRead) {
                            val unmasked = chunkBuf[i].toInt() xor maskBuf[(frameOffset + i) and 3].toInt()
                            chunkBuf[i] = (unmasked xor key[k].toInt()).toByte()
                            k = (k + 1) and 15
                        }
                    } else {
                        for (i in 0 until toRead) {
                            chunkBuf[i] = (chunkBuf[i].toInt() xor key[k].toInt()).toByte()
                            k = (k + 1) and 15
                        }
                    }
                    msgKeyOffset += toRead
                    frameOffset += toRead
                    localOut.write(chunkBuf, 0, toRead)
                    remaining -= toRead
                }
                if (fin && wsConn.input.available() == 0) {
                    localOut.flush()
                }
            }
            localOut.flush()
        } catch (_: Exception) {
        }
    }

    private fun openWebSocket(): DirectWsConnection? {
        if (cleanIp.isNotBlank() && System.currentTimeMillis() >= cleanIpCooldownUntilMs.get()) {
            val cleanResult = runCatching { dialDirectWebSocket(cleanIp, CLEAN_IP_CONNECT_TIMEOUT_MS) }
            val conn = cleanResult.getOrNull()
            if (conn != null) {
                cleanIpFailureCount.set(0)
                return conn
            }
            if (cleanIpFailureCount.incrementAndGet() >= 2) {
                cleanIpCooldownUntilMs.set(System.currentTimeMillis() + CLEAN_IP_COOLDOWN_MS)
            }
            if (loggedCleanIpFallback.compareAndSet(false, true)) {
                onLog("Amir CF Clean IP ($cleanIp) dial failed (${cleanResult.exceptionOrNull()?.message}), falling back to direct host")
            }
        }
        return runCatching { dialDirectWebSocket(workerHost, DIRECT_CONNECT_TIMEOUT_MS) }.getOrNull()
    }

    private fun dialDirectWebSocket(connectTarget: String, connectTimeoutMs: Int): DirectWsConnection {
        val rawSocket = Socket()
        var socketToTrack: Socket = rawSocket
        activeSockets.add(rawSocket)
        try {
            rawSocket.tcpNoDelay = true // Forces SocketImpl FD creation before protect()
            protectSocket(rawSocket)
            val resolvedAddr = InetAddress.getByName(connectTarget)
            rawSocket.connect(InetSocketAddress(resolvedAddr, workerPort), connectTimeoutMs)
            rawSocket.soTimeout = HANDSHAKE_TIMEOUT_MS

            val finalSocket: Socket = if (isTls) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(rawSocket, workerHost, workerPort, true) as SSLSocket
                sslSocket.tcpNoDelay = true
                val sslParams = sslSocket.sslParameters
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isIpLiteral(workerHost)) {
                    sslParams.serverNames = listOf(SNIHostName(workerHost))
                }
                sslParams.endpointIdentificationAlgorithm = "HTTPS"
                sslSocket.sslParameters = sslParams
                sslSocket.startHandshake()
                val verified = HttpsURLConnection.getDefaultHostnameVerifier().verify(workerHost, sslSocket.session)
                if (!verified) {
                    throw SSLPeerUnverifiedException("Hostname verification failed for $workerHost")
                }
                activeSockets.remove(rawSocket)
                activeSockets.add(sslSocket)
                socketToTrack = sslSocket
                sslSocket
            } else {
                rawSocket
            }

            val wsIn = BufferedInputStream(finalSocket.getInputStream(), IO_BUFFER_SIZE)
            val wsOut = BufferedOutputStream(finalSocket.getOutputStream(), IO_BUFFER_SIZE)

            // RFC-6455 HTTP/1.1 Upgrade (compression=None, exact match to Cl4.py)
            val keyNonce = ByteArray(16).also { random.nextBytes(it) }
            val wsKey = Base64.encodeToString(keyNonce, Base64.NO_WRAP)
            val upgradeReq = buildString {
                append("GET ").append(requestTarget).append(" HTTP/1.1\r\n")
                append("Host: ").append(httpHostHeader).append("\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ").append(wsKey).append("\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("Sec-WebSocket-Protocol: ").append(SUBPROTO).append("\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)

            wsOut.write(upgradeReq)
            wsOut.flush()

            val statusLine = readHttpLine(wsIn) ?: throw EOFException("Closed before HTTP 101 status")
            if (!statusLine.startsWith("HTTP/1.1 101") && !statusLine.startsWith("HTTP/1.0 101")) {
                throw IOException("WebSocket upgrade rejected: $statusLine")
            }

            var acceptHeader: String? = null
            while (true) {
                val line = readHttpLine(wsIn) ?: throw EOFException("Truncated HTTP upgrade headers")
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim()
                    if (name.equals("Sec-WebSocket-Accept", ignoreCase = true)) {
                        acceptHeader = line.substring(colon + 1).trim()
                    }
                }
            }

            val expectedAccept = computeWsAccept(wsKey)
            if (acceptHeader != expectedAccept) {
                throw IOException("Invalid Sec-WebSocket-Accept header")
            }

            return DirectWsConnection(finalSocket, wsIn, wsOut)
        } catch (t: Throwable) {
            activeSockets.remove(socketToTrack)
            activeSockets.remove(rawSocket)
            runCatching { socketToTrack.close() }
            runCatching { rawSocket.close() }
            throw t
        }
    }

    private class DirectWsConnection(
        val socket: Socket,
        val input: BufferedInputStream,
        val out: BufferedOutputStream
    ) {
        val writeLock = Any()

        fun sendBinaryFrame(data: ByteArray, offset: Int, length: Int, mask: ByteArray? = null) {
            val m = mask ?: ByteArray(4).also { random.nextBytes(it) }
            synchronized(writeLock) {
                out.write(0x82)
                when {
                    length <= 125 -> out.write(0x80 or length)
                    length <= 65535 -> {
                        out.write(0x80 or 126)
                        out.write((length ushr 8) and 0xFF)
                        out.write(length and 0xFF)
                    }
                    else -> {
                        out.write(0x80 or 127)
                        val lenLong = length.toLong()
                        for (shift in 56 downTo 0 step 8) {
                            out.write(((lenLong ushr shift) and 0xFF).toInt())
                        }
                    }
                }
                out.write(m)
                val masked = ByteArray(length)
                for (i in 0 until length) {
                    masked[i] = (data[offset + i].toInt() xor m[i and 3].toInt()).toByte()
                }
                out.write(masked)
            }
        }

        fun sendControlPong(payload: ByteArray, length: Int) {
            val m = ByteArray(4).also { random.nextBytes(it) }
            val clamped = length.coerceIn(0, 125)
            out.write(0x8A)
            out.write(0x80 or clamped)
            out.write(m)
            for (i in 0 until clamped) {
                out.write((payload[i].toInt() xor m[i and 3].toInt()) and 0xFF)
            }
        }

        fun readMessageBytes(): ByteArray? {
            var acc: ByteArray? = null
            var accLen = 0
            while (true) {
                val b0 = input.read()
                if (b0 < 0) return null
                val b1 = input.read()
                if (b1 < 0) return null
                val fin = (b0 and 0x80) != 0
                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                val lenByte = b1 and 0x7F
                val payloadLen = when (lenByte) {
                    126 -> {
                        val hi = input.read()
                        val lo = input.read()
                        if (hi < 0 || lo < 0) return null
                        (hi shl 8) or lo
                    }
                    127 -> {
                        var v = 0L
                        for (i in 0 until 8) {
                            val b = input.read()
                            if (b < 0) return null
                            v = (v shl 8) or (b.toLong() and 0xFFL)
                        }
                        if (v > 4 * 1024 * 1024) return null
                        v.toInt()
                    }
                    else -> lenByte
                }
                val maskKey = if (masked) readExactBytes(input, 4) else null
                val part = readExactBytes(input, payloadLen)
                if (maskKey != null) {
                    for (i in part.indices) {
                        part[i] = (part[i].toInt() xor maskKey[i and 3].toInt()).toByte()
                    }
                }
                if (opcode == 8) return null
                if (opcode == 9) {
                    synchronized(writeLock) {
                        sendControlPong(part, part.size)
                        out.flush()
                    }
                    continue
                }
                if (opcode == 10) continue

                if (acc == null && fin) return part
                val prev = acc ?: ByteArray(0)
                val next = ByteArray(accLen + part.size)
                System.arraycopy(prev, 0, next, 0, accLen)
                System.arraycopy(part, 0, next, accLen, part.size)
                acc = next
                accLen += part.size
                if (fin) return acc
            }
        }
    }

    suspend fun measureDelay(testUrl: String): Long = withContext(Dispatchers.IO) {
        if (uuidBytes.isEmpty() || workerHost.isBlank()) return@withContext -1L
        val parsed = Uri.parse(testUrl)
        val targetHost = parsed.host?.takeIf { it.isNotBlank() } ?: "www.gstatic.com"
        val targetPort = if (parsed.port > 0) parsed.port else 80
        val path = parsed.encodedPath?.takeIf { it.isNotBlank() } ?: "/generate_204"
        val startMs = System.currentTimeMillis()
        var wsConn: DirectWsConnection? = null
        try {
            wsConn = openWebSocket() ?: return@withContext -1L
            val (initPacket, key) = buildInitPacket(uuidBytes, targetHost, targetPort)
            wsConn.sendBinaryFrame(initPacket, 0, initPacket.size)
            wsConn.out.flush()

            val ack = wsConn.readMessageBytes() ?: return@withContext -1L
            if (ack.size < 16) return@withContext -1L
            val decryptedAck = xorBytes(ack, key)
            val expectedAck = hmacSha256(key, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (!decryptedAck.contentEquals(expectedAck)) return@withContext -1L

            val httpReq = "HEAD $path HTTP/1.1\r\nHost: $targetHost\r\nConnection: close\r\n\r\n"
                .toByteArray(StandardCharsets.US_ASCII)
            val encReq = xorBytes(httpReq, key)
            wsConn.sendBinaryFrame(encReq, 0, encReq.size)
            wsConn.out.flush()

            val resp = wsConn.readMessageBytes() ?: return@withContext -1L
            if (resp.isEmpty()) return@withContext -1L
            (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        } finally {
            wsConn?.let { conn ->
                activeSockets.remove(conn.socket)
                runCatching { conn.socket.close() }
            }
        }
    }

    companion object {
        private const val SUBPROTO = "chat.v2"
        private const val MAX_CHUNK = 32768
        private const val WS_MAX_HEADER_BYTES = 14
        private const val IO_BUFFER_SIZE = 65536
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val CLEAN_IP_CONNECT_TIMEOUT_MS = 5_000
        private const val DIRECT_CONNECT_TIMEOUT_MS = 10_000
        private const val CLEAN_IP_COOLDOWN_MS = 30_000L
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private val random = SecureRandom()

        fun deriveKey(uuidBytes: ByteArray, nonce: ByteArray): ByteArray {
            require(uuidBytes.isNotEmpty()) { "UUID cannot be empty" }
            require(nonce.size == 16) { "Nonce must be 16 bytes" }
            return ByteArray(16) { i ->
                (uuidBytes[i % uuidBytes.size].toInt() xor nonce[i].toInt()).toByte()
            }
        }

        fun buildInitPacket(
            uuidBytes: ByteArray,
            host: String,
            port: Int,
            payload: ByteArray = ByteArray(0)
        ): Pair<ByteArray, ByteArray> {
            val nonce = ByteArray(16).also { random.nextBytes(it) }
            val key = deriveKey(uuidBytes, nonce)

            val (addrType, addrBytes) = encodeTargetAddress(host)
            val headerLen = 1 + 2 + 1 + addrBytes.size
            val body = ByteArray(headerLen + payload.size)
            body[0] = 1
            body[1] = ((port shr 8) and 0xFF).toByte()
            body[2] = (port and 0xFF).toByte()
            body[3] = addrType
            System.arraycopy(addrBytes, 0, body, 4, addrBytes.size)
            if (payload.isNotEmpty()) {
                System.arraycopy(payload, 0, body, headerLen, payload.size)
            }

            val enc = xorBytes(body, key)
            val mac = hmacSha256(key, enc).copyOfRange(0, 8)
            val packet = ByteArray(nonce.size + enc.size + mac.size)
            System.arraycopy(nonce, 0, packet, 0, nonce.size)
            System.arraycopy(enc, 0, packet, nonce.size, enc.size)
            System.arraycopy(mac, 0, packet, nonce.size + enc.size, mac.size)
            return packet to key
        }

        private fun encodeTargetAddress(host: String): Pair<Byte, ByteArray> {
            val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
            if (isIpv4Literal(cleanHost)) {
                val bytes = InetAddress.getByName(cleanHost).address
                return 1.toByte() to bytes
            }
            if (cleanHost.contains(":")) {
                val bytes = runCatching { InetAddress.getByName(cleanHost).address }.getOrNull()
                if (bytes != null && bytes.size == 16) {
                    // Cl4 protocol uses address type 3 for IPv6
                    return 3.toByte() to bytes
                }
            }
            val hostBytes = cleanHost.toByteArray(StandardCharsets.UTF_8)
            val out = ByteArray(1 + hostBytes.size)
            out[0] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, out, 1, hostBytes.size)
            return 2.toByte() to out
        }

        private fun isIpv4Literal(host: String): Boolean {
            val parts = host.split(".")
            if (parts.size != 4) return false
            return parts.all { p ->
                val num = p.toIntOrNull() ?: return@all false
                num in 0..255 && p == num.toString()
            }
        }

        private fun isIpLiteral(host: String): Boolean {
            val clean = host.trim().removePrefix("[").removeSuffix("]")
            return isIpv4Literal(clean) || clean.contains(':')
        }

        fun xorBytes(data: ByteArray, key: ByteArray): ByteArray {
            val out = ByteArray(data.size)
            val kLen = key.size
            for (i in data.indices) {
                out[i] = (data[i].toInt() xor key[i % kLen].toInt()).toByte()
            }
            return out
        }

        fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun computeWsAccept(wsKey: String): String {
            val sha1 = MessageDigest.getInstance("SHA-1")
            val digest = sha1.digest((wsKey + WS_GUID).toByteArray(StandardCharsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        }

        private fun readHttpLine(input: InputStream): String? {
            val sb = StringBuilder(64)
            var readAny = false
            while (true) {
                val b = input.read()
                if (b < 0) return if (readAny) sb.toString() else null
                readAny = true
                if (b == '\n'.code) {
                    val len = sb.length
                    if (len > 0 && sb[len - 1] == '\r') {
                        sb.setLength(len - 1)
                    }
                    return sb.toString()
                }
                sb.append(b.toChar())
                if (sb.length > 8192) throw IOException("HTTP header line too long")
            }
        }

        private fun readExactBytes(input: InputStream, count: Int): ByteArray {
            val buf = ByteArray(count)
            readExactInto(input, buf, 0, count)
            return buf
        }

        private fun readExactInto(input: InputStream, buf: ByteArray, offset: Int, count: Int) {
            var readTotal = 0
            while (readTotal < count) {
                val r = input.read(buf, offset + readTotal, count - readTotal)
                if (r < 0) throw EOFException("Unexpected EOF")
                readTotal += r
            }
        }
    }
}

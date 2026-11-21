package com.example.core

import android.net.Uri
import android.os.Build
import android.util.Base64
import com.example.data.model.AppSettings
import com.example.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class AmirLocalProxy(
    private val serverConfig: ServerConfig,
    private val settings: AppSettings = AppSettings(),
    private val resolvedHosts: Map<String, List<String>> = emptyMap(),
    private val protectSocket: (Socket) -> Boolean = { true },
    private val onLog: (String) -> Unit = {}
) {
    private val proxyExecutor = Executors.newCachedThreadPool()
    private val warmPoolExecutor = Executors.newFixedThreadPool(2)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeDatagramSockets = ConcurrentHashMap.newKeySet<DatagramSocket>()
    private val warmPool = ConcurrentLinkedDeque<PooledWsConnection>()
    private val pendingWarmups = AtomicInteger(0)
    private val dnsCache = ConcurrentHashMap<String, CachedDnsResponse>()
    private val running = AtomicBoolean(false)
    private val loggedCleanIpFallback = AtomicBoolean(false)
    private val cleanIpFailureCount = AtomicInteger(0)
    private val cleanIpCooldownUntilMs = AtomicLong(0L)
    private val directDohFailureCount = AtomicInteger(0)
    private val directDohCooldownUntilMs = AtomicLong(0L)
    private val lastErrorLogMs = AtomicLong(0L)
    private val parsedDnsHosts = XrayConfigBuilder.parseDnsHosts(settings.dnsHosts)

    private data class PooledWsConnection(
        val conn: DirectWsConnection,
        val createdAtMs: Long,
        val usedCleanIp: Boolean
    )

    private data class ObtainedWs(
        val conn: DirectWsConnection,
        val fromPool: Boolean,
        val usedCleanIp: Boolean
    )

    private data class DialAttemptResult(
        val conn: DirectWsConnection,
        val usedCleanIp: Boolean
    )

    private data class CachedDnsResponse(
        val payload: ByteArray,
        val expiresAtMs: Long
    )

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

    private val enableWarmPool: Boolean = !isLoopbackTarget(workerHost, parsedDnsHosts, resolvedHosts)

    fun start(preferredPort: Int = 0): Int {
        val loopback = InetAddress.getByName("127.0.0.1")
        val ss = if (preferredPort in 1024..65535) {
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(loopback, preferredPort), 1024)
                }
            }.getOrElse {
                ServerSocket(0, 1024, loopback)
            }
        } else {
            ServerSocket(0, 1024, loopback)
        }
        serverSocket = ss
        running.set(true)
        val boundPort = ss.localPort
        emitInfo(
            "Amir standalone core SOCKS5 output listening on 127.0.0.1:$boundPort -> $workerHost" +
                (if (cleanIp.isNotBlank()) " (CF Clean IP: $cleanIp)" else "")
        )

        // Pre-warm TLS + WebSocket connections so first browser requests skip multi-RTT setup
        if (enableWarmPool && uuidBytes.isNotEmpty() && workerHost.isNotBlank()) {
            scheduleWarmPoolRefill(targetCount = INITIAL_WARM_POOL_SIZE)
        }

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
        while (true) {
            val pooled = warmPool.pollFirst() ?: break
            activeSockets.remove(pooled.conn.socket)
            runCatching { pooled.conn.socket.close() }
        }
        activeSockets.forEach { sock ->
            runCatching { sock.close() }
        }
        activeSockets.clear()
        activeDatagramSockets.forEach { ds ->
            runCatching { ds.close() }
        }
        activeDatagramSockets.clear()
        dnsCache.clear()
        runCatching { warmPoolExecutor.shutdownNow() }
        runCatching { proxyExecutor.shutdownNow() }
    }

    private fun scheduleWarmPoolRefill(targetCount: Int = MAX_WARM_POOL_SIZE) {
        if (!enableWarmPool || !running.get() || warmPoolExecutor.isShutdown) return
        val currentSize = warmPool.size + pendingWarmups.get()
        val needed = (targetCount - currentSize).coerceAtLeast(0)
        repeat(needed) {
            if (pendingWarmups.incrementAndGet() > MAX_WARM_POOL_SIZE + 1) {
                pendingWarmups.decrementAndGet()
                return
            }
            runCatching {
                warmPoolExecutor.execute {
                    try {
                        if (!running.get()) return@execute
                        val dialed = dialBestWebSocket() ?: return@execute
                        if (!running.get() || warmPool.size >= MAX_WARM_POOL_SIZE) {
                            activeSockets.remove(dialed.conn.socket)
                            runCatching { dialed.conn.socket.close() }
                            return@execute
                        }
                        warmPool.offerLast(
                            PooledWsConnection(
                                conn = dialed.conn,
                                createdAtMs = System.currentTimeMillis(),
                                usedCleanIp = dialed.usedCleanIp
                            )
                        )
                    } catch (_: Exception) {
                    } finally {
                        pendingWarmups.decrementAndGet()
                    }
                }
            }.onFailure {
                pendingWarmups.decrementAndGet()
            }
        }
    }

    private fun obtainWebSocket(): ObtainedWs? {
        val now = System.currentTimeMillis()
        while (true) {
            val candidate = warmPool.pollFirst() ?: break
            val age = now - candidate.createdAtMs
            val alive = !candidate.conn.socket.isClosed &&
                candidate.conn.socket.isConnected &&
                age in 0L..WARM_POOL_MAX_AGE_MS
            if (alive) {
                scheduleWarmPoolRefill()
                return ObtainedWs(candidate.conn, fromPool = true, usedCleanIp = candidate.usedCleanIp)
            } else {
                activeSockets.remove(candidate.conn.socket)
                runCatching { candidate.conn.socket.close() }
            }
        }
        val direct = dialBestWebSocket()
        scheduleWarmPoolRefill()
        return direct?.let { ObtainedWs(it.conn, fromPool = false, usedCleanIp = it.usedCleanIp) }
    }

    private fun handleSocksClient(localSocket: Socket) {
        activeSockets.add(localSocket)
        var wsConn: DirectWsConnection? = null
        try {
            localSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
            localSocket.tcpNoDelay = true
            val localIn = BufferedInputStream(localSocket.getInputStream(), IO_BUFFER_SIZE)
            val localOut = localSocket.getOutputStream()

            // 1. SOCKS5 Greeting (only No-Auth 0x00 supported)
            val header = readExactBytes(localIn, 2)
            if (header[0].toInt() != 5) return
            val nMethods = header[1].toInt() and 0xFF
            if (nMethods == 0) return
            val methods = readExactBytes(localIn, nMethods)
            val supportsNoAuth = methods.any { (it.toInt() and 0xFF) == 0x00 }
            if (!supportsNoAuth) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0xFF.toByte()))
                    localOut.flush()
                }
                return
            }
            localOut.write(byteArrayOf(0x05, 0x00))
            localOut.flush()

            // 2. SOCKS5 Request: CONNECT (0x01) or UDP ASSOCIATE (0x03 for DNS port 53)
            val req = readExactBytes(localIn, 4)
            if (req[0].toInt() != 5) return
            val cmd = req[1].toInt() and 0xFF
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
                else -> {
                    runCatching {
                        localOut.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                        localOut.flush()
                    }
                    return
                }
            }
            val portBytes = readExactBytes(localIn, 2)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            if (cmd == 3) {
                handleSocksUdpAssociate(localSocket, localIn, localOut)
                return
            }

            if (cmd != 1) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }

            // Cloudflare Workers connect() forbids connecting to Cloudflare's own 1.1.1.1/1.0.0.1 IPs
            val dialHost = if (targetPort == 53 && isCloudflareResolverIp(targetHost)) {
                "8.8.8.8"
            } else {
                targetHost
            }

            // 3. Fast-path Cloudflare DoH edge endpoints (Cloudflare Workers connect() forbids Cloudflare IPs)
            if (targetPort == 443 && isCloudflareDohIp(targetHost)) {
                val now = System.currentTimeMillis()
                val isIpV6Target = targetHost.contains(':')
                val skipIpv6Doh = isIpV6Target && !settings.preferIpv6 && !settings.enableIpv6
                if (!skipIpv6Doh && now >= directDohCooldownUntilMs.get()) {
                    var receivedAnyRemoteBytes = false
                    val relayed = tryDirectProtectedTcpRelay(
                        targetHost = targetHost,
                        targetPort = targetPort,
                        localSocket = localSocket,
                        localIn = localIn,
                        localOut = localOut,
                        connectTimeoutMs = DIRECT_DOH_CONNECT_TIMEOUT_MS,
                        initialFirstByteTimeoutMs = DIRECT_DOH_FIRST_BYTE_TIMEOUT_MS,
                        onFirstRemoteByte = {
                            receivedAnyRemoteBytes = true
                            directDohFailureCount.set(0)
                            directDohCooldownUntilMs.set(0L)
                        }
                    )
                    if (relayed) {
                        if (!receivedAnyRemoteBytes) {
                            recordDirectDohFailure()
                        }
                        return
                    }
                    recordDirectDohFailure()
                }
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }

            // 4. Obtain WebSocket from pre-warmed pool or dial directly
            val obtained = obtainWebSocket()
            if (obtained == null) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }
            wsConn = obtained.conn

            // 5. Send Amir init packet (fail fast on stale pooled WS and retry via alternate path if needed)
            val (initPacket, key) = buildInitPacket(uuidBytes, dialHost, targetPort)
            var ackBytes: ByteArray? = null
            var activeKey = key
            var handshakeFailed = false
            try {
                wsConn.socket.soTimeout = if (obtained.fromPool) POOLED_ACK_TIMEOUT_MS else HANDSHAKE_TIMEOUT_MS
                wsConn.sendBinaryFrame(initPacket, 0, initPacket.size)
                wsConn.out.flush()
                ackBytes = wsConn.readMessageBytes()
                if (ackBytes == null || ackBytes.size < 16) {
                    handshakeFailed = true
                }
            } catch (_: Exception) {
                handshakeFailed = true
            }
            if (handshakeFailed) {
                activeSockets.remove(wsConn.socket)
                runCatching { wsConn.socket.close() }
                val retryDial = dialBestWebSocket(preferDirectHostFirst = obtained.usedCleanIp)
                wsConn = retryDial?.conn
                if (wsConn != null) {
                    val (retryInit, retryKey) = buildInitPacket(uuidBytes, dialHost, targetPort)
                    activeKey = retryKey
                    ackBytes = runCatching {
                        wsConn.socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                        wsConn.sendBinaryFrame(retryInit, 0, retryInit.size)
                        wsConn.out.flush()
                        wsConn.readMessageBytes()
                    }.getOrNull()
                    if (obtained.usedCleanIp && ackBytes != null && ackBytes.size >= 16 && !retryDial.usedCleanIp) {
                        recordCleanIpFailure("post-upgrade handshake dropped on $cleanIp")
                    }
                } else {
                    ackBytes = null
                }
            }

            if (wsConn == null || ackBytes == null || ackBytes.size < 16) {
                emitThrottledError("Amir tunnel handshake failed for $dialHost:$targetPort")
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }

            val decryptedAck = xorBytes(ackBytes, activeKey)
            val expectedAck = hmacSha256(activeKey, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (decryptedAck.size < 16 || !MessageDigest.isEqual(decryptedAck.copyOfRange(0, 16), expectedAck)) {
                emitThrottledError("Amir HMAC ACK mismatch for $dialHost:$targetPort")
                return
            }

            // Switch both sockets to infinite idle timeout for active full-duplex streams
            localSocket.soTimeout = 0
            wsConn.socket.soTimeout = 0

            // 6. Send SOCKS5 success response
            localOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            localOut.flush()

            // 7. Full-duplex XOR relay (keep WS alive briefly on clean half-close so HTTP responses drain,
            //    while closing immediately on aborted/closed client sockets so Worker slots are never leaked)
            val boundWs = wsConn
            val upFuture = proxyExecutor.submit {
                relayUp(localSocket, localIn, boundWs, activeKey)
            }
            relayDown(boundWs, localOut, activeKey)
            runCatching { upFuture.cancel(false) }
        } catch (e: Exception) {
            emitThrottledError("Amir proxy connection error: ${e.message}")
        } finally {
            activeSockets.remove(localSocket)
            runCatching { localSocket.close() }
            wsConn?.let { conn ->
                activeSockets.remove(conn.socket)
                runCatching {
                    if (!conn.socket.isClosed) {
                        conn.socket.soTimeout = 1_000
                        synchronized(conn.writeLock) {
                            conn.sendCloseFrame()
                            conn.out.flush()
                        }
                    }
                }
                runCatching { conn.socket.close() }
            }
        }
    }

    private fun handleSocksUdpAssociate(
        controlSocket: Socket,
        controlIn: InputStream,
        controlOut: OutputStream
    ) {
        val udpSocket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        activeDatagramSockets.add(udpSocket)
        try {
            val udpPort = udpSocket.localPort
            val bindReply = byteArrayOf(
                0x05, 0x00, 0x00, 0x01,
                127, 0, 0, 1,
                ((udpPort ushr 8) and 0xFF).toByte(),
                (udpPort and 0xFF).toByte()
            )
            controlOut.write(bindReply)
            controlOut.flush()
            controlSocket.soTimeout = 0

            // Close UDP association cleanly when TCP control socket closes
            val controlWatcher = proxyExecutor.submit {
                try {
                    val buf = ByteArray(64)
                    while (running.get() && !controlSocket.isClosed) {
                        if (controlIn.read(buf) < 0) break
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { udpSocket.close() }
                }
            }

            val recvBuf = ByteArray(4096)
            while (running.get() && !udpSocket.isClosed && !controlSocket.isClosed) {
                val packet = DatagramPacket(recvBuf, recvBuf.size)
                try {
                    udpSocket.receive(packet)
                } catch (_: Exception) {
                    break
                }
                val copy = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val clientAddr = packet.socketAddress
                proxyExecutor.execute {
                    processSocksUdpDnsPacket(udpSocket, clientAddr, copy)
                }
            }
            runCatching { controlWatcher.cancel(true) }
        } finally {
            activeDatagramSockets.remove(udpSocket)
            runCatching { udpSocket.close() }
        }
    }

    private fun processSocksUdpDnsPacket(
        udpSocket: DatagramSocket,
        clientAddr: java.net.SocketAddress,
        rawPacket: ByteArray
    ) {
        if (rawPacket.size < 10) return
        val frag = rawPacket[2].toInt() and 0xFF
        if (frag != 0) return

        val atyp = rawPacket[3].toInt() and 0xFF
        var idx = 4
        val dstHost: String
        when (atyp) {
            1 -> {
                if (rawPacket.size < idx + 6) return
                dstHost = InetAddress.getByAddress(rawPacket.copyOfRange(idx, idx + 4)).hostAddress ?: return
                idx += 4
            }
            3 -> {
                if (rawPacket.size < idx + 1) return
                val len = rawPacket[idx].toInt() and 0xFF
                idx++
                if (rawPacket.size < idx + len + 2) return
                dstHost = String(rawPacket, idx, len, StandardCharsets.UTF_8)
                idx += len
            }
            4 -> {
                if (rawPacket.size < idx + 18) return
                dstHost = InetAddress.getByAddress(rawPacket.copyOfRange(idx, idx + 16)).hostAddress ?: return
                idx += 16
            }
            else -> return
        }
        val dstPort = ((rawPacket[idx].toInt() and 0xFF) shl 8) or (rawPacket[idx + 1].toInt() and 0xFF)
        idx += 2

        // Only DNS (port 53) can be tunneled over AMIR's TCP-over-WS transport
        if (dstPort != 53) return

        val socksHeader = rawPacket.copyOfRange(0, idx)
        val dnsQuery = rawPacket.copyOfRange(idx, rawPacket.size)
        if (dnsQuery.size < 12) return

        val cacheKey = extractDnsQuestionCacheKey(dnsQuery)
        if (cacheKey != null) {
            val cached = dnsCache[cacheKey]
            val now = System.currentTimeMillis()
            if (cached != null && now < cached.expiresAtMs && cached.payload.size >= 12) {
                val replyDns = cached.payload.copyOf()
                replyDns[0] = dnsQuery[0]
                replyDns[1] = dnsQuery[1]
                sendSocksUdpReply(udpSocket, clientAddr, socksHeader, replyDns)
                return
            }
        }

        // Redirect 1.1.1.1 / 1.0.0.1 to 8.8.8.8 so Cloudflare Worker connect() succeeds,
        // while keeping original socksHeader so Android/Xray matches the UDP 5-tuple.
        val dialDnsHost = if (isCloudflareResolverIp(dstHost)) "8.8.8.8" else dstHost
        val dnsResp = queryDnsOverAmirTcp(dialDnsHost, 53, dnsQuery) ?: return
        if (cacheKey != null && dnsResp.size >= 12) {
            val rcode = dnsResp[3].toInt() and 0x0F
            if (rcode == 0) {
                dnsCache[cacheKey] = CachedDnsResponse(
                    payload = dnsResp.copyOf(),
                    expiresAtMs = System.currentTimeMillis() + DNS_CACHE_TTL_MS
                )
            }
        }
        sendSocksUdpReply(udpSocket, clientAddr, socksHeader, dnsResp)
    }

    private fun queryDnsOverAmirTcp(dnsHost: String, dnsPort: Int, dnsQuery: ByteArray): ByteArray? {
        var wsConn: DirectWsConnection? = null
        try {
            val obtained = obtainWebSocket() ?: return null
            wsConn = obtained.conn
            val (initPacket, initKey) = buildInitPacket(uuidBytes, dnsHost, dnsPort)
            var activeKey = initKey
            var ack: ByteArray? = null
            var handshakeFailed = false
            try {
                wsConn.socket.soTimeout = if (obtained.fromPool) POOLED_ACK_TIMEOUT_MS else HANDSHAKE_TIMEOUT_MS
                wsConn.sendBinaryFrame(initPacket, 0, initPacket.size)
                wsConn.out.flush()
                ack = wsConn.readMessageBytes()
                if (ack == null || ack.size < 16) {
                    handshakeFailed = true
                }
            } catch (_: Exception) {
                handshakeFailed = true
            }
            if (handshakeFailed) {
                activeSockets.remove(wsConn.socket)
                runCatching { wsConn.socket.close() }
                val retryDial = dialBestWebSocket(preferDirectHostFirst = obtained.usedCleanIp) ?: return null
                wsConn = retryDial.conn
                wsConn.socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                val (retryInit, retryKey) = buildInitPacket(uuidBytes, dnsHost, dnsPort)
                activeKey = retryKey
                wsConn.sendBinaryFrame(retryInit, 0, retryInit.size)
                wsConn.out.flush()
                ack = wsConn.readMessageBytes()
            }
            if (ack == null || ack.size < 16) return null
            val decAck = xorBytes(ack, activeKey)
            val expAck = hmacSha256(activeKey, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (!MessageDigest.isEqual(decAck.copyOfRange(0, 16), expAck)) return null

            // DNS-over-TCP (RFC 1035 Section 4.2.2): 2-byte big-endian length prefix + message
            val tcpDnsReq = ByteArray(2 + dnsQuery.size)
            tcpDnsReq[0] = ((dnsQuery.size ushr 8) and 0xFF).toByte()
            tcpDnsReq[1] = (dnsQuery.size and 0xFF).toByte()
            System.arraycopy(dnsQuery, 0, tcpDnsReq, 2, dnsQuery.size)

            val encReq = xorBytes(tcpDnsReq, activeKey)
            wsConn.sendBinaryFrame(encReq, 0, encReq.size)
            wsConn.out.flush()

            var keyOffset = 0
            val accumulated = java.io.ByteArrayOutputStream(512)
            while (accumulated.size() < 2) {
                val frame = wsConn.readMessageBytes() ?: break
                val dec = xorBytesWithOffset(frame, activeKey, keyOffset)
                keyOffset = (keyOffset + frame.size) and 15
                accumulated.write(dec)
            }
            var raw = accumulated.toByteArray()
            if (raw.size < 2) return null
            val expectedLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
            if (expectedLen <= 0 || expectedLen > 8192) return null

            while (accumulated.size() < 2 + expectedLen) {
                val frame = wsConn.readMessageBytes() ?: break
                val dec = xorBytesWithOffset(frame, activeKey, keyOffset)
                keyOffset = (keyOffset + frame.size) and 15
                accumulated.write(dec)
            }
            raw = accumulated.toByteArray()
            if (raw.size < 2 + expectedLen) return null
            return raw.copyOfRange(2, 2 + expectedLen)
        } catch (_: Exception) {
            return null
        } finally {
            wsConn?.let { conn ->
                activeSockets.remove(conn.socket)
                runCatching { conn.socket.close() }
            }
        }
    }

    private fun sendSocksUdpReply(
        udpSocket: DatagramSocket,
        clientAddr: java.net.SocketAddress,
        socksHeader: ByteArray,
        dnsResponse: ByteArray
    ) {
        val out = ByteArray(socksHeader.size + dnsResponse.size)
        System.arraycopy(socksHeader, 0, out, 0, socksHeader.size)
        System.arraycopy(dnsResponse, 0, out, socksHeader.size, dnsResponse.size)
        runCatching {
            udpSocket.send(DatagramPacket(out, out.size, clientAddr))
        }
    }

    private fun relayUp(
        localSocket: Socket,
        localIn: InputStream,
        wsConn: DirectWsConnection,
        key: ByteArray
    ) {
        val frameBuf = ByteArray(WS_MAX_HEADER_BYTES + MAX_CHUNK)
        val combinedXor = ByteArray(16)
        val mask = ByteArray(4)
        var maskSeed = System.nanoTime() xor (Thread.currentThread().id shl 16) xor -0x61c8864680b583ebL
        var hadError = false
        try {
            while (running.get() && !wsConn.socket.isClosed && !localSocket.isClosed) {
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
            hadError = true
        } finally {
            // Do NOT send WS Close (0x88) on clean uplink EOF because half-closed HTTP clients
            // still expect relayDown() to receive delayed server responses. However, bound
            // relayDown()'s per-read wait (so abandoned connections don't leak Worker sockets
            // forever) and close immediately on reset/teardown.
            if (hadError || !running.get() || localSocket.isClosed) {
                runCatching { wsConn.socket.close() }
            } else {
                runCatching {
                    if (!wsConn.socket.isClosed) {
                        wsConn.socket.soTimeout = HALF_CLOSE_DRAIN_TIMEOUT_MS
                    }
                }
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
                        8 -> break
                        9 -> {
                            synchronized(wsConn.writeLock) {
                                wsConn.sendControlPong(ctrlBuf, ctrlLen)
                                wsConn.out.flush()
                            }
                        }
                        else -> Unit
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
                if (fin) {
                    localOut.flush()
                }
            }
            localOut.flush()
        } catch (_: Exception) {
        } finally {
            runCatching { localOut.flush() }
        }
    }

    private fun tryDirectProtectedTcpRelay(
        targetHost: String,
        targetPort: Int,
        localSocket: Socket,
        localIn: InputStream,
        localOut: OutputStream,
        connectTimeoutMs: Int = CLEAN_IP_CONNECT_TIMEOUT_MS,
        initialFirstByteTimeoutMs: Int = 0,
        onFirstRemoteByte: () -> Unit = {}
    ): Boolean {
        val candidates = buildList {
            if (cleanIp.isNotBlank() && System.currentTimeMillis() >= cleanIpCooldownUntilMs.get()) {
                add(cleanIp)
            }
            add(targetHost)
        }.distinct()

        var remoteSocket: Socket? = null
        for (candidate in candidates) {
            val sock = Socket()
            activeSockets.add(sock)
            val connected = runCatching {
                sock.tcpNoDelay = true
                sock.keepAlive = true
                val addr = resolveConnectAddress(candidate)
                protectSocket(sock)
                sock.connect(InetSocketAddress(addr, targetPort), connectTimeoutMs)
                true
            }.getOrDefault(false)
            if (connected) {
                remoteSocket = sock
                break
            } else {
                activeSockets.remove(sock)
                runCatching { sock.close() }
            }
        }
        val remote = remoteSocket ?: return false

        try {
            localSocket.soTimeout = 0
            remote.soTimeout = initialFirstByteTimeoutMs
            localOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            localOut.flush()

            val remoteIn = remote.getInputStream()
            val remoteOut = remote.getOutputStream()
            val upFuture = proxyExecutor.submit {
                val buf = ByteArray(16384)
                runCatching {
                    while (running.get() && !remote.isClosed && !localSocket.isClosed) {
                        val n = localIn.read(buf)
                        if (n <= 0) break
                        remoteOut.write(buf, 0, n)
                        if (localIn.available() == 0) remoteOut.flush()
                    }
                    runCatching { remote.shutdownOutput() }
                }
            }
            val downBuf = ByteArray(16384)
            var notifiedFirstByte = false
            runCatching {
                while (running.get() && !remote.isClosed && !localSocket.isClosed) {
                    val n = remoteIn.read(downBuf)
                    if (n <= 0) break
                    if (!notifiedFirstByte) {
                        notifiedFirstByte = true
                        remote.soTimeout = 0
                        onFirstRemoteByte()
                    }
                    localOut.write(downBuf, 0, n)
                    if (remoteIn.available() == 0) localOut.flush()
                }
                runCatching { localOut.flush() }
            }
            runCatching { upFuture.cancel(false) }
            return true
        } finally {
            activeSockets.remove(remote)
            runCatching { remote.close() }
        }
    }

    private fun recordCleanIpFailure(reason: String?) {
        if (cleanIpFailureCount.incrementAndGet() >= 2) {
            cleanIpCooldownUntilMs.set(System.currentTimeMillis() + CLEAN_IP_COOLDOWN_MS)
        }
        if (loggedCleanIpFallback.compareAndSet(false, true)) {
            emitInfo("Amir CF Clean IP ($cleanIp) dial failed ($reason), falling back to direct host")
        }
    }

    private fun recordDirectDohFailure() {
        if (directDohFailureCount.incrementAndGet() >= 1) {
            directDohCooldownUntilMs.set(System.currentTimeMillis() + DIRECT_DOH_COOLDOWN_MS)
        }
    }

    private fun dialBestWebSocket(preferDirectHostFirst: Boolean = false): DialAttemptResult? {
        val cleanIpAllowed = cleanIp.isNotBlank() && System.currentTimeMillis() >= cleanIpCooldownUntilMs.get()
        if (preferDirectHostFirst) {
            val directRes = runCatching { dialDirectWebSocket(workerHost, DIRECT_CONNECT_TIMEOUT_MS) }.getOrNull()
            if (directRes != null) {
                return DialAttemptResult(directRes, usedCleanIp = false)
            }
            if (cleanIpAllowed) {
                val cleanRes = runCatching { dialDirectWebSocket(cleanIp, CLEAN_IP_CONNECT_TIMEOUT_MS) }.getOrNull()
                if (cleanRes != null) {
                    cleanIpFailureCount.set(0)
                    return DialAttemptResult(cleanRes, usedCleanIp = true)
                }
            }
            emitThrottledError("Amir WebSocket dial failed on direct host & clean IP")
            return null
        }

        var cleanFailed = false
        var cleanErrorMsg: String? = null
        if (cleanIpAllowed) {
            val cleanResult = runCatching { dialDirectWebSocket(cleanIp, CLEAN_IP_CONNECT_TIMEOUT_MS) }
            val conn = cleanResult.getOrNull()
            if (conn != null) {
                cleanIpFailureCount.set(0)
                return DialAttemptResult(conn, usedCleanIp = true)
            }
            cleanFailed = true
            cleanErrorMsg = cleanResult.exceptionOrNull()?.message
        }
        val directConn = runCatching { dialDirectWebSocket(workerHost, DIRECT_CONNECT_TIMEOUT_MS) }
            .onFailure { err ->
                emitThrottledError("Amir WebSocket dial failed: ${err.message}")
            }
            .getOrNull()
        if (directConn != null) {
            if (cleanFailed) {
                recordCleanIpFailure(cleanErrorMsg)
            }
            return DialAttemptResult(directConn, usedCleanIp = false)
        }
        return null
    }

    private fun emitInfo(msg: String) {
        if (settings.logLevel != "none" && settings.logLevel != "error") {
            onLog(msg)
        }
    }

    private fun emitThrottledError(msg: String) {
        if (settings.logLevel == "none") return
        val now = System.currentTimeMillis()
        val prev = lastErrorLogMs.get()
        if (now - prev >= 4_000L && lastErrorLogMs.compareAndSet(prev, now)) {
            onLog(msg)
        }
    }

    private fun resolveConnectAddresses(connectTarget: String): List<InetAddress> {
        val cleanTarget = connectTarget.trim().removePrefix("[").removeSuffix("]")
        if (isIpLiteral(cleanTarget)) {
            return listOf(InetAddress.getByName(cleanTarget))
        }
        val mapped = parsedDnsHosts[cleanTarget]
            ?: parsedDnsHosts["domain:$cleanTarget"]
            ?: parsedDnsHosts["full:$cleanTarget"]
            ?: resolvedHosts[cleanTarget]
        if (!mapped.isNullOrEmpty()) {
            val mappedAddrs = mapped.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
            if (mappedAddrs.isNotEmpty()) return mappedAddrs
        }
        val all = InetAddress.getAllByName(cleanTarget).toList()
        if (!settings.preferIpv6) {
            val v4 = all.filterIsInstance<Inet4Address>()
            if (v4.isNotEmpty()) return v4
        }
        return all.ifEmpty { listOf(InetAddress.getByName(cleanTarget)) }
    }

    private fun resolveConnectAddress(connectTarget: String): InetAddress {
        return resolveConnectAddresses(connectTarget).first()
    }

    private fun dialDirectWebSocket(connectTarget: String, connectTimeoutMs: Int): DirectWsConnection {
        val addrs = resolveConnectAddresses(connectTarget).take(2)
        var lastError: Throwable? = null
        for (targetAddr in addrs) {
            val res = runCatching { dialSingleAddressWebSocket(targetAddr, connectTimeoutMs) }
            if (res.isSuccess) return res.getOrThrow()
            lastError = res.exceptionOrNull()
        }
        throw lastError ?: IOException("Failed to dial $connectTarget")
    }

    private fun dialSingleAddressWebSocket(targetAddr: InetAddress, connectTimeoutMs: Int): DirectWsConnection {
        val rawSocket = Socket()
        var socketToTrack: Socket = rawSocket
        activeSockets.add(rawSocket)
        try {
            rawSocket.tcpNoDelay = true
            rawSocket.keepAlive = true
            rawSocket.receiveBufferSize = IO_BUFFER_SIZE
            rawSocket.sendBufferSize = IO_BUFFER_SIZE
            protectSocket(rawSocket)
            rawSocket.connect(InetSocketAddress(targetAddr, workerPort), connectTimeoutMs)
            rawSocket.soTimeout = connectTimeoutMs

            val finalSocket: Socket = if (isTls) {
                val sslSocket = tlsSocketFactory.createSocket(rawSocket, workerHost, workerPort, true) as SSLSocket
                sslSocket.tcpNoDelay = true
                sslSocket.soTimeout = connectTimeoutMs
                val sslParams = sslSocket.sslParameters
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isIpLiteral(workerHost)) {
                    sslParams.serverNames = listOf(SNIHostName(workerHost))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sslParams.applicationProtocols = arrayOf("http/1.1")
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

            val keyNonce = ByteArray(16).also { random.nextBytes(it) }
            val wsKey = Base64.encodeToString(keyNonce, Base64.NO_WRAP)
            val upgradeReq = buildString {
                append("GET ").append(requestTarget).append(" HTTP/1.1\r\n")
                append("Host: ").append(httpHostHeader).append("\r\n")
                append("User-Agent: Mozilla/5.0\r\n")
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

            finalSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
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

        fun sendCloseFrame() {
            val m = ByteArray(4).also { random.nextBytes(it) }
            out.write(0x88)
            out.write(0x80)
            out.write(m)
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
            wsConn = dialBestWebSocket()?.conn ?: return@withContext -1L
            val (initPacket, key) = buildInitPacket(uuidBytes, targetHost, targetPort)
            wsConn.sendBinaryFrame(initPacket, 0, initPacket.size)
            wsConn.out.flush()

            val ack = wsConn.readMessageBytes() ?: return@withContext -1L
            if (ack.size < 16) return@withContext -1L
            val decryptedAck = xorBytes(ack, key)
            val expectedAck = hmacSha256(key, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (decryptedAck.size < 16 || !MessageDigest.isEqual(decryptedAck.copyOfRange(0, 16), expectedAck)) {
                return@withContext -1L
            }

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
        const val DEFAULT_UUID = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        const val DEFAULT_WORKER_URL = "wss://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/"
        const val DEFAULT_CLEAN_IP = "172.67.70.244"
        const val DEFAULT_SOCKS_PORT = 40446
        const val SUBPROTO = "chat.v2"
        const val MAX_CHUNK = 16384
        private const val WS_MAX_HEADER_BYTES = 14
        private const val IO_BUFFER_SIZE = 65536
        private const val INITIAL_WARM_POOL_SIZE = 1
        private const val MAX_WARM_POOL_SIZE = 2
        private const val WARM_POOL_MAX_AGE_MS = 8_000L
        private const val DNS_CACHE_TTL_MS = 120_000L
        private const val POOLED_ACK_TIMEOUT_MS = 2_000
        private const val HANDSHAKE_TIMEOUT_MS = 6_000
        private const val HALF_CLOSE_DRAIN_TIMEOUT_MS = 10_000
        private const val CLEAN_IP_CONNECT_TIMEOUT_MS = 3_000
        private const val DIRECT_CONNECT_TIMEOUT_MS = 4_000
        private const val CLEAN_IP_COOLDOWN_MS = 60_000L
        private const val DIRECT_DOH_CONNECT_TIMEOUT_MS = 2_000
        private const val DIRECT_DOH_FIRST_BYTE_TIMEOUT_MS = 3_000
        private const val DIRECT_DOH_COOLDOWN_MS = 60_000L
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private val random = SecureRandom()
        private val tlsSocketFactory: SSLSocketFactory by lazy {
            runCatching {
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, null, random)
                ctx.clientSessionContext?.apply {
                    sessionCacheSize = 128
                    sessionTimeout = 600
                }
                ctx.socketFactory
            }.getOrElse { SSLSocketFactory.getDefault() as SSLSocketFactory }
        }

        private fun isLoopbackHost(host: String): Boolean {
            val clean = host.trim().lowercase().removePrefix("[").removeSuffix("]")
            return clean == "127.0.0.1" || clean.startsWith("127.") || clean == "localhost" || clean == "::1"
        }

        private fun isLoopbackTarget(
            workerHost: String,
            parsedDnsHosts: Map<String, List<String>>,
            resolvedHosts: Map<String, List<String>>
        ): Boolean {
            if (isLoopbackHost(workerHost)) return true
            val mapped = parsedDnsHosts[workerHost]
                ?: parsedDnsHosts["domain:$workerHost"]
                ?: parsedDnsHosts["full:$workerHost"]
                ?: resolvedHosts[workerHost]
            return mapped?.any { isLoopbackHost(it) } == true
        }

        private val CLOUDFLARE_RESOLVER_ADDRS: Set<InetAddress> by lazy {
            listOf(
                "1.1.1.1",
                "1.0.0.1",
                "2606:4700:4700::1111",
                "2606:4700:4700::1001"
            ).mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }.toSet()
        }

        private val CLOUDFLARE_DOH_ADDRS: Set<InetAddress> by lazy {
            listOf(
                "104.16.248.249",
                "104.16.249.249",
                "162.159.61.8",
                "172.64.41.8",
                "1.1.1.1",
                "1.0.0.1",
                "2606:4700::6810:f8f9",
                "2606:4700::6810:f9f9",
                "2a06:98c1:52::8",
                "2803:f800:53::8",
                "2606:4700:4700::1111",
                "2606:4700:4700::1001"
            ).mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }.toSet()
        }

        private fun isCloudflareResolverIp(host: String): Boolean {
            val clean = host.trim().removePrefix("[").removeSuffix("]")
            if (!isIpLiteral(clean)) return false
            val addr = runCatching { InetAddress.getByName(clean) }.getOrNull() ?: return false
            return addr in CLOUDFLARE_RESOLVER_ADDRS
        }

        private fun isCloudflareDohIp(host: String): Boolean {
            val clean = host.trim().removePrefix("[").removeSuffix("]")
            if (!isIpLiteral(clean)) return false
            val addr = runCatching { InetAddress.getByName(clean) }.getOrNull() ?: return false
            return addr in CLOUDFLARE_DOH_ADDRS
        }

        private fun extractDnsQuestionCacheKey(dns: ByteArray): String? {
            if (dns.size < 17) return null
            val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
            if (qdCount != 1) return null
            var idx = 12
            val name = StringBuilder(64)
            var jumps = 0
            while (idx < dns.size && jumps++ < 64) {
                val len = dns[idx].toInt() and 0xFF
                if (len == 0) {
                    idx++
                    break
                }
                if ((len and 0xC0) != 0 || idx + 1 + len > dns.size) return null
                if (name.isNotEmpty()) name.append('.')
                name.append(String(dns, idx + 1, len, StandardCharsets.US_ASCII).lowercase())
                idx += 1 + len
            }
            if (idx + 4 > dns.size || name.isEmpty()) return null
            val qtype = ((dns[idx].toInt() and 0xFF) shl 8) or (dns[idx + 1].toInt() and 0xFF)
            val qclass = ((dns[idx + 2].toInt() and 0xFF) shl 8) or (dns[idx + 3].toInt() and 0xFF)
            return "$name:$qtype:$qclass"
        }

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
                    return 3.toByte() to bytes
                }
            }
            val hostBytes = cleanHost.toByteArray(StandardCharsets.UTF_8)
            require(hostBytes.size <= 255) { "Target domain exceeds 255 bytes" }
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

        private fun xorBytesWithOffset(data: ByteArray, key: ByteArray, initialOffset: Int): ByteArray {
            val out = ByteArray(data.size)
            val kLen = key.size
            var k = initialOffset % kLen
            for (i in data.indices) {
                out[i] = (data[i].toInt() xor key[k].toInt()).toByte()
                k = (k + 1) % kLen
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

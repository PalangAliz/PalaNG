package com.example.core

import android.net.Uri
import com.example.data.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.SocketFactory

class AmirLocalProxy(
    private val serverConfig: ServerConfig,
    private val protectSocket: (Socket) -> Boolean = { true },
    private val onLog: (String) -> Unit = {}
) {
    // Dedicated unbounded IO pool so blocking socket reads never starve Dispatchers.IO (capped at 64 threads)
    private val proxyExecutor = Executors.newCachedThreadPool()
    private val proxyDispatcher = proxyExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + proxyDispatcher)

    private var serverSocket: ServerSocket? = null
    private var acceptJob: kotlinx.coroutines.Job? = null
    private val loggedCleanIpFallback = AtomicBoolean(false)

    val localPort: Int
        get() = serverSocket?.localPort ?: 0

    private val workerUrl: String = ConfigParser.normalizeAmirWorkerUrl(
        serverConfig.extraParam1.ifBlank { serverConfig.address }
    )
    private val workerHost: String = Uri.parse(workerUrl).host.orEmpty().ifBlank { serverConfig.address }
    private val cleanIp: String = serverConfig.extraParam2.trim().removePrefix("[").removeSuffix("]")
    private val uuidBytes: ByteArray = serverConfig.passwordOrId.trim().toByteArray(StandardCharsets.UTF_8)

    private val httpClientDirect: OkHttpClient by lazy { buildOkHttpClient(useCleanIp = false) }
    private val httpClientCleanIp: OkHttpClient? by lazy {
        if (cleanIp.isNotBlank()) buildOkHttpClient(useCleanIp = true) else null
    }

    fun start(): Int {
        val ss = ServerSocket(0, 512, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        val boundPort = ss.localPort
        onLog("Amir local SOCKS5 listening on 127.0.0.1:$boundPort -> $workerHost" +
            if (cleanIp.isNotBlank()) " (CF Clean IP: $cleanIp)" else "")

        acceptJob = scope.launch {
            while (isActive && !ss.isClosed) {
                val clientSocket = runCatching { ss.accept() }.getOrNull() ?: break
                launch {
                    handleSocksClient(clientSocket)
                }
            }
        }
        return boundPort
    }

    fun stop() {
        runCatching { acceptJob?.cancel() }
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.cancel()
        runCatching { httpClientDirect.dispatcher.executorService.shutdown() }
        runCatching { httpClientCleanIp?.dispatcher?.executorService?.shutdown() }
        runCatching { proxyDispatcher.close() }
        runCatching { proxyExecutor.shutdownNow() }
    }

    private fun buildOkHttpClient(useCleanIp: Boolean): OkHttpClient {
        val customSocketFactory = object : SocketFactory() {
            private val delegate = getDefault()

            override fun createSocket(): Socket = configure(delegate.createSocket())
            override fun createSocket(host: String?, port: Int): Socket = configure(delegate.createSocket(host, port))
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                configure(delegate.createSocket(host, port, localHost, localPort))
            override fun createSocket(host: InetAddress?, port: Int): Socket = configure(delegate.createSocket(host, port))
            override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                configure(delegate.createSocket(address, port, localAddress, localPort))

            private fun configure(s: Socket): Socket {
                runCatching {
                    s.tcpNoDelay = true // also forces SocketImpl FD creation before protect()
                    s.receiveBufferSize = SOCKET_BUFFER_BYTES
                    s.sendBufferSize = SOCKET_BUFFER_BYTES
                }
                protectSocket(s)
                return s
            }
        }

        // Disable permessage-deflate compression (matches Cl4.py compression=None):
        // compressing high-entropy XOR binary frames wastes CPU and adds flush latency.
        val disableWsCompressionInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .removeHeader("Sec-WebSocket-Extensions")
                .build()
            chain.proceed(req)
        }

        val dispatcher = Dispatcher().apply {
            maxRequests = 512
            maxRequestsPerHost = 512
        }

        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .proxy(Proxy.NO_PROXY)
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(disableWsCompressionInterceptor)
            .connectTimeout(if (useCleanIp) 5 else 10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .socketFactory(customSocketFactory)

        if (useCleanIp && cleanIp.isNotBlank()) {
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (hostname.equals(workerHost, ignoreCase = true)) {
                        return InetAddress.getAllByName(cleanIp).toList()
                    }
                    return Dns.SYSTEM.lookup(hostname)
                }
            })
        }
        return builder.build()
    }

    private suspend fun handleSocksClient(socket: Socket) {
        var ws: WebSocket? = null
        try {
            socket.soTimeout = 10_000
            socket.tcpNoDelay = true
            runCatching {
                socket.receiveBufferSize = SOCKET_BUFFER_BYTES
                socket.sendBufferSize = SOCKET_BUFFER_BYTES
            }
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. SOCKS5 Greeting
            val header = readExact(input, 2)
            if (header[0].toInt() != 5) return
            val nMethods = header[1].toInt() and 0xFF
            readExact(input, nMethods)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // 2. SOCKS5 Connect Request
            val req = readExact(input, 4)
            if (req[0].toInt() != 5) return
            if (req[1].toInt() != 1) {
                runCatching {
                    output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                }
                return
            }
            val atyp = req[3].toInt() and 0xFF
            val targetHost = when (atyp) {
                1 -> {
                    val addr = readExact(input, 4)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                3 -> {
                    val len = readExact(input, 1)[0].toInt() and 0xFF
                    String(readExact(input, len), StandardCharsets.UTF_8)
                }
                4 -> {
                    val addr = readExact(input, 16)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                else -> return
            }
            val portBytes = readExact(input, 2)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
            socket.soTimeout = 0

            // 3. Connect WebSocket to Cloudflare Worker (try Clean IP first, fallback to direct)
            val wsConn = openWebSocket()
            if (wsConn == null) {
                runCatching {
                    output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                }
                return
            }
            ws = wsConn.webSocket

            // 4. Send Amir init packet
            val (initPacket, key) = buildInitPacket(uuidBytes, targetHost, targetPort)
            if (!wsConn.webSocket.send(initPacket.toByteString())) return

            // 5. Wait for encrypted HMAC("OK") acknowledgement
            val ack = withTimeoutOrNull(10_000L) {
                wsConn.incoming.receiveCatching().getOrNull()
            } ?: return
            if (ack.size < 16) return
            val decryptedAck = xorBytes(ack, key)
            val expectedAck = hmacSha256(key, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (!decryptedAck.contentEquals(expectedAck)) return

            // 6. Send SOCKS5 success response
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            // 7. Bidirectional XOR-encrypted relay
            coroutineScope {
                val upJob = launch {
                    val buf = ByteArray(MAX_CHUNK)
                    var hadError = false
                    try {
                        while (isActive) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            val encrypted = xorBytesSlice(buf, read, key)
                            while (wsConn.webSocket.queueSize() > BUFFER_MAX && isActive) {
                                delay(2)
                            }
                            if (!wsConn.webSocket.send(encrypted.toByteString())) {
                                hadError = true
                                break
                            }
                        }
                    } catch (_: Exception) {
                        hadError = true
                    } finally {
                        if (hadError) {
                            runCatching { wsConn.webSocket.close(1000, "err") }
                        }
                    }
                }

                val downJob = launch {
                    try {
                        for (msg in wsConn.incoming) {
                            if (msg.isNotEmpty()) {
                                val decrypted = xorBytes(msg, key)
                                output.write(decrypted)
                            }
                        }
                        output.flush()
                    } catch (_: Exception) {
                    } finally {
                        runCatching { socket.close() }
                    }
                }

                upJob.join()
                downJob.join()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
        } finally {
            runCatching { ws?.close(1000, "closed") }
            runCatching { socket.close() }
        }
    }

    private data class WsConnection(
        val webSocket: WebSocket,
        val incoming: Channel<ByteArray>
    )

    private suspend fun openWebSocket(): WsConnection? {
        val cleanClient = httpClientCleanIp
        if (cleanClient != null) {
            val cleanAttempt = runCatching { dialWebSocket(cleanClient, 5_000L) }
            if (cleanAttempt.isSuccess) return cleanAttempt.getOrNull()
            if (loggedCleanIpFallback.compareAndSet(false, true)) {
                onLog("Amir CF Clean IP ($cleanIp) dial failed (${cleanAttempt.exceptionOrNull()?.message}), falling back to direct host")
            }
        }
        return runCatching { dialWebSocket(httpClientDirect, 10_000L) }.getOrNull()
    }

    private suspend fun dialWebSocket(client: OkHttpClient, timeoutMs: Long = 10_000L): WsConnection {
        val httpUrl = when {
            workerUrl.startsWith("wss://", ignoreCase = true) -> "https://" + workerUrl.substring(6)
            workerUrl.startsWith("ws://", ignoreCase = true) -> "http://" + workerUrl.substring(5)
            else -> workerUrl
        }
        val request = Request.Builder()
            .url(httpUrl)
            .header("Host", workerHost)
            .header("Sec-WebSocket-Protocol", SUBPROTO)
            .build()

        val opened = CompletableDeferred<Unit>()
        val incoming = Channel<ByteArray>(capacity = Channel.UNLIMITED)

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                incoming.trySend(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                incoming.close()
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                incoming.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) {
                    opened.completeExceptionally(t)
                }
                incoming.close(t)
            }
        })

        try {
            withTimeout(timeoutMs) {
                opened.await()
            }
            return WsConnection(ws, incoming)
        } catch (t: Throwable) {
            ws.cancel()
            incoming.close()
            throw t
        }
    }

    suspend fun measureDelay(testUrl: String): Long {
        if (uuidBytes.isEmpty() || workerHost.isBlank()) return -1L
        val parsed = Uri.parse(testUrl)
        val targetHost = parsed.host?.takeIf { it.isNotBlank() } ?: "www.gstatic.com"
        val targetPort = if (parsed.port > 0) parsed.port else 80
        val path = parsed.encodedPath?.takeIf { it.isNotBlank() } ?: "/generate_204"
        val startMs = System.currentTimeMillis()
        var ws: WebSocket? = null
        return try {
            val wsConn = openWebSocket() ?: return -1L
            ws = wsConn.webSocket
            val (initPacket, key) = buildInitPacket(uuidBytes, targetHost, targetPort)
            if (!ws.send(initPacket.toByteString())) return -1L

            val ack = withTimeoutOrNull(8_000L) {
                wsConn.incoming.receiveCatching().getOrNull()
            } ?: return -1L
            if (ack.size < 16) return -1L
            val decryptedAck = xorBytes(ack, key)
            val expectedAck = hmacSha256(key, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (!decryptedAck.contentEquals(expectedAck)) return -1L

            val httpReq = "HEAD $path HTTP/1.1\r\nHost: $targetHost\r\nConnection: close\r\n\r\n"
                .toByteArray(StandardCharsets.US_ASCII)
            if (!ws.send(xorBytes(httpReq, key).toByteString())) return -1L

            val resp = withTimeoutOrNull(8_000L) {
                wsConn.incoming.receiveCatching().getOrNull()
            } ?: return -1L
            if (resp.isEmpty()) return -1L
            (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        } finally {
            runCatching { ws?.close(1000, "test_done") }
        }
    }

    companion object {
        private const val SUBPROTO = "chat.v2"
        private const val MAX_CHUNK = 32768
        private const val SOCKET_BUFFER_BYTES = 131072
        private const val BUFFER_MAX = 1_048_576L
        private val random = SecureRandom()

        fun deriveKey(uuidBytes: ByteArray, nonce: ByteArray): ByteArray {
            require(uuidBytes.isNotEmpty()) { "UUID cannot be empty" }
            require(nonce.size == 16) { "Nonce must be 16 bytes" }
            return ByteArray(16) { i ->
                (uuidBytes[i % uuidBytes.size].toInt() xor nonce[i].toInt()).toByte()
            }
        }

        fun buildInitPacket(uuidBytes: ByteArray, host: String, port: Int, payload: ByteArray = ByteArray(0)): Pair<ByteArray, ByteArray> {
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

        fun xorBytes(data: ByteArray, key: ByteArray): ByteArray {
            val out = ByteArray(data.size)
            val kLen = key.size
            for (i in data.indices) {
                out[i] = (data[i].toInt() xor key[i % kLen].toInt()).toByte()
            }
            return out
        }

        fun xorBytesSlice(data: ByteArray, length: Int, key: ByteArray): ByteArray {
            val out = ByteArray(length)
            val kLen = key.size
            for (i in 0 until length) {
                out[i] = (data[i].toInt() xor key[i % kLen].toInt()).toByte()
            }
            return out
        }

        fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun readExact(input: InputStream, count: Int): ByteArray {
            val buf = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val r = input.read(buf, offset, count - offset)
                if (r < 0) throw EOFException("Unexpected EOF")
                offset += r
            }
            return buf
        }
    }
}

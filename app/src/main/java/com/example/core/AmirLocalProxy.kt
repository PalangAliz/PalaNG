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
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
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
    private val daemonFactory = ThreadFactory { r ->
        Thread(r, "AmirProxyWorker").apply { isDaemon = true }
    }
    private val proxyExecutor = Executors.newCachedThreadPool(daemonFactory)
    private val dialRaceExecutor = Executors.newCachedThreadPool(daemonFactory)
    private val warmPoolExecutor = Executors.newFixedThreadPool(4, daemonFactory)
    private val workerDnsExecutor = Executors.newCachedThreadPool(daemonFactory)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeDatagramSockets = ConcurrentHashMap.newKeySet<DatagramSocket>()
    private val warmPool = ConcurrentLinkedDeque<PooledWsConnection>()
    private val pendingWarmups = AtomicInteger(0)
    private val hasSuccessfulDial = AtomicBoolean(false)
    private val warmPoolBackoffUntilMs = AtomicLong(0L)
    private val dnsCache = ConcurrentHashMap<String, CachedDnsResponse>()
    private val dnsInFlight = ConcurrentHashMap<String, CompletableFuture<ByteArray?>>()
    private val workerDnsCache = ConcurrentHashMap<String, WorkerDnsCacheEntry>()
    private val running = AtomicBoolean(false)
    private val loggedCleanIpFallback = AtomicBoolean(false)
    private val cleanIpFailureCount = AtomicInteger(0)
    private val cleanIpCooldownUntilMs = AtomicLong(0L)
    private val preferredEndpointIp = AtomicReference<String?>(null)
    private val throttledEndpoints = ConcurrentHashMap<String, Long>()
    private val directDohFailureCount = AtomicInteger(0)
    private val directDohCooldownUntilMs = AtomicLong(0L)
    private val lastErrorLogMs = AtomicLong(0L)
    private val uplinkBytesCounter = AtomicLong(0L)
    private val downlinkBytesCounter = AtomicLong(0L)
    private val customHostOverrides = parseHostOverrides(settings.dnsHosts)

    private data class PooledWsConnection(
        val conn: DirectWsConnection,
        val createdAtMs: Long,
        val usedCleanIp: Boolean,
        val endpointIp: String = ""
    )

    private data class ObtainedWs(
        val conn: DirectWsConnection,
        val fromPool: Boolean,
        val usedCleanIp: Boolean,
        val endpointIp: String = ""
    )

    private data class DialAttemptResult(
        val conn: DirectWsConnection,
        val usedCleanIp: Boolean,
        val endpointIp: String = ""
    )

    private class DialAttemptHandle {
        private val cancelled = AtomicBoolean(false)
        private val currentSocket = AtomicReference<Socket?>(null)

        fun isCancelled(): Boolean = cancelled.get()

        fun setSocket(socket: Socket) {
            currentSocket.set(socket)
            if (cancelled.get()) {
                runCatching { socket.close() }
            }
        }

        fun cancel() {
            cancelled.set(true)
            currentSocket.get()?.let { s ->
                runCatching { s.close() }
            }
        }
    }

    @Volatile
    internal var testTlsSocketFactory: SSLSocketFactory? = null

    @Volatile
    internal var testHostnameVerifier: HostnameVerifier? = null

    private data class TunnelSession(
        val conn: DirectWsConnection,
        val key: ByteArray,
        val endpointIp: String
    )

    private data class RelayDownResult(
        val receivedBytes: Long,
        val hadError: Boolean
    )

    private data class EndpointCandidate(
        val addr: InetAddress,
        val ipString: String,
        val isCleanIp: Boolean,
        val connectTimeoutMs: Int
    )

    private data class CachedDnsResponse(
        val payload: ByteArray,
        val expiresAtMs: Long,
        val staleUntilMs: Long = expiresAtMs
    )

    private data class WorkerDnsCacheEntry(
        val addrs: List<InetAddress>,
        val expiresAtMs: Long
    )

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var httpServerSocket: ServerSocket? = null

    @Volatile
    private var dnsDatagramSocket: DatagramSocket? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: 0

    val localSocksPort: Int
        get() = serverSocket?.localPort ?: 0

    val localHttpPort: Int
        get() = httpServerSocket?.localPort ?: localSocksPort

    val localDnsPort: Int
        get() = dnsDatagramSocket?.localPort ?: 0

    fun isRunning(): Boolean = running.get()

    fun consumeTrafficDelta(): Pair<Long, Long> {
        val up = uplinkBytesCounter.getAndSet(0L)
        val down = downlinkBytesCounter.getAndSet(0L)
        return up to down
    }

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

    private val enableWarmPool: Boolean = !isLoopbackTarget(workerHost, customHostOverrides, resolvedHosts)

    fun start(preferredPort: Int = 0): Int {
        return startDual(
            httpPreferredPort = preferredPort,
            socksPreferredPort = preferredPort,
            listenHost = "127.0.0.1"
        ).second
    }

    fun startDual(
        httpPreferredPort: Int = 10809,
        socksPreferredPort: Int = 10808,
        listenHost: String = "127.0.0.1",
        dnsPreferredPort: Int = -1
    ): Pair<Int, Int> {
        val bindAddr = InetAddress.getByName(listenHost.ifBlank { "127.0.0.1" })
        val socksSs = bindServerSocket(bindAddr, socksPreferredPort)
        serverSocket = socksSs

        val separateHttp = httpPreferredPort != socksPreferredPort || socksPreferredPort == 0
        val httpSs = if (separateHttp && httpPreferredPort > 0) {
            bindServerSocket(bindAddr, httpPreferredPort, excludePort = socksSs.localPort)
        } else {
            null
        }
        httpServerSocket = httpSs

        val dnsDs = if (dnsPreferredPort >= 0) {
            val loopback = InetAddress.getByName("127.0.0.1")
            val boundDns = if (dnsPreferredPort in 1024..65535) {
                runCatching { DatagramSocket(dnsPreferredPort, loopback) }.getOrNull()
                    ?: DatagramSocket(0, loopback)
            } else {
                DatagramSocket(0, loopback)
            }
            activeDatagramSockets.add(boundDns)
            boundDns
        } else {
            null
        }
        dnsDatagramSocket = dnsDs

        running.set(true)
        val boundSocksPort = socksSs.localPort
        val boundHttpPort = httpSs?.localPort ?: boundSocksPort
        val boundDnsPort = dnsDs?.localPort ?: 0
        emitInfo(
            "AMIR proxy listening on $listenHost (HTTP/APN:$boundHttpPort, SOCKS5:$boundSocksPort" +
                (if (boundDnsPort > 0) ", LocalDNS:$boundDnsPort" else "") +
                ") -> $workerHost" +
                (if (cleanIp.isNotBlank()) " (CF Clean IP: $cleanIp)" else "")
        )

        // Pre-warm non-loopback targets immediately so initial connections reuse ready TLS+WS sessions
        if (enableWarmPool && INITIAL_WARM_POOL_SIZE > 0 && uuidBytes.isNotEmpty() && workerHost.isNotBlank()) {
            scheduleWarmPoolRefill(targetCount = INITIAL_WARM_POOL_SIZE, allowUnproven = true)
        }

        acceptLoop(socksSs)
        if (httpSs != null) {
            acceptLoop(httpSs)
        }
        if (dnsDs != null) {
            acceptLocalDnsLoop(dnsDs)
        }
        return boundHttpPort to boundSocksPort
    }

    private fun bindServerSocket(
        bindAddr: InetAddress,
        preferredPort: Int,
        excludePort: Int = -1
    ): ServerSocket {
        if (preferredPort in 1024..65535 && preferredPort != excludePort) {
            val preferred = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindAddr, preferredPort), 1024)
                }
            }.getOrNull()
            if (preferred != null) return preferred
        }
        return ServerSocket(0, 1024, bindAddr)
    }

    private fun acceptLoop(listener: ServerSocket) {
        proxyExecutor.execute {
            while (running.get() && !listener.isClosed) {
                val clientSocket = try {
                    listener.accept()
                } catch (_: Exception) {
                    break
                }
                proxyExecutor.execute {
                    handleIncomingClient(clientSocket)
                }
            }
        }
    }

    private fun acceptLocalDnsLoop(dnsSocket: DatagramSocket) {
        proxyExecutor.execute {
            val defaultDnsTarget = resolveConfiguredUpstreamDnsIp()
            val buf = ByteArray(4096)
            while (running.get() && !dnsSocket.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    dnsSocket.receive(packet)
                } catch (_: Exception) {
                    break
                }
                val query = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val clientAddr = packet.socketAddress
                if (query.size < 12) continue
                proxyExecutor.execute {
                    val response = resolveDnsQueryOverAmir(defaultDnsTarget, query) ?: return@execute
                    runCatching {
                        dnsSocket.send(DatagramPacket(response, response.size, clientAddr))
                    }
                }
            }
        }
    }

    private fun resolveConfiguredUpstreamDnsIp(): String {
        val extracted = XrayConfigBuilder.extractDnsHost(settings.remoteDns)
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            .orEmpty()
        if (extracted.isNotEmpty() && isIpLiteral(extracted) && !isCloudflareResolverIp(extracted) && !isCloudflareDohIp(extracted)) {
            return extracted
        }
        return "8.8.8.8"
    }

    fun stop() {
        running.set(false)
        runCatching { dnsDatagramSocket?.close() }
        dnsDatagramSocket = null
        runCatching { httpServerSocket?.close() }
        httpServerSocket = null
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
        dnsInFlight.values.forEach { future ->
            runCatching { future.cancel(true) }
        }
        dnsInFlight.clear()
        dnsCache.clear()
        workerDnsCache.clear()
        runCatching { warmPoolExecutor.shutdownNow() }
        runCatching { dialRaceExecutor.shutdownNow() }
        runCatching { workerDnsExecutor.shutdownNow() }
        runCatching { proxyExecutor.shutdownNow() }
    }

    private fun scheduleWarmPoolRefill(
        targetCount: Int = MAX_WARM_POOL_SIZE,
        allowUnproven: Boolean = false
    ) {
        if (!enableWarmPool || targetCount <= 0 || !running.get() || warmPoolExecutor.isShutdown) return
        if ((!allowUnproven && !hasSuccessfulDial.get()) || System.currentTimeMillis() < warmPoolBackoffUntilMs.get()) return
        val currentSize = warmPool.size + pendingWarmups.get()
        val needed = (targetCount - currentSize).coerceAtLeast(0)
        repeat(needed) {
            if (pendingWarmups.incrementAndGet() > MAX_WARM_POOL_SIZE + 2) {
                pendingWarmups.decrementAndGet()
                return
            }
            runCatching {
                warmPoolExecutor.execute {
                    try {
                        if (!running.get() || System.currentTimeMillis() < warmPoolBackoffUntilMs.get()) return@execute
                        val dialed = dialBestWebSocket()
                        if (dialed == null) {
                            warmPoolBackoffUntilMs.set(System.currentTimeMillis() + WARM_POOL_BACKOFF_MS)
                            return@execute
                        }
                        if (!running.get() || warmPool.size >= MAX_WARM_POOL_SIZE) {
                            activeSockets.remove(dialed.conn.socket)
                            runCatching { dialed.conn.socket.close() }
                            return@execute
                        }
                        warmPool.offerLast(
                            PooledWsConnection(
                                conn = dialed.conn,
                                createdAtMs = System.currentTimeMillis(),
                                usedCleanIp = dialed.usedCleanIp,
                                endpointIp = dialed.endpointIp
                            )
                        )
                    } catch (_: Exception) {
                        warmPoolBackoffUntilMs.set(System.currentTimeMillis() + WARM_POOL_BACKOFF_MS)
                    } finally {
                        pendingWarmups.decrementAndGet()
                    }
                }
            }.onFailure {
                pendingWarmups.decrementAndGet()
            }
        }
    }

    private fun obtainWebSocket(forDns: Boolean = false): ObtainedWs? {
        val now = System.currentTimeMillis()
        // Reserve up to 2 warm sockets exclusively for user TCP data connections when DNS bursts occur
        if (!forDns || warmPool.size > 2) {
            while (true) {
                val candidate = warmPool.pollFirst() ?: break
                val age = now - candidate.createdAtMs
                val alive = !candidate.conn.socket.isClosed &&
                    candidate.conn.socket.isConnected &&
                    age in 0L..WARM_POOL_MAX_AGE_MS
                if (alive) {
                    scheduleWarmPoolRefill()
                    return ObtainedWs(
                        conn = candidate.conn,
                        fromPool = true,
                        usedCleanIp = candidate.usedCleanIp,
                        endpointIp = candidate.endpointIp
                    )
                } else {
                    activeSockets.remove(candidate.conn.socket)
                    runCatching { candidate.conn.socket.close() }
                }
            }
        }
        val direct = dialBestWebSocket()
        if (direct != null) {
            scheduleWarmPoolRefill()
        }
        return direct?.let { ObtainedWs(it.conn, fromPool = false, usedCleanIp = it.usedCleanIp, endpointIp = it.endpointIp) }
    }

    private fun handleIncomingClient(localSocket: Socket) {
        activeSockets.add(localSocket)
        var delegated = false
        try {
            localSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
            localSocket.tcpNoDelay = true
            val localIn = BufferedInputStream(localSocket.getInputStream(), IO_BUFFER_SIZE)
            val localOut = localSocket.getOutputStream()

            localIn.mark(4)
            val firstByte = localIn.read()
            if (firstByte < 0) return
            localIn.reset()

            delegated = true
            if (firstByte == 0x05) {
                handleSocksClient(localSocket, localIn, localOut)
            } else {
                handleHttpProxyClient(localSocket, localIn, localOut)
            }
        } catch (e: Exception) {
            emitThrottledError("Amir proxy accept error: ${e.message}")
        } finally {
            if (!delegated) {
                activeSockets.remove(localSocket)
                runCatching { localSocket.close() }
            }
        }
    }

    private fun handleHttpProxyClient(
        localSocket: Socket,
        localIn: BufferedInputStream,
        localOut: OutputStream
    ) {
        var wsConn: DirectWsConnection? = null
        try {
            val requestLine = readHttpLine(localIn)?.trim().orEmpty()
            if (requestLine.isEmpty()) return
            val parts = requestLine.split(Regex("\\s+"))
            if (parts.size < 2) {
                writeSimpleHttpError(localOut, 400, "Bad Request")
                return
            }
            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val httpVersion = parts.getOrNull(2)?.takeIf { it.startsWith("HTTP/", ignoreCase = true) } ?: "HTTP/1.1"

            val rawHeaders = mutableListOf<Pair<String, String>>()
            var hostHeader = ""
            var proxyAuthHeader = ""
            while (true) {
                val line = readHttpLine(localIn) ?: return
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                    rawHeaders += name to value
                    if (name.equals("Host", ignoreCase = true)) {
                        hostHeader = value
                    } else if (name.equals("Proxy-Authorization", ignoreCase = true)) {
                        proxyAuthHeader = value
                    }
                }
            }

            if (!isHttpProxyAuthorized(proxyAuthHeader)) {
                runCatching {
                    localOut.write(
                        (
                            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                "Proxy-Authenticate: Basic realm=\"PalaNG AMIR\"\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(StandardCharsets.US_ASCII)
                    )
                    localOut.flush()
                }
                return
            }

            if (method == "CONNECT") {
                val (targetHost, targetPort) = parseHostPort(rawTarget, defaultPort = 443)
                if (targetHost.isBlank() || targetPort !in 1..65535) {
                    writeSimpleHttpError(localOut, 400, "Bad Request")
                    return
                }
                val session = establishAmirTunnel(targetHost, targetPort)
                if (session == null) {
                    writeSimpleHttpError(localOut, 502, "Bad Gateway")
                    return
                }
                wsConn = session.conn
                val activeKey = session.key

                localSocket.soTimeout = 0
                wsConn.socket.soTimeout = WS_IDLE_KEEPALIVE_MS
                localOut.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: PalaNG-AMIR\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                localOut.flush()

                val boundWs = wsConn
                val upFuture = proxyExecutor.submit {
                    relayUp(localSocket, localIn, boundWs, activeKey)
                }
                val downRes = relayDown(boundWs, localOut, activeKey)
                if (downRes.hadError && downRes.receivedBytes == 0L) {
                    markEndpointThrottled(session.endpointIp)
                }
                runCatching { upFuture.cancel(false) }
                return
            }

            // Plain HTTP Proxy request (e.g. GET http://example.com/path HTTP/1.1)
            val parsedTarget = parseHttpForwardTarget(rawTarget, hostHeader)
            if (parsedTarget == null) {
                writeSimpleHttpError(localOut, 400, "Bad Request")
                return
            }
            val (targetHost, targetPort, originPath) = parsedTarget

            val session = establishAmirTunnel(targetHost, targetPort)
            if (session == null) {
                writeSimpleHttpError(localOut, 502, "Bad Gateway")
                return
            }
            wsConn = session.conn
            val activeKey = session.key

            val reqBuilder = StringBuilder(512)
            reqBuilder.append(method).append(' ').append(originPath).append(' ').append(httpVersion).append("\r\n")
            var sawHost = false
            for ((name, value) in rawHeaders) {
                if (name.equals("Proxy-Connection", ignoreCase = true) ||
                    name.equals("Proxy-Authorization", ignoreCase = true) ||
                    name.equals("Connection", ignoreCase = true) ||
                    name.equals("Keep-Alive", ignoreCase = true)
                ) {
                    continue
                }
                if (name.equals("Host", ignoreCase = true)) sawHost = true
                reqBuilder.append(name).append(": ").append(value).append("\r\n")
            }
            if (!sawHost) {
                val formattedHost = if (targetHost.contains(':')) "[$targetHost]" else targetHost
                val hVal = if (targetPort == 80) formattedHost else "$formattedHost:$targetPort"
                reqBuilder.append("Host: ").append(hVal).append("\r\n")
            }
            reqBuilder.append("Connection: close\r\n\r\n")
            val initialBytes = reqBuilder.toString().toByteArray(StandardCharsets.ISO_8859_1)

            localSocket.soTimeout = 0
            wsConn.socket.soTimeout = WS_IDLE_KEEPALIVE_MS

            // Forward rewritten request headers over AMIR WS before entering full-duplex body/response relay
            var offset = 0
            while (offset < initialBytes.size) {
                val chunkLen = minOf(MAX_CHUNK, initialBytes.size - offset)
                val slice = initialBytes.copyOfRange(offset, offset + chunkLen)
                val enc = xorBytes(slice, activeKey)
                wsConn.sendBinaryFrame(enc, 0, enc.size)
                uplinkBytesCounter.addAndGet(chunkLen.toLong())
                offset += chunkLen
            }
            wsConn.writeLock.lock()
            try {
                wsConn.out.flush()
            } finally {
                wsConn.writeLock.unlock()
            }

            val boundWs = wsConn
            val upFuture = proxyExecutor.submit {
                relayUp(localSocket, localIn, boundWs, activeKey)
            }
            val downRes = relayDown(boundWs, localOut, activeKey)
            if (downRes.hadError && downRes.receivedBytes == 0L) {
                markEndpointThrottled(session.endpointIp)
            }
            runCatching { upFuture.cancel(false) }
        } catch (e: Exception) {
            emitThrottledError("Amir HTTP proxy error: ${e.message}")
        } finally {
            closeLocalAndWs(localSocket, wsConn)
        }
    }

    private fun establishAmirTunnel(targetHost: String, targetPort: Int): TunnelSession? {
        val dialHost = if (targetPort == 53 && isCloudflareResolverIp(targetHost)) {
            "8.8.8.8"
        } else {
            targetHost
        }
        val obtained = obtainWebSocket() ?: return null
        var wsConn: DirectWsConnection? = obtained.conn
        var usedEndpointIp = obtained.endpointIp
        val (initPacket, key) = buildInitPacket(uuidBytes, dialHost, targetPort)
        var activeKey = key
        var ackBytes: ByteArray? = null
        var handshakeFailed = false
        val ackStartMs = System.currentTimeMillis()
        try {
            wsConn!!.socket.soTimeout = if (obtained.fromPool) POOLED_ACK_TIMEOUT_MS else HANDSHAKE_TIMEOUT_MS
            wsConn.sendBinaryFrame(initPacket, 0, initPacket.size)
            wsConn.out.flush()
            ackBytes = wsConn.readMessageBytes()
            if (ackBytes == null || ackBytes.size < 16) {
                handshakeFailed = true
            }
        } catch (_: Exception) {
            handshakeFailed = true
        }
        val ackElapsedMs = System.currentTimeMillis() - ackStartMs
        if (!handshakeFailed && ackElapsedMs > SLOW_ACK_THRESHOLD_MS) {
            markEndpointThrottled(usedEndpointIp)
        }
        if (handshakeFailed) {
            if (!obtained.fromPool) {
                markEndpointThrottled(obtained.endpointIp)
            }
            activeSockets.remove(wsConn!!.socket)
            runCatching { wsConn.socket.close() }
            // Only bypass Clean IP on retry if a freshly dialed Clean IP failed post-upgrade;
            // pooled sockets can simply expire while idle on Cloudflare edge.
            val forceDirectFirst = !obtained.fromPool && obtained.usedCleanIp
            val retryDial = dialBestWebSocket(preferDirectHostFirst = forceDirectFirst)
            wsConn = retryDial?.conn
            if (wsConn != null) {
                usedEndpointIp = retryDial.endpointIp
                val (retryInit, retryKey) = buildInitPacket(uuidBytes, dialHost, targetPort)
                activeKey = retryKey
                ackBytes = runCatching {
                    wsConn.socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                    wsConn.sendBinaryFrame(retryInit, 0, retryInit.size)
                    wsConn.out.flush()
                    wsConn.readMessageBytes()
                }.getOrNull()
                if (ackBytes == null || ackBytes.size < 16) {
                    markEndpointThrottled(retryDial.endpointIp)
                }
                if (obtained.usedCleanIp && !obtained.fromPool && ackBytes != null && ackBytes.size >= 16 && !retryDial.usedCleanIp) {
                    recordCleanIpFailure("post-upgrade handshake dropped on $cleanIp")
                }
            } else {
                ackBytes = null
            }
        }

        if (wsConn == null || ackBytes == null || ackBytes.size < 16) {
            emitThrottledError("Amir tunnel handshake failed for $dialHost:$targetPort")
            wsConn?.let { conn ->
                activeSockets.remove(conn.socket)
                runCatching { conn.socket.close() }
            }
            return null
        }

        val decryptedAck = xorBytes(ackBytes, activeKey)
        val expectedAck = hmacSha256(activeKey, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
        if (decryptedAck.size < 16 || !MessageDigest.isEqual(decryptedAck.copyOfRange(0, 16), expectedAck)) {
            markEndpointThrottled(usedEndpointIp)
            emitThrottledError("Amir HMAC ACK mismatch for $dialHost:$targetPort")
            activeSockets.remove(wsConn.socket)
            runCatching { wsConn.socket.close() }
            return null
        }
        return TunnelSession(wsConn, activeKey, usedEndpointIp)
    }

    private fun isHttpProxyAuthorized(proxyAuthHeader: String): Boolean {
        val reqUser = settings.localProxyUsername
        val reqPass = settings.localProxyPassword
        if (reqUser.isEmpty() && reqPass.isEmpty()) return true
        if (!proxyAuthHeader.startsWith("Basic ", ignoreCase = true)) return false
        val encoded = proxyAuthHeader.substring(6).trim()
        val decoded = runCatching {
            String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
        }.getOrNull() ?: return false
        return decoded == "$reqUser:$reqPass"
    }

    private fun closeLocalAndWs(localSocket: Socket, wsConn: DirectWsConnection?) {
        activeSockets.remove(localSocket)
        runCatching { localSocket.close() }
        wsConn?.let { conn ->
            if (!conn.closed.compareAndSet(false, true)) return@let
            activeSockets.remove(conn.socket)
            if (!conn.socket.isClosed) {
                var locked = false
                try {
                    // Never block teardown behind a stalled relayUp write
                    locked = conn.writeLock.tryLock(30, TimeUnit.MILLISECONDS)
                    if (locked) {
                        conn.socket.soTimeout = 400
                        conn.sendCloseFrame()
                        conn.out.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    if (locked) {
                        runCatching { conn.writeLock.unlock() }
                    }
                }
            }
            runCatching { conn.socket.close() }
        }
    }

    private fun handleSocksClient(
        localSocket: Socket,
        localIn: BufferedInputStream,
        localOut: OutputStream
    ) {
        var wsConn: DirectWsConnection? = null
        try {
            // 1. SOCKS5 Greeting (No-Auth 0x00, plus RFC 1929 0x02 when proxy credentials are configured)
            val header = readExactBytes(localIn, 2)
            if (header[0].toInt() != 5) return
            val nMethods = header[1].toInt() and 0xFF
            if (nMethods == 0) return
            val methods = readExactBytes(localIn, nMethods)
            val authConfigured = settings.localProxyUsername.isNotEmpty() || settings.localProxyPassword.isNotEmpty()
            val supportsNoAuth = methods.any { (it.toInt() and 0xFF) == 0x00 }
            val supportsUserPass = methods.any { (it.toInt() and 0xFF) == 0x02 }
            if (authConfigured && supportsUserPass) {
                localOut.write(byteArrayOf(0x05, 0x02))
                localOut.flush()
                val ver = readExactBytes(localIn, 1)[0].toInt() and 0xFF
                if (ver != 1) return
                val uLen = readExactBytes(localIn, 1)[0].toInt() and 0xFF
                val user = String(readExactBytes(localIn, uLen), StandardCharsets.UTF_8)
                val pLen = readExactBytes(localIn, 1)[0].toInt() and 0xFF
                val pass = String(readExactBytes(localIn, pLen), StandardCharsets.UTF_8)
                if (user != settings.localProxyUsername || pass != settings.localProxyPassword) {
                    runCatching {
                        localOut.write(byteArrayOf(0x01, 0x01))
                        localOut.flush()
                    }
                    return
                }
                localOut.write(byteArrayOf(0x01, 0x00))
                localOut.flush()
            } else if (!authConfigured && supportsNoAuth) {
                localOut.write(byteArrayOf(0x05, 0x00))
                localOut.flush()
            } else {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0xFF.toByte()))
                    localOut.flush()
                }
                return
            }

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

            // Reject Android Private DNS (DoT 853) probes aimed at Cloudflare resolver IPs immediately so fallback is instant
            if (targetPort == 853 && isCloudflareResolverIp(targetHost)) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
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

            // 4-6. Establish AMIR Worker WebSocket tunnel and verify HMAC ACK
            val session = establishAmirTunnel(dialHost, targetPort)
            if (session == null) {
                runCatching {
                    localOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    localOut.flush()
                }
                return
            }
            wsConn = session.conn
            val activeKey = session.key

            localSocket.soTimeout = 0
            wsConn.socket.soTimeout = WS_IDLE_KEEPALIVE_MS

            // Send SOCKS5 success response
            localOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            localOut.flush()

            // 7. Full-duplex XOR relay (keep WS alive briefly on clean half-close so HTTP responses drain,
            //    while closing immediately on aborted/closed client sockets so Worker slots are never leaked)
            val boundWs = wsConn
            val upFuture = proxyExecutor.submit {
                relayUp(localSocket, localIn, boundWs, activeKey)
            }
            val downRes = relayDown(boundWs, localOut, activeKey)
            if (downRes.hadError && downRes.receivedBytes == 0L) {
                markEndpointThrottled(session.endpointIp)
            }
            runCatching { upFuture.cancel(false) }
        } catch (e: Exception) {
            emitThrottledError("Amir proxy connection error: ${e.message}")
        } finally {
            closeLocalAndWs(localSocket, wsConn)
        }
    }

    private fun handleSocksUdpAssociate(
        controlSocket: Socket,
        controlIn: InputStream,
        controlOut: OutputStream
    ) {
        val udpSocket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        udpSocket.soTimeout = UDP_ASSOCIATE_IDLE_TIMEOUT_MS
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

        // Redirect 1.1.1.1 / 1.0.0.1 to 8.8.8.8 so Cloudflare Worker connect() succeeds,
        // while keeping original socksHeader so Android/Xray matches the UDP 5-tuple.
        val dialDnsHost = if (isCloudflareResolverIp(dstHost)) "8.8.8.8" else dstHost
        val dnsResp = resolveDnsQueryOverAmir(dialDnsHost, dnsQuery) ?: return
        sendSocksUdpReply(udpSocket, clientAddr, socksHeader, dnsResp)
    }

    private fun resolveDnsQueryOverAmir(dialDnsHost: String, dnsQuery: ByteArray): ByteArray? {
        if (dnsQuery.size < 12) return null
        val qtype = extractDnsQuestionQtype(dnsQuery)
        if ((qtype == DNS_QTYPE_AAAA && !settings.enableIpv6 && !settings.preferIpv6) ||
            qtype == DNS_QTYPE_HTTPS ||
            qtype == DNS_QTYPE_SVCB
        ) {
            buildSyntheticNodataResponse(dnsQuery)?.let { return it }
        }
        val cacheKey = extractDnsQuestionCacheKey(dnsQuery)
        if (cacheKey != null) {
            val cached = dnsCache[cacheKey]
            val now = System.currentTimeMillis()
            if (cached != null && cached.payload.size >= 12) {
                if (now < cached.expiresAtMs) {
                    val replyDns = cached.payload.copyOf()
                    replyDns[0] = dnsQuery[0]
                    replyDns[1] = dnsQuery[1]
                    return replyDns
                } else if (now < cached.staleUntilMs) {
                    // Stale-while-revalidate: serve cached answer immediately and refresh silently
                    if (dnsInFlight[cacheKey] == null) {
                        runCatching {
                            proxyExecutor.execute {
                                queryAndCacheDns(cacheKey, dialDnsHost, dnsQuery)
                            }
                        }
                    }
                    val replyDns = cached.payload.copyOf()
                    replyDns[0] = dnsQuery[0]
                    replyDns[1] = dnsQuery[1]
                    return replyDns
                }
            }
            return queryAndCacheDns(cacheKey, dialDnsHost, dnsQuery)
        }
        return queryDnsOverAmirWithFallback(dialDnsHost, dnsQuery)
    }

    private fun queryAndCacheDns(cacheKey: String, dialDnsHost: String, dnsQuery: ByteArray): ByteArray? {
        // Singleflight coalesce duplicate concurrent DNS queries for the same host+qtype
        val newFuture = CompletableFuture<ByteArray?>()
        val existingFuture = dnsInFlight.putIfAbsent(cacheKey, newFuture)
        if (existingFuture != null) {
            val shared = runCatching {
                existingFuture.get(DNS_SINGLEFLIGHT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }.getOrNull() ?: return null
            if (shared.size < 12) return null
            val patched = shared.copyOf()
            patched[0] = dnsQuery[0]
            patched[1] = dnsQuery[1]
            return patched
        }

        return try {
            val resp = queryDnsOverAmirWithFallback(dialDnsHost, dnsQuery)
            if (resp != null && resp.size >= 12) {
                val rcode = resp[3].toInt() and 0x0F
                if (rcode == 0 || rcode == 3) {
                    if (dnsCache.size >= MAX_DNS_CACHE_ENTRIES) {
                        dnsCache.clear()
                    }
                    val now = System.currentTimeMillis()
                    val ttlMs = if (rcode == 3) DNS_NEGATIVE_TTL_MS else extractDnsMinTtlMs(resp)
                    val staleUntil = if (rcode == 0) now + ttlMs + DNS_STALE_GRACE_MS else now + ttlMs
                    dnsCache[cacheKey] = CachedDnsResponse(
                        payload = resp.copyOf(),
                        expiresAtMs = now + ttlMs,
                        staleUntilMs = staleUntil
                    )
                }
            }
            newFuture.complete(resp)
            resp
        } catch (_: Throwable) {
            newFuture.complete(null)
            null
        } finally {
            dnsInFlight.remove(cacheKey, newFuture)
        }
    }

    private fun queryDnsOverAmirWithFallback(primaryDnsHost: String, dnsQuery: ByteArray): ByteArray? {
        val primaryResp = queryDnsOverAmirTcp(primaryDnsHost, 53, dnsQuery)
        if (primaryResp != null) return primaryResp
        val fallbackHost = if (primaryDnsHost == "8.8.8.8") "8.8.4.4" else "8.8.8.8"
        return queryDnsOverAmirTcp(fallbackHost, 53, dnsQuery)
    }

    private fun queryDnsOverAmirTcp(dnsHost: String, dnsPort: Int, dnsQuery: ByteArray): ByteArray? {
        var wsConn: DirectWsConnection? = null
        try {
            val obtained = obtainWebSocket(forDns = true) ?: return null
            wsConn = obtained.conn
            val (initPacket, initKey) = buildInitPacket(uuidBytes, dnsHost, dnsPort)
            var activeKey = initKey
            var ack: ByteArray? = null
            var handshakeFailed = false
            try {
                wsConn.socket.soTimeout = if (obtained.fromPool) POOLED_ACK_TIMEOUT_MS else DNS_QUERY_TIMEOUT_MS
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
                if (!obtained.fromPool) {
                    markEndpointThrottled(obtained.endpointIp)
                }
                activeSockets.remove(wsConn.socket)
                runCatching { wsConn.socket.close() }
                val forceDirectFirst = !obtained.fromPool && obtained.usedCleanIp
                val retryDial = dialBestWebSocket(preferDirectHostFirst = forceDirectFirst) ?: return null
                wsConn = retryDial.conn
                wsConn.socket.soTimeout = DNS_QUERY_TIMEOUT_MS
                val (retryInit, retryKey) = buildInitPacket(uuidBytes, dnsHost, dnsPort)
                activeKey = retryKey
                wsConn.sendBinaryFrame(retryInit, 0, retryInit.size)
                wsConn.out.flush()
                ack = wsConn.readMessageBytes()
                if (ack == null || ack.size < 16) {
                    markEndpointThrottled(retryDial.endpointIp)
                }
            }
            if (ack == null || ack.size < 16) return null
            val decAck = xorBytes(ack, activeKey)
            val expAck = hmacSha256(activeKey, "OK".toByteArray(StandardCharsets.US_ASCII)).copyOfRange(0, 16)
            if (!MessageDigest.isEqual(decAck.copyOfRange(0, 16), expAck)) {
                preferredEndpointIp.set(null)
                return null
            }

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
                uplinkBytesCounter.addAndGet(read.toLong())

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

                wsConn.writeLock.lock()
                try {
                    wsConn.out.write(frameBuf, headerStart, headerLen + read)
                    // Flush immediately for interactive / end-of-burst frames without delaying small writes
                    if (read < MAX_CHUNK || localIn.available() == 0) {
                        wsConn.out.flush()
                    }
                } finally {
                    wsConn.writeLock.unlock()
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
                wsConn.closed.set(true)
                runCatching { wsConn.socket.close() }
            } else {
                wsConn.uplinkHalfClosed.set(true)
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
    ): RelayDownResult {
        val chunkBuf = ByteArray(IO_BUFFER_SIZE)
        val ctrlBuf = ByteArray(128)
        val maskBuf = ByteArray(4)
        var msgKeyOffset = 0
        var awaitingPong = false
        var totalReceivedBytes = 0L
        var hadError = false
        try {
            while (running.get() && !wsConn.socket.isClosed) {
                val b0: Int
                try {
                    b0 = wsConn.input.read()
                } catch (_: java.net.SocketTimeoutException) {
                    if (wsConn.uplinkHalfClosed.get() || awaitingPong) {
                        hadError = totalReceivedBytes == 0L
                        break
                    }
                    awaitingPong = true
                    wsConn.socket.soTimeout = WS_PING_TIMEOUT_MS
                    var locked = false
                    try {
                        locked = wsConn.writeLock.tryLock(50, TimeUnit.MILLISECONDS)
                        if (locked) {
                            wsConn.sendControlPing()
                            wsConn.out.flush()
                        } else {
                            // Uplink is actively writing right now; keepalive ping skipped
                            awaitingPong = false
                            wsConn.socket.soTimeout = WS_IDLE_KEEPALIVE_MS
                        }
                    } finally {
                        if (locked) runCatching { wsConn.writeLock.unlock() }
                    }
                    continue
                }
                if (b0 < 0) break
                if (awaitingPong) {
                    awaitingPong = false
                    if (!wsConn.uplinkHalfClosed.get()) {
                        wsConn.socket.soTimeout = WS_IDLE_KEEPALIVE_MS
                    }
                }
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
                            if (b < 0) return RelayDownResult(totalReceivedBytes, hadError)
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
                            wsConn.writeLock.lock()
                            try {
                                wsConn.sendControlPong(ctrlBuf, ctrlLen)
                                wsConn.out.flush()
                            } finally {
                                wsConn.writeLock.unlock()
                            }
                        }
                        else -> Unit
                    }
                    continue
                }

                if (opcode != 0) {
                    msgKeyOffset = 0
                }

                // Stream incoming frame bytes immediately as they arrive without waiting for full 64KB frame boundary
                var remaining = payloadLen
                var frameOffset = 0
                while (remaining > 0L) {
                    val toRead = minOf(remaining, chunkBuf.size.toLong()).toInt()
                    val readNow = wsConn.input.read(chunkBuf, 0, toRead)
                    if (readNow < 0) throw EOFException("Unexpected EOF")
                    if (readNow == 0) continue
                    var k = msgKeyOffset and 15
                    if (masked) {
                        for (i in 0 until readNow) {
                            val unmasked = chunkBuf[i].toInt() xor maskBuf[(frameOffset + i) and 3].toInt()
                            chunkBuf[i] = (unmasked xor key[k].toInt()).toByte()
                            k = (k + 1) and 15
                        }
                    } else {
                        for (i in 0 until readNow) {
                            chunkBuf[i] = (chunkBuf[i].toInt() xor key[k].toInt()).toByte()
                            k = (k + 1) and 15
                        }
                    }
                    msgKeyOffset += readNow
                    frameOffset += readNow
                    totalReceivedBytes += readNow.toLong()
                    downlinkBytesCounter.addAndGet(readNow.toLong())
                    localOut.write(chunkBuf, 0, readNow)
                    remaining -= readNow
                    if (remaining == 0L || wsConn.input.available() == 0) {
                        localOut.flush()
                    }
                }
                if (fin && payloadLen == 0L) {
                    localOut.flush()
                }
            }
            localOut.flush()
        } catch (_: Exception) {
            hadError = true
        } finally {
            runCatching { localOut.flush() }
        }
        return RelayDownResult(totalReceivedBytes, hadError)
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
                if (candidate == cleanIp) cleanIpFailureCount.set(0)
                break
            } else {
                activeSockets.remove(sock)
                runCatching { sock.close() }
                if (candidate == cleanIp && targetHost != cleanIp) {
                    recordCleanIpFailure("DoH TCP connect failed")
                }
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

    private fun markEndpointThrottled(ip: String) {
        if (ip.isBlank()) return
        preferredEndpointIp.compareAndSet(ip, null)
        throttledEndpoints[ip] = System.currentTimeMillis() + ENDPOINT_SOFT_COOLDOWN_MS
    }

    private fun dialBestWebSocket(preferDirectHostFirst: Boolean = false): DialAttemptResult? {
        val cleanIpAllowed = cleanIp.isNotBlank() && System.currentTimeMillis() >= cleanIpCooldownUntilMs.get()
        val candidates = buildOrderedEndpointCandidates(preferDirectHostFirst, cleanIpAllowed)
        if (candidates.isEmpty()) {
            emitThrottledError("Amir WebSocket dial failed: no resolved endpoints for $workerHost")
            return null
        }
        if (candidates.size == 1) {
            val single = candidates.first()
            val conn = runCatching { dialSingleAddressWebSocket(single.addr, single.connectTimeoutMs) }
                .onFailure { err ->
                    preferredEndpointIp.compareAndSet(single.ipString, null)
                    emitThrottledError("Amir WebSocket dial failed: ${err.message}")
                }
                .getOrNull() ?: return null
            preferredEndpointIp.set(single.ipString)
            throttledEndpoints.remove(single.ipString)
            hasSuccessfulDial.set(true)
            warmPoolBackoffUntilMs.set(0L)
            if (single.isCleanIp) cleanIpFailureCount.set(0)
            return DialAttemptResult(conn, usedCleanIp = single.isCleanIp, endpointIp = single.ipString)
        }

        // Smart staggered Happy Eyeballs: give primary candidate a head start (wake immediately on error),
        // launch backup races only if slow, and immediately abort losing attempts once winner succeeds
        // without touching the winner's underlying TCP/TLS socket.
        val winner = CompletableFuture<DialAttemptResult?>()
        val winnerClaimed = AtomicBoolean(false)
        val primaryFinished = CountDownLatch(1)
        val remainingCount = AtomicInteger(candidates.size)
        val attempts = Array(candidates.size) { DialAttemptHandle() }

        fun closeLosingAttempts(winnerIndex: Int) {
            for (idx in attempts.indices) {
                if (idx != winnerIndex) {
                    attempts[idx].cancel()
                }
            }
        }

        fun launchCandidate(index: Int, candidate: EndpointCandidate, isPrimary: Boolean) {
            val attempt = attempts[index]
            if (winnerClaimed.get() || !running.get()) {
                attempt.cancel()
                if (isPrimary) primaryFinished.countDown()
                if (remainingCount.decrementAndGet() == 0) {
                    winner.complete(null)
                }
                return
            }
            runCatching {
                dialRaceExecutor.execute {
                    if (winnerClaimed.get() || !running.get()) {
                        attempt.cancel()
                        if (isPrimary) primaryFinished.countDown()
                        if (remainingCount.decrementAndGet() == 0) winner.complete(null)
                        return@execute
                    }
                    val conn = runCatching {
                        dialSingleAddressWebSocket(candidate.addr, candidate.connectTimeoutMs, attempt)
                    }.getOrNull()
                    if (conn != null && !attempt.isCancelled() && winnerClaimed.compareAndSet(false, true)) {
                        if (isPrimary) primaryFinished.countDown()
                        preferredEndpointIp.set(candidate.ipString)
                        throttledEndpoints.remove(candidate.ipString)
                        hasSuccessfulDial.set(true)
                        warmPoolBackoffUntilMs.set(0L)
                        if (candidate.isCleanIp) {
                            cleanIpFailureCount.set(0)
                        }
                        closeLosingAttempts(index)
                        winner.complete(DialAttemptResult(conn, usedCleanIp = candidate.isCleanIp, endpointIp = candidate.ipString))
                    } else {
                        if (isPrimary) {
                            primaryFinished.countDown()
                            if (candidate.isCleanIp && conn == null && !winnerClaimed.get()) {
                                recordCleanIpFailure("dial failed")
                            }
                        }
                        if (conn != null) {
                            activeSockets.remove(conn.socket)
                            runCatching { conn.socket.close() }
                        }
                        if (remainingCount.decrementAndGet() == 0) {
                            winner.complete(null)
                        }
                    }
                }
            }.onFailure {
                attempt.cancel()
                if (isPrimary) primaryFinished.countDown()
                if (remainingCount.decrementAndGet() == 0) {
                    winner.complete(null)
                }
            }
        }

        launchCandidate(0, candidates[0], isPrimary = true)
        val hasPreferOrClean = !preferDirectHostFirst &&
            (candidates[0].ipString == preferredEndpointIp.get() || candidates[0].isCleanIp)
        val staggerMs = if (hasPreferOrClean) PRIMARY_ENDPOINT_STAGGER_MS else HAPPY_EYEBALLS_STAGGER_MS
        // Wait at most staggerMs OR wake instantly if primary fails early!
        runCatching { primaryFinished.await(staggerMs, TimeUnit.MILLISECONDS) }
        winner.getNow(null)?.let { return it }

        for (i in 1 until candidates.size) {
            if (winnerClaimed.get()) break
            launchCandidate(i, candidates[i], isPrimary = false)
        }

        val finalWin = runCatching {
            winner.get(DIRECT_CONNECT_TIMEOUT_MS.toLong() + TLS_WS_UPGRADE_TIMEOUT_MS.toLong() + 1_000L, TimeUnit.MILLISECONDS)
        }.getOrNull()
        if (finalWin == null) {
            closeLosingAttempts(-1)
            val currentPref = preferredEndpointIp.get()
            if (currentPref != null && candidates.any { it.ipString == currentPref }) {
                preferredEndpointIp.compareAndSet(currentPref, null)
            }
            emitThrottledError("Amir WebSocket dial failed across all endpoints")
        }
        return finalWin
    }

    private fun buildOrderedEndpointCandidates(
        preferDirectHostFirst: Boolean,
        cleanIpAllowed: Boolean
    ): List<EndpointCandidate> {
        val raw = mutableListOf<EndpointCandidate>()
        val seenIps = HashSet<String>(8)
        val isLoopback = isLoopbackTarget(workerHost, customHostOverrides, resolvedHosts)
        val hasStaticBackupIps = cleanIpAllowed ||
            (isTls && !isLoopback && !isIpLiteral(workerHost) && (cleanIp.isNotBlank() || isCloudflareHostname(workerHost)))

        fun addForTarget(target: String, isCleanIp: Boolean, timeoutMs: Int, maxCount: Int, dnsWaitMs: Long = WORKER_DNS_LOOKUP_TIMEOUT_MS) {
            val addrs = runCatching { resolveConnectAddresses(target, dnsWaitMs) }.getOrDefault(emptyList()).take(maxCount)
            for (addr in addrs) {
                val ipStr = addr.hostAddress ?: continue
                if (seenIps.add(ipStr)) {
                    raw.add(EndpointCandidate(addr, ipStr, isCleanIp, timeoutMs))
                }
            }
        }

        val hostDnsWaitMs = if (hasStaticBackupIps && !preferDirectHostFirst) WORKER_DNS_FAST_TIMEOUT_MS else WORKER_DNS_LOOKUP_TIMEOUT_MS
        if (preferDirectHostFirst) {
            addForTarget(workerHost, isCleanIp = false, timeoutMs = DIRECT_CONNECT_TIMEOUT_MS, maxCount = 2, dnsWaitMs = WORKER_DNS_LOOKUP_TIMEOUT_MS)
            if (cleanIpAllowed) {
                addForTarget(cleanIp, isCleanIp = true, timeoutMs = CLEAN_IP_CONNECT_TIMEOUT_MS, maxCount = 1)
            }
        } else {
            if (cleanIpAllowed) {
                addForTarget(cleanIp, isCleanIp = true, timeoutMs = CLEAN_IP_CONNECT_TIMEOUT_MS, maxCount = 1)
            }
            addForTarget(workerHost, isCleanIp = false, timeoutMs = DIRECT_CONNECT_TIMEOUT_MS, maxCount = 2, dnsWaitMs = hostDnsWaitMs)
        }

        val noDirectResolved = raw.none { !it.isCleanIp }
        val allowCfBackups = isTls && !isLoopback && !isIpLiteral(workerHost) &&
            (cleanIp.isNotBlank() || isCloudflareHostname(workerHost) || noDirectResolved)
        if (allowCfBackups && raw.size < 6) {
            for (backupIp in CLOUDFLARE_BACKUP_IPS) {
                if (raw.size >= 6) break
                if (seenIps.add(backupIp)) {
                    val addr = runCatching { InetAddress.getByName(backupIp) }.getOrNull() ?: continue
                    raw.add(EndpointCandidate(addr, backupIp, isCleanIp = false, connectTimeoutMs = CLEAN_IP_CONNECT_TIMEOUT_MS))
                }
            }
        }

        val now = System.currentTimeMillis()
        val unthrottled = raw.filter { (throttledEndpoints[it.ipString] ?: 0L) <= now }.toMutableList()
        val ordered = if (unthrottled.isNotEmpty()) unthrottled else raw

        val lastBest = preferredEndpointIp.get()
        if (!preferDirectHostFirst && lastBest != null && ordered.size > 1) {
            val bestIdx = ordered.indexOfFirst { it.ipString == lastBest }
            if (bestIdx > 0) {
                val best = ordered.removeAt(bestIdx)
                ordered.add(0, best)
            }
        }
        return ordered.take(4)
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

    private fun resolveConnectAddresses(
        connectTarget: String,
        dnsWaitTimeoutMs: Long = WORKER_DNS_LOOKUP_TIMEOUT_MS
    ): List<InetAddress> {
        val cleanTarget = connectTarget.trim().removePrefix("[").removeSuffix("]")
        if (cleanTarget.isEmpty()) return emptyList()
        if (isIpLiteral(cleanTarget)) {
            return listOf(InetAddress.getByName(cleanTarget))
        }
        val explicitMapped = customHostOverrides[cleanTarget]
            ?: customHostOverrides["domain:$cleanTarget"]
            ?: customHostOverrides["full:$cleanTarget"]
        if (!explicitMapped.isNullOrEmpty()) {
            val mappedAddrs = explicitMapped.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
            if (mappedAddrs.isNotEmpty()) return mappedAddrs
        }
        val targetIsLoopback = isLoopbackTarget(cleanTarget, customHostOverrides, resolvedHosts)
        val preResolved = resolvedHosts[cleanTarget]
        if (!preResolved.isNullOrEmpty()) {
            val mappedAddrs = preResolved.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
                .filter { targetIsLoopback || isValidPublicServerIp(it) }
            if (mappedAddrs.isNotEmpty()) return mappedAddrs
        }
        return resolveDomainPublicAddressesCached(
            host = cleanTarget,
            allowLoopback = isLoopbackHost(cleanTarget),
            waitTimeoutMs = dnsWaitTimeoutMs
        )
    }

    private fun resolveDomainPublicAddressesCached(
        host: String,
        allowLoopback: Boolean,
        waitTimeoutMs: Long = WORKER_DNS_LOOKUP_TIMEOUT_MS
    ): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = workerDnsCache[host]
        if (cached != null && now < cached.expiresAtMs) {
            return cached.addrs
        }
        val future = CompletableFuture.supplyAsync({
            val rawAddrs = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
            val valid = rawAddrs.filter { allowLoopback || isValidPublicServerIp(it) }
            val ordered = if (!settings.preferIpv6) {
                val v4 = valid.filterIsInstance<Inet4Address>()
                if (v4.isNotEmpty()) v4 else valid
            } else {
                val v6 = valid.filterIsInstance<Inet6Address>()
                val v4 = valid.filterIsInstance<Inet4Address>()
                v6 + v4
            }
            val ttl = if (ordered.isNotEmpty()) WORKER_DNS_CACHE_HIT_TTL_MS else WORKER_DNS_CACHE_MISS_TTL_MS
            if (workerDnsCache.size >= 128) workerDnsCache.clear()
            workerDnsCache[host] = WorkerDnsCacheEntry(ordered, System.currentTimeMillis() + ttl)
            ordered
        }, workerDnsExecutor)

        return try {
            future.get(waitTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            // Timed out waiting on slow ISP DNS; leave background lookup warming the cache
            emptyList()
        }
    }

    private fun resolveConnectAddress(connectTarget: String): InetAddress {
        return resolveConnectAddresses(connectTarget).firstOrNull()
            ?: throw IOException("No valid endpoint address for $connectTarget")
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

    private fun dialSingleAddressWebSocket(
        targetAddr: InetAddress,
        connectTimeoutMs: Int,
        attempt: DialAttemptHandle? = null
    ): DirectWsConnection {
        val rawSocket = Socket()
        var socketToTrack: Socket = rawSocket
        activeSockets.add(rawSocket)
        attempt?.setSocket(rawSocket)
        try {
            if (attempt?.isCancelled() == true) {
                throw IOException("Dial cancelled")
            }
            rawSocket.tcpNoDelay = true
            rawSocket.keepAlive = true
            // Keep kernel TCP window autotuning unlocked (explicit SO_RCVBUF/SO_SNDBUF caps throughput on high-RTT links)
            protectSocket(rawSocket)
            rawSocket.connect(InetSocketAddress(targetAddr, workerPort), connectTimeoutMs)
            rawSocket.soTimeout = TLS_WS_UPGRADE_TIMEOUT_MS

            val finalSocket: Socket = if (isTls) {
                val activeTlsFactory = testTlsSocketFactory ?: tlsSocketFactory
                val sslSocket = activeTlsFactory.createSocket(rawSocket, workerHost, workerPort, true) as SSLSocket
                sslSocket.tcpNoDelay = true
                sslSocket.soTimeout = TLS_WS_UPGRADE_TIMEOUT_MS
                attempt?.setSocket(sslSocket)
                if (attempt?.isCancelled() == true) {
                    throw IOException("Dial cancelled")
                }
                val sslParams = sslSocket.sslParameters
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isIpLiteral(workerHost)) {
                    sslParams.serverNames = listOf(SNIHostName(workerHost))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sslParams.applicationProtocols = arrayOf("http/1.1")
                }
                if (testHostnameVerifier == null) {
                    sslParams.endpointIdentificationAlgorithm = "HTTPS"
                }
                sslSocket.sslParameters = sslParams
                sslSocket.startHandshake()
                val verifier = testHostnameVerifier ?: HttpsURLConnection.getDefaultHostnameVerifier()
                val verified = verifier.verify(workerHost, sslSocket.session)
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
        val writeLock = ReentrantLock()
        val closed = AtomicBoolean(false)
        val uplinkHalfClosed = AtomicBoolean(false)

        fun sendBinaryFrame(data: ByteArray, offset: Int, length: Int, mask: ByteArray? = null) {
            val m = mask ?: ByteArray(4).also { ThreadLocalRandom.current().nextBytes(it) }
            writeLock.lock()
            try {
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
            } finally {
                writeLock.unlock()
            }
        }

        fun sendControlPing() {
            val m = ByteArray(4).also { ThreadLocalRandom.current().nextBytes(it) }
            out.write(0x89)
            out.write(0x80)
            out.write(m)
        }

        fun sendControlPong(payload: ByteArray, length: Int) {
            val m = ByteArray(4).also { ThreadLocalRandom.current().nextBytes(it) }
            val clamped = length.coerceIn(0, 125)
            out.write(0x8A)
            out.write(0x80 or clamped)
            out.write(m)
            for (i in 0 until clamped) {
                out.write((payload[i].toInt() xor m[i and 3].toInt()) and 0xFF)
            }
        }

        fun sendCloseFrame() {
            val m = ByteArray(4).also { ThreadLocalRandom.current().nextBytes(it) }
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
                    writeLock.lock()
                    try {
                        sendControlPong(part, part.size)
                        out.flush()
                    } finally {
                        writeLock.unlock()
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
            wsConn.socket.soTimeout = HANDSHAKE_TIMEOUT_MS
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
        } catch (e: Exception) {
            emitThrottledError("Amir delay test error: ${e.message}")
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
        private const val INITIAL_WARM_POOL_SIZE = 3
        private const val MAX_WARM_POOL_SIZE = 8
        private const val WARM_POOL_MAX_AGE_MS = 16_000L
        private const val WARM_POOL_BACKOFF_MS = 4_000L
        private const val WORKER_DNS_CACHE_HIT_TTL_MS = 60_000L
        private const val WORKER_DNS_CACHE_MISS_TTL_MS = 5_000L
        private const val WORKER_DNS_LOOKUP_TIMEOUT_MS = 2_000L
        private const val WORKER_DNS_FAST_TIMEOUT_MS = 120L
        private const val DNS_CACHE_TTL_MS = 300_000L
        private const val DNS_MIN_TTL_MS = 120_000L
        private const val DNS_MAX_TTL_MS = 600_000L
        private const val DNS_STALE_GRACE_MS = 600_000L
        private const val DNS_NEGATIVE_TTL_MS = 15_000L
        private const val DNS_QUERY_TIMEOUT_MS = 3_000
        private const val MAX_DNS_CACHE_ENTRIES = 1024
        private const val DNS_SINGLEFLIGHT_TIMEOUT_MS = 6_000L
        private const val UDP_ASSOCIATE_IDLE_TIMEOUT_MS = 20_000
        private const val POOLED_ACK_TIMEOUT_MS = 1_500
        private const val HANDSHAKE_TIMEOUT_MS = 6_500
        private const val SLOW_ACK_THRESHOLD_MS = 1_800L
        private const val ENDPOINT_SOFT_COOLDOWN_MS = 45_000L
        private const val TLS_WS_UPGRADE_TIMEOUT_MS = 5_500
        private const val HALF_CLOSE_DRAIN_TIMEOUT_MS = 10_000
        private const val WS_IDLE_KEEPALIVE_MS = 25_000
        private const val WS_PING_TIMEOUT_MS = 10_000
        private const val PRIMARY_ENDPOINT_STAGGER_MS = 220L
        private const val HAPPY_EYEBALLS_STAGGER_MS = 120L
        private const val CLEAN_IP_CONNECT_TIMEOUT_MS = 3_200
        private const val DIRECT_CONNECT_TIMEOUT_MS = 3_500
        private const val CLEAN_IP_COOLDOWN_MS = 60_000L
        private const val DIRECT_DOH_CONNECT_TIMEOUT_MS = 1_500
        private const val DIRECT_DOH_FIRST_BYTE_TIMEOUT_MS = 2_500
        private const val DIRECT_DOH_COOLDOWN_MS = 60_000L
        private const val DNS_QTYPE_AAAA = 28
        private const val DNS_QTYPE_SVCB = 64
        private const val DNS_QTYPE_HTTPS = 65
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private val CLOUDFLARE_BACKUP_IPS = listOf(
            "104.16.132.229",
            "162.159.135.42",
            "104.18.2.161",
            "172.64.155.14",
            "188.114.97.3",
            "172.67.70.244"
        )
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

        private fun parseHostOverrides(raw: String): Map<String, List<String>> {
            if (raw.isBlank()) return emptyMap()
            val out = linkedMapOf<String, MutableList<String>>()
            raw.split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { entry ->
                    val colon = entry.lastIndexOf(':')
                    if (colon <= 0 || colon >= entry.length - 1) return@forEach
                    val host = entry.substring(0, colon).trim()
                    val ip = entry.substring(colon + 1).trim().removePrefix("[").removeSuffix("]")
                    if (host.isNotEmpty() && isIpLiteral(ip)) {
                        out.getOrPut(host) { mutableListOf() }.add(ip)
                    }
                }
            return out
        }

        private fun isLoopbackTarget(
            workerHost: String,
            customHostOverrides: Map<String, List<String>>,
            resolvedHosts: Map<String, List<String>>
        ): Boolean {
            if (isLoopbackHost(workerHost)) return true
            val mapped = customHostOverrides[workerHost]
                ?: customHostOverrides["domain:$workerHost"]
                ?: customHostOverrides["full:$workerHost"]
                ?: resolvedHosts[workerHost]
            return mapped?.any { isLoopbackHost(it) } == true
        }

        private fun writeSimpleHttpError(out: OutputStream, code: Int, reason: String) {
            runCatching {
                out.write(
                    (
                        "HTTP/1.1 $code $reason\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(StandardCharsets.US_ASCII)
                )
                out.flush()
            }
        }

        internal fun parseHostPort(authority: String, defaultPort: Int): Pair<String, Int> {
            val trimmed = authority.trim().substringAfterLast('@')
            if (trimmed.isEmpty()) return "" to -1
            if (trimmed.startsWith("[")) {
                val closeIdx = trimmed.indexOf(']')
                if (closeIdx <= 1) return "" to -1
                val host = trimmed.substring(1, closeIdx)
                val rest = trimmed.substring(closeIdx + 1)
                val port = if (rest.startsWith(":")) {
                    rest.substring(1).toIntOrNull() ?: return "" to -1
                } else {
                    defaultPort
                }
                return host to port
            }
            val firstColon = trimmed.indexOf(':')
            val lastColon = trimmed.lastIndexOf(':')
            return if (lastColon > 0 && firstColon == lastColon) {
                val host = trimmed.substring(0, lastColon)
                val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: return "" to -1
                host to port
            } else {
                trimmed to defaultPort
            }
        }

        internal fun parseHttpForwardTarget(rawTarget: String, hostHeader: String): Triple<String, Int, String>? {
            val target = rawTarget.trim()
            if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
                val isHttps = target.startsWith("https://", ignoreCase = true)
                val afterScheme = target.substring(if (isHttps) 8 else 7)
                val slashIdx = afterScheme.indexOf('/')
                val qIdx = afterScheme.indexOf('?')
                val sepIdx = when {
                    slashIdx >= 0 && qIdx >= 0 -> minOf(slashIdx, qIdx)
                    slashIdx >= 0 -> slashIdx
                    qIdx >= 0 -> qIdx
                    else -> -1
                }
                val authority = if (sepIdx >= 0) afterScheme.substring(0, sepIdx) else afterScheme
                val originPath = if (sepIdx >= 0) {
                    val tail = afterScheme.substring(sepIdx)
                    if (tail.startsWith("/")) tail else "/$tail"
                } else {
                    "/"
                }
                val (host, port) = parseHostPort(authority, defaultPort = if (isHttps) 443 else 80)
                if (host.isBlank() || port !in 1..65535) return null
                return Triple(host, port, originPath)
            }
            if (hostHeader.isBlank()) return null
            val (host, port) = parseHostPort(hostHeader, defaultPort = 80)
            if (host.isBlank() || port !in 1..65535) return null
            val originPath = target.ifEmpty { "/" }
            return Triple(host, port, originPath)
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

        internal fun isValidPublicServerIp(addr: InetAddress): Boolean {
            if (addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isMulticastAddress ||
                addr.isSiteLocalAddress
            ) {
                return false
            }
            if (addr is Inet4Address) {
                val b = addr.address
                val b0 = b[0].toInt() and 0xFF
                val b1 = b[1].toInt() and 0xFF
                val b2 = b[2].toInt() and 0xFF
                return when {
                    b0 == 0 -> false // 0.0.0.0/8
                    b0 == 10 -> false // 10.0.0.0/8 (includes ISP DNS sinkholes like 10.10.34.x)
                    b0 == 100 && b1 in 64..127 -> false // 100.64.0.0/10 CGNAT
                    b0 == 127 -> false // 127.0.0.0/8
                    b0 == 169 && b1 == 254 -> false // 169.254.0.0/16
                    b0 == 172 && b1 in 16..31 -> false // 172.16.0.0/12
                    b0 == 192 && b1 == 0 && b2 == 0 -> false // 192.0.0.0/24
                    b0 == 192 && b1 == 0 && b2 == 2 -> false // 192.0.2.0/24 TEST-NET-1
                    b0 == 192 && b1 == 168 -> false // 192.168.0.0/16
                    b0 == 198 && (b1 == 18 || b1 == 19) -> false // 198.18.0.0/15 FakeDNS/Benchmark
                    b0 == 198 && b1 == 51 && b2 == 100 -> false // 198.51.100.0/24 TEST-NET-2
                    b0 == 203 && b1 == 0 && b2 == 113 -> false // 203.0.113.0/24 TEST-NET-3
                    b0 >= 224 -> false // Multicast & Reserved
                    else -> true
                }
            }
            if (addr is Inet6Address) {
                val b = addr.address
                val b0 = b[0].toInt() and 0xFF
                val b1 = b[1].toInt() and 0xFF
                val b2 = b[2].toInt() and 0xFF
                val b3 = b[3].toInt() and 0xFF
                if ((b0 and 0xFE) == 0xFC) return false // fc00::/7 ULA
                if (b0 == 0x20 && b1 == 0x01 && b2 == 0x0D && b3 == 0xB8) return false // 2001:db8::/32
                return true
            }
            return true
        }

        private fun isCloudflareHostname(host: String): Boolean {
            val clean = host.trim().lowercase()
            return clean.endsWith(".workers.dev") ||
                clean.endsWith(".pages.dev") ||
                clean.endsWith(".trycloudflare.com")
        }

        private fun extractDnsQuestionQtype(dns: ByteArray): Int {
            if (dns.size < 17) return -1
            val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
            if (qdCount != 1) return -1
            var idx = 12
            var jumps = 0
            while (idx < dns.size && jumps++ < 64) {
                val len = dns[idx].toInt() and 0xFF
                if (len == 0) {
                    idx++
                    break
                }
                if ((len and 0xC0) != 0 || idx + 1 + len > dns.size) return -1
                idx += 1 + len
            }
            if (idx + 4 > dns.size) return -1
            return ((dns[idx].toInt() and 0xFF) shl 8) or (dns[idx + 1].toInt() and 0xFF)
        }

        internal fun buildSyntheticNodataResponse(query: ByteArray): ByteArray? {
            if (query.size < 17) return null
            val qdCount = ((query[4].toInt() and 0xFF) shl 8) or (query[5].toInt() and 0xFF)
            if (qdCount != 1) return null
            var idx = 12
            var jumps = 0
            while (idx < query.size && jumps++ < 64) {
                val len = query[idx].toInt() and 0xFF
                if (len == 0) {
                    idx++
                    break
                }
                if ((len and 0xC0) != 0 || idx + 1 + len > query.size) return null
                idx += 1 + len
            }
            val questionEnd = idx + 4
            if (questionEnd > query.size) return null
            val resp = query.copyOfRange(0, questionEnd)
            val rd = query[2].toInt() and 0x01
            resp[2] = (0x80 or rd).toByte()
            resp[3] = 0x80.toByte() // RA=1, RCODE=0 (NOERROR/NODATA)
            resp[4] = 0x00
            resp[5] = 0x01
            resp[6] = 0x00
            resp[7] = 0x00
            resp[8] = 0x00
            resp[9] = 0x00
            resp[10] = 0x00
            resp[11] = 0x00
            return resp
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

        private fun extractDnsMinTtlMs(dns: ByteArray): Long {
            if (dns.size < 12) return DNS_CACHE_TTL_MS
            val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
            val anCount = ((dns[6].toInt() and 0xFF) shl 8) or (dns[7].toInt() and 0xFF)
            if (anCount <= 0) return DNS_CACHE_TTL_MS
            var idx = 12
            fun skipName(): Boolean {
                var guard = 0
                while (idx < dns.size && guard++ < 64) {
                    val len = dns[idx].toInt() and 0xFF
                    if (len == 0) {
                        idx++
                        return true
                    }
                    if ((len and 0xC0) == 0xC0) {
                        if (idx + 2 > dns.size) return false
                        idx += 2
                        return true
                    }
                    idx += 1 + len
                }
                return false
            }
            repeat(qdCount) {
                if (!skipName() || idx + 4 > dns.size) return DNS_CACHE_TTL_MS
                idx += 4
            }
            var minTtlSec = Long.MAX_VALUE
            repeat(anCount) {
                if (!skipName() || idx + 10 > dns.size) return@repeat
                val ttl = ((dns[idx + 4].toLong() and 0xFFL) shl 24) or
                    ((dns[idx + 5].toLong() and 0xFFL) shl 16) or
                    ((dns[idx + 6].toLong() and 0xFFL) shl 8) or
                    (dns[idx + 7].toLong() and 0xFFL)
                val rdLen = ((dns[idx + 8].toInt() and 0xFF) shl 8) or (dns[idx + 9].toInt() and 0xFF)
                idx += 10 + rdLen
                if (ttl in 1L..86400L && ttl < minTtlSec) {
                    minTtlSec = ttl
                }
            }
            if (minTtlSec == Long.MAX_VALUE) return DNS_CACHE_TTL_MS
            return (minTtlSec * 1000L).coerceIn(DNS_MIN_TTL_MS, DNS_MAX_TTL_MS)
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

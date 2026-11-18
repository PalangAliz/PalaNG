package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.XrayNgApplication
import com.example.core.AmirLocalProxy
import com.example.core.AssetHelper
import com.example.core.XrayConfigBuilder
import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import libv2ray.ProcessFinder
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

class XrayVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var amirLocalProxy: AmirLocalProxy? = null
    private var trafficJob: Job? = null
    private var currentServerRemarks: String = "PalaNG"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AssetHelper.ensureCoreAssets(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_MEASURE_DELAY -> {
                val url = intent.getStringExtra(EXTRA_TEST_URL) ?: "https://www.gstatic.com/generate_204"
                measureActiveDelay(url)
                return START_STICKY
            }
            else -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID).orEmpty()
                startVpn(serverId)
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnInternal()
        restorePreAmirDnsIfNeededBlocking()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpn(requestedServerId: String) {
        serviceScope.launch {
            if (VpnStateHolder.connectionState.value == ConnectionState.CONNECTED ||
                VpnStateHolder.connectionState.value == ConnectionState.CONNECTING
            ) {
                stopVpnInternal()
                delay(250)
            }

            val app = application as XrayNgApplication
            val rawSettings = app.repository.getSettingsSnapshot()
            val targetId = requestedServerId.ifBlank { rawSettings.selectedServerId }
            val server = app.repository.getServerById(targetId)
                ?: app.repository.getAllServers().firstOrNull()

            if (server == null) {
                VpnStateHolder.setError("No server configuration selected")
                VpnStateHolder.setState(ConnectionState.DISCONNECTED, "", "")
                stopSelf()
                return@launch
            }

            // Enforce Local DNS + Fake DNS for Amir protocol; restore prior user preference for other configs
            val settings = applyDnsPolicyForServer(app, server.protocol, rawSettings)

            currentServerRemarks = server.remarks
            VpnStateHolder.setError(null)
            VpnStateHolder.setState(ConnectionState.CONNECTING, server.id, server.remarks)
            VpnStateHolder.appendLog("Starting ${server.protocol.displayName}: ${server.remarks} (${server.maskedAddress()})")

            startAsForeground(buildNotification("Connecting to ${server.remarks}…"))

            runCatching {
                val rules = app.repository.getRoutingRules()
                val amirPort = if (server.protocol == ProtocolType.AMIR) {
                    val proxy = AmirLocalProxy(
                        serverConfig = server,
                        protectSocket = { sock -> protect(sock) },
                        onLog = { msg -> VpnStateHolder.appendLog(msg) }
                    )
                    amirLocalProxy = proxy
                    proxy.start()
                } else {
                    0
                }

                // Allocate ephemeral ports when Dynamic Local Proxy Port is enabled
                val effectiveLocalProxy = settings.effectiveLocalProxyEnabled()
                val runtimeSocksPort: Int
                val runtimeHttpPort: Int
                if (effectiveLocalProxy && settings.dynamicProxyPortEnabled) {
                    runtimeSocksPort = allocateFreePort(fallback = settings.socksPort)
                    runtimeHttpPort = allocateFreePort(fallback = settings.httpPort, excludePort = runtimeSocksPort)
                    VpnStateHolder.appendLog("Dynamic local proxy ports allocated: SOCKS=$runtimeSocksPort, HTTP=$runtimeHttpPort")
                } else {
                    runtimeSocksPort = settings.socksPort
                    runtimeHttpPort = settings.httpPort
                }

                VpnStateHolder.setActiveProxy(
                    ActiveRuntimeProxy(
                        enabled = effectiveLocalProxy,
                        socksPort = runtimeSocksPort,
                        httpPort = runtimeHttpPort,
                        username = settings.localProxyUsername,
                        password = settings.localProxyPassword
                    )
                )

                // Resolve outbound server domain before VPN interface starts when "Resolve and add to DNS Hosts" is active
                val resolvedHosts = resolveServerAddressForDnsHosts(server, settings)

                val configJson = XrayConfigBuilder.buildFullConfig(
                    server = server,
                    settings = settings,
                    customRules = rules,
                    amirLocalPort = amirPort,
                    resolvedServerHosts = resolvedHosts,
                    runtimeSocksPort = runtimeSocksPort,
                    runtimeHttpPort = runtimeHttpPort
                )

                val tunFd = if (settings.vpnMode) {
                    val pfd = buildTunInterface(server.remarks, settings, runtimeHttpPort)
                        ?: error("Failed to establish VPN interface (check VPN permission)")
                    tunInterface = pfd
                    pfd.fd
                } else {
                    VpnStateHolder.appendLog("Running in Proxy-Only mode (SOCKS:$runtimeSocksPort, HTTP:$runtimeHttpPort)")
                    0
                }

                val callbackHandler = object : CoreCallbackHandler {
                    override fun startup(): Long {
                        VpnStateHolder.appendLog("Xray-core started successfully")
                        return 0L
                    }

                    override fun shutdown(): Long {
                        VpnStateHolder.appendLog("Xray-core stopped")
                        return 0L
                    }

                    override fun onEmitStatus(status: Long, message: String?): Long {
                        if (!message.isNullOrBlank()) {
                            VpnStateHolder.appendLog(message)
                        }
                        return 0L
                    }
                }

                val controller = Libv2ray.newCoreController(callbackHandler)
                registerAndroidProcessFinder(controller)
                coreController = controller
                controller.startLoop(configJson, tunFd)

                VpnStateHolder.setState(ConnectionState.CONNECTED, server.id, server.remarks)
                updateNotification("Connected: ${server.remarks}")
                startTrafficMonitor(settings.speedDisplayEnabled)
            }.onFailure { err ->
                VpnStateHolder.appendLog("Start error: ${err.message}")
                VpnStateHolder.setError(err.message ?: "Failed to start Xray core")
                stopVpnInternal()
                restorePreAmirDnsIfNeeded(app)
                stopSelf()
            }
        }
    }

    private fun allocateFreePort(fallback: Int, excludePort: Int = -1): Int {
        repeat(4) {
            runCatching {
                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { sock ->
                    val port = sock.localPort
                    if (port in 1024..65535 && port != excludePort) {
                        return port
                    }
                }
            }
        }
        return if (fallback != excludePort) fallback else (fallback + 1).coerceAtMost(65535)
    }

    private fun resolveServerAddressForDnsHosts(
        server: ServerConfig,
        settings: AppSettings
    ): Map<String, List<String>> {
        if (settings.outboundDomainResolve != AppSettings.OUTBOUND_RESOLVE_AND_ADD) {
            return emptyMap()
        }
        val host = server.address.trim()
        if (host.isEmpty() || server.protocol == ProtocolType.AMIR || server.protocol == ProtocolType.CUSTOM) {
            return emptyMap()
        }
        // Skip literal IPv4 / IPv6 addresses
        if (isIpLiteral(host)) return emptyMap()

        return runCatching {
            val allAddrs = InetAddress.getAllByName(host).toList()
            val filtered = when {
                settings.preferIpv6 -> {
                    val v6 = allAddrs.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress }
                    val v4 = allAddrs.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
                    v6 + v4
                }
                else -> {
                    allAddrs.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
                        .ifEmpty { allAddrs.mapNotNull { it.hostAddress } }
                }
            }
            if (filtered.isNotEmpty()) {
                VpnStateHolder.appendLog("Resolved outbound host $host -> ${filtered.first()}")
                mapOf(host to filtered.distinct())
            } else {
                emptyMap()
            }
        }.getOrElse { emptyMap() }
    }

    private fun isIpLiteral(value: String): Boolean {
        val clean = value.removePrefix("[").removeSuffix("]")
        val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        if (ipv4.matches(clean)) return true
        return clean.contains(':') && !clean.contains('/')
    }

    private suspend fun applyDnsPolicyForServer(
        app: XrayNgApplication,
        protocol: ProtocolType,
        current: AppSettings
    ): AppSettings {
        return if (protocol == ProtocolType.AMIR) {
            val savedLocal = current.preAmirLocalDnsEnabled ?: current.localDnsEnabled
            val savedFake = current.preAmirFakeDnsEnabled ?: current.fakeDnsEnabled
            if (!current.localDnsEnabled || !current.fakeDnsEnabled || current.preAmirLocalDnsEnabled == null) {
                app.repository.updateSettings { s ->
                    s.copy(
                        preAmirLocalDnsEnabled = s.preAmirLocalDnsEnabled ?: savedLocal,
                        preAmirFakeDnsEnabled = s.preAmirFakeDnsEnabled ?: savedFake,
                        localDnsEnabled = true,
                        fakeDnsEnabled = true
                    )
                }
                VpnStateHolder.appendLog("Amir protocol active: forced Enable Local DNS = ON and Enable Fake DNS = ON")
            }
            current.copy(
                preAmirLocalDnsEnabled = current.preAmirLocalDnsEnabled ?: savedLocal,
                preAmirFakeDnsEnabled = current.preAmirFakeDnsEnabled ?: savedFake,
                localDnsEnabled = true,
                fakeDnsEnabled = true
            )
        } else {
            restorePreAmirDnsIfNeeded(app, current)
        }
    }

    private suspend fun restorePreAmirDnsIfNeeded(
        app: XrayNgApplication,
        snapshot: AppSettings? = null
    ): AppSettings {
        val current = snapshot ?: app.repository.getSettingsSnapshot()
        if (current.preAmirLocalDnsEnabled == null && current.preAmirFakeDnsEnabled == null) {
            return current
        }
        val restoredLocal = current.preAmirLocalDnsEnabled ?: current.localDnsEnabled
        val restoredFake = current.preAmirFakeDnsEnabled ?: current.fakeDnsEnabled
        app.repository.updateSettings { s ->
            s.copy(
                localDnsEnabled = s.preAmirLocalDnsEnabled ?: s.localDnsEnabled,
                fakeDnsEnabled = s.preAmirFakeDnsEnabled ?: s.fakeDnsEnabled,
                preAmirLocalDnsEnabled = null,
                preAmirFakeDnsEnabled = null
            )
        }
        VpnStateHolder.appendLog("Restored previous DNS settings (Local DNS=$restoredLocal, Fake DNS=$restoredFake)")
        return current.copy(
            localDnsEnabled = restoredLocal,
            fakeDnsEnabled = restoredFake,
            preAmirLocalDnsEnabled = null,
            preAmirFakeDnsEnabled = null
        )
    }

    private fun restorePreAmirDnsIfNeededBlocking() {
        runCatching {
            val app = application as XrayNgApplication
            runBlocking { restorePreAmirDnsIfNeeded(app) }
        }
    }

    private fun buildTunInterface(
        sessionName: String,
        settings: AppSettings,
        effectiveHttpPort: Int
    ): ParcelFileDescriptor? {
        val preset = AppSettings.resolveVpnInterfacePreset(settings.vpnInterfaceAddress)
        val mtu = settings.vpnMtu.coerceIn(1280, 9000)

        val builder = Builder()
            .setSession(sessionName.ifBlank { "PalaNG" })
            .setMtu(mtu)
            .addAddress(preset.clientIp, preset.prefixLength)

        val parsedVpnDns = parseVpnDnsServers(settings.vpnDns, settings.enableIpv6)
        if (settings.localDnsEnabled || settings.fakeDnsEnabled) {
            builder.addDnsServer(preset.routerDnsIp)
        } else if (parsedVpnDns.isNotEmpty()) {
            parsedVpnDns.forEach { dns ->
                runCatching { builder.addDnsServer(dns) }
            }
        } else {
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
        }

        if (settings.enableIpv6) {
            builder.addAddress("fd00:10:0:0::2", 126)
            if (settings.localDnsEnabled || settings.fakeDnsEnabled || parsedVpnDns.none { it.contains(':') }) {
                builder.addDnsServer("2606:4700:4700::1111")
            }
        }

        builder.addRoute("0.0.0.0", 0)
        if (settings.enableIpv6) {
            builder.addRoute("::", 0)
        }

        if (settings.bypassLan && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                "10.0.0.0" to 8,
                "127.0.0.0" to 8,
                "169.254.0.0" to 16,
                "172.16.0.0" to 12,
                "192.168.0.0" to 16,
                "224.0.0.0" to 4
            ).forEach { (ip, prefix) ->
                runCatching {
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(ip), prefix))
                }
            }
            // Ensure private TUN DNS gateway IP (e.g. 10.10.14.2 / 172.16.0.2) still routes into the TUN
            if (settings.localDnsEnabled || settings.fakeDnsEnabled) {
                runCatching {
                    builder.addRoute(preset.routerDnsIp, 32)
                }
            }
        }

        if (settings.perAppProxyEnabled && settings.perAppPackages.isNotEmpty()) {
            if (settings.perAppBypassMode) {
                runCatching { builder.addDisallowedApplication(packageName) }
                settings.perAppPackages.forEach { pkg ->
                    if (pkg != packageName) {
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }
            } else {
                settings.perAppPackages.forEach { pkg ->
                    if (pkg != packageName) {
                        runCatching { builder.addAllowedApplication(pkg) }
                    }
                }
            }
        } else {
            runCatching { builder.addDisallowedApplication(packageName) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (settings.appendHttpProxyToVpn && settings.effectiveLocalProxyEnabled()) {
                runCatching {
                    builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", effectiveHttpPort))
                }
            }
        }

        return builder.establish()
    }

    private fun parseVpnDnsServers(raw: String, includeIpv6: Boolean): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',', ' ', '\n')
            .map { it.trim() }
            .filter { token ->
                if (token.isEmpty()) return@filter false
                val isV6 = token.contains(':')
                if (isV6 && !includeIpv6) return@filter false
                isIpLiteral(token)
            }
            .distinct()
    }

    private fun registerAndroidProcessFinder(controller: CoreController) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        controller.registerProcessFinder(object : ProcessFinder {
            override fun findProcessByConnection(
                network: String?,
                srcIP: String?,
                srcPort: Long,
                destIP: String?,
                destPort: Long
            ): Long {
                return runCatching {
                    val proto = if (network.equals("udp", ignoreCase = true)) {
                        android.system.OsConstants.IPPROTO_UDP
                    } else {
                        android.system.OsConstants.IPPROTO_TCP
                    }
                    val local = InetSocketAddress(InetAddress.getByName(srcIP), srcPort.toInt())
                    val remote = InetSocketAddress(InetAddress.getByName(destIP), destPort.toInt())
                    cm.getConnectionOwnerUid(proto, local, remote).toLong()
                }.getOrDefault(-1L)
            }
        })
    }

    private fun startTrafficMonitor(speedDisplayEnabled: Boolean) {
        trafficJob?.cancel()
        trafficJob = serviceScope.launch {
            while (isActive && VpnStateHolder.connectionState.value == ConnectionState.CONNECTED) {
                delay(1000L)
                val controller = coreController ?: break
                val rawStats = runCatching { controller.queryAllOutboundTrafficStats() }.getOrNull().orEmpty()
                var uplink = 0L
                var downlink = 0L
                var directTotal = 0L
                if (rawStats.isNotBlank()) {
                    rawStats.split(";").forEach { item ->
                        val parts = item.split(",")
                        if (parts.size == 3) {
                            val value = parts[2].toLongOrNull() ?: 0L
                            when (parts[0]) {
                                "proxy" -> when (parts[1]) {
                                    "uplink" -> uplink += value
                                    "downlink" -> downlink += value
                                }
                                "direct" -> directTotal += value
                            }
                        }
                    }
                }
                VpnStateHolder.updateTraffic(uplink, downlink)
                if (speedDisplayEnabled) {
                    val dominantTag = if (directTotal > uplink + downlink) "Direct" else "Proxy"
                    val speedText = "↑ ${formatSpeed(uplink)} | ↓ ${formatSpeed(downlink)} ($dominantTag)"
                    updateNotification("$currentServerRemarks • $speedText")
                }
            }
        }
    }

    private fun measureActiveDelay(testUrl: String) {
        serviceScope.launch {
            val controller = coreController
            if (controller == null || !controller.isRunning) {
                VpnStateHolder.setLastTestedDelay(-1L)
                return@launch
            }
            VpnStateHolder.setTestingDelay(true)
            val delayMs = runCatching {
                controller.measureDelay(testUrl)
            }.getOrElse { err ->
                VpnStateHolder.appendLog("Delay test failed: ${err.message}")
                -1L
            }
            VpnStateHolder.setLastTestedDelay(delayMs)
            VpnStateHolder.appendLog(
                if (delayMs >= 0) "Real connection test: ${delayMs} ms" else "Real connection test: timeout/error"
            )
        }
    }

    private fun stopVpn() {
        VpnStateHolder.setState(ConnectionState.DISCONNECTING)
        serviceScope.launch {
            stopVpnInternal()
            val app = application as XrayNgApplication
            restorePreAmirDnsIfNeeded(app)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopVpnInternal() {
        trafficJob?.cancel()
        trafficJob = null
        runCatching {
            coreController?.let { controller ->
                if (controller.isRunning) {
                    controller.stopLoop()
                }
            }
        }
        coreController = null
        runCatching { amirLocalProxy?.stop() }
        amirLocalProxy = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        VpnStateHolder.setState(
            ConnectionState.DISCONNECTED,
            VpnStateHolder.activeServerId.value,
            VpnStateHolder.activeServerRemarks.value
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PalaNG VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Xray VPN connection status and live speed"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, XrayVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_vpn)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Disconnect", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_VPN"
        const val ACTION_STOP = "com.example.action.STOP_VPN"
        const val ACTION_MEASURE_DELAY = "com.example.action.MEASURE_DELAY"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_TEST_URL = "test_url"
        private const val CHANNEL_ID = "xrayng_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, serverId: String) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun measureDelay(context: Context, testUrl: String) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                action = ACTION_MEASURE_DELAY
                putExtra(EXTRA_TEST_URL, testUrl)
            }
            context.startService(intent)
        }

        fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec < 1024 -> "$bytesPerSec B/s"
                bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
                else -> String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
                bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }
    }
}

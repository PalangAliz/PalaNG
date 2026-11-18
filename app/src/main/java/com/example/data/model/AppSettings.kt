package com.example.data.model

enum class RoutingMode(val label: String) {
    BYPASS_LAN("Bypass LAN"),
    BYPASS_LAN_AND_MAINLAND("Bypass LAN & Mainland"),
    BYPASS_LAN_AND_IR("Bypass LAN & Iran"),
    GLOBAL_PROXY("Global Proxy"),
    CUSTOM("Custom Rules")
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class LanguageMode(val code: String) {
    SYSTEM("system"),
    EN("en"),
    FA("fa")
}

data class VpnAddressPreset(
    val key: String,
    val label: String,
    val clientIp: String,
    val routerDnsIp: String,
    val prefixLength: Int = 30
)

data class AppSettings(
    val selectedServerId: String = "",
    val selectedGroupId: String = GROUP_ALL,
    // Mode Settings
    val vpnMode: Boolean = true,
    // UI Settings
    val speedDisplayEnabled: Boolean = true,
    val confirmDeleteConfig: Boolean = true,
    val twoColumnLayout: Boolean = false,
    val showAllGroupTab: Boolean = true,
    val dynamicColorEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    // VPN Settings
    val enableIpv6: Boolean = false,
    val preferIpv6: Boolean = false,
    val localDnsEnabled: Boolean = false,
    val fakeDnsEnabled: Boolean = false,
    val preAmirLocalDnsEnabled: Boolean? = null,
    val preAmirFakeDnsEnabled: Boolean? = null,
    val vpnDns: String = "1.1.1.1",
    val appendHttpProxyToVpn: Boolean = false,
    val bypassLan: Boolean = true,
    val vpnInterfaceAddress: String = "26.26.26.x",
    val vpnMtu: Int = 1500,
    val perAppProxyEnabled: Boolean = false,
    val perAppBypassMode: Boolean = true,
    val perAppPackages: Set<String> = emptySet(),
    // Core Settings
    val sniffingEnabled: Boolean = true,
    val routeOnly: Boolean = false,
    val localProxyEnabled: Boolean = true,
    val allowLan: Boolean = false,
    val dynamicProxyPortEnabled: Boolean = false,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val localProxyUsername: String = "",
    val localProxyPassword: String = "",
    val socks5UdpEnabled: Boolean = true,
    val remoteDns: String = "https://1.1.1.1/dns-query",
    val domesticDns: String = "223.5.5.5",
    val dnsHosts: String = "",
    val logLevel: String = "warning",
    val outboundDomainResolve: String = OUTBOUND_RESOLVE_AND_ADD,
    val routingMode: RoutingMode = RoutingMode.BYPASS_LAN,
    val domainStrategy: String = "IPIfNonMatch",
    // Mux Settings
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 8,
    val muxXudpProxyUdp443: String = "reject",
    // Fragment Settings
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "100-200",
    val fragmentInterval: String = "10-20",
    val fragmentMaxSplit: String = "10",
    // Observatory Settings
    val observatoryMode: String = OBSERVATORY_NONE,
    val observatoryInterval: String = "3m",
    val burstObservatoryInterval: String = "5m",
    val burstObservatoryHttpMethod: String = "HEAD",
    val burstObservatorySampling: Int = 2,
    val burstObservatoryTimeout: String = "30s",
    // Advanced Settings
    val autoStartOnBoot: Boolean = false,
    val delayTestUrl: String = "https://www.gstatic.com/generate_204",
    val concurrentDelayTests: Int = 16,
    val connInfoTestUrl: String = "https://api.ip.sb/geoip"
) {
    fun effectiveLocalProxyEnabled(): Boolean = localProxyEnabled || !vpnMode

    companion object {
        const val GROUP_ALL = "ALL"
        const val GROUP_DEFAULT = ""

        const val OUTBOUND_RESOLVE_AND_ADD = "RESOLVE_AND_ADD"
        const val OUTBOUND_AS_IS = "AsIs"
        const val OUTBOUND_USE_IP = "UseIP"
        const val OUTBOUND_USE_IPV4 = "UseIPv4"
        const val OUTBOUND_USE_IPV6 = "UseIPv6"

        const val OBSERVATORY_NONE = "none"
        const val OBSERVATORY_LEAST_PING = "leastPing"
        const val OBSERVATORY_LEAST_LOAD = "leastLoad"

        val VPN_ADDRESS_PRESETS = listOf(
            VpnAddressPreset("26.26.26.x", "26.26.26.x", "26.26.26.1", "26.26.26.2"),
            VpnAddressPreset("10.10.14.x", "10.10.14.x", "10.10.14.1", "10.10.14.2"),
            VpnAddressPreset("10.0.0.x", "10.0.0.x", "10.0.0.1", "10.0.0.2"),
            VpnAddressPreset("172.16.0.x", "172.16.0.x", "172.16.0.1", "172.16.0.2")
        )

        fun resolveVpnInterfacePreset(key: String): VpnAddressPreset {
            return VPN_ADDRESS_PRESETS.firstOrNull { it.key == key } ?: VPN_ADDRESS_PRESETS.first()
        }
    }
}

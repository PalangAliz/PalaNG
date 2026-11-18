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

data class AppSettings(
    val selectedServerId: String = "",
    val selectedGroupId: String = GROUP_ALL,
    val vpnMode: Boolean = true,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val allowLan: Boolean = false,
    val sniffingEnabled: Boolean = true,
    val routeOnly: Boolean = false,
    val localDnsEnabled: Boolean = false,
    val fakeDnsEnabled: Boolean = false,
    val preAmirLocalDnsEnabled: Boolean? = null,
    val preAmirFakeDnsEnabled: Boolean? = null,
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val fragmentEnabled: Boolean = false,
    val fragmentLength: String = "100-200",
    val fragmentInterval: String = "10-20",
    val fragmentPackets: String = "tlshello",
    val remoteDns: String = "https://1.1.1.1/dns-query",
    val domesticDns: String = "223.5.5.5",
    val routingMode: RoutingMode = RoutingMode.BYPASS_LAN,
    val domainStrategy: String = "IPIfNonMatch",
    val delayTestUrl: String = "https://www.gstatic.com/generate_204",
    val preferIpv6: Boolean = false,
    val bypassLan: Boolean = true,
    val perAppProxyEnabled: Boolean = false,
    val perAppBypassMode: Boolean = true,
    val perAppPackages: Set<String> = emptySet(),
    val logLevel: String = "warning",
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val languageMode: LanguageMode = LanguageMode.SYSTEM
) {
    companion object {
        const val GROUP_ALL = "ALL"
        const val GROUP_DEFAULT = ""
    }
}

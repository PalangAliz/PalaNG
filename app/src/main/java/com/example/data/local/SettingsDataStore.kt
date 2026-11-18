package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.LanguageMode
import com.example.data.model.RoutingMode
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xrayng_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val SELECTED_GROUP_ID = stringPreferencesKey("selected_group_id")
        val VPN_MODE = booleanPreferencesKey("vpn_mode")

        // UI settings
        val SPEED_DISPLAY_ENABLED = booleanPreferencesKey("speed_display_enabled")
        val CONFIRM_DELETE_CONFIG = booleanPreferencesKey("confirm_delete_config")
        val TWO_COLUMN_LAYOUT = booleanPreferencesKey("two_column_layout")
        val SHOW_ALL_GROUP_TAB = booleanPreferencesKey("show_all_group_tab")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")

        // VPN settings
        val ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val PREFER_IPV6 = booleanPreferencesKey("prefer_ipv6")
        val LOCAL_DNS_ENABLED = booleanPreferencesKey("local_dns_enabled")
        val FAKE_DNS_ENABLED = booleanPreferencesKey("fake_dns_enabled")
        val PRE_AMIR_LOCAL_DNS = intPreferencesKey("pre_amir_local_dns")
        val PRE_AMIR_FAKE_DNS = intPreferencesKey("pre_amir_fake_dns")
        val VPN_DNS = stringPreferencesKey("vpn_dns")
        val APPEND_HTTP_PROXY_TO_VPN = booleanPreferencesKey("append_http_proxy_to_vpn")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val VPN_INTERFACE_ADDRESS = stringPreferencesKey("vpn_interface_address")
        val VPN_MTU = intPreferencesKey("vpn_mtu")
        val PER_APP_PROXY_ENABLED = booleanPreferencesKey("per_app_proxy_enabled")
        val PER_APP_BYPASS_MODE = booleanPreferencesKey("per_app_bypass_mode")
        val PER_APP_PACKAGES = stringSetPreferencesKey("per_app_packages")

        // Core settings
        val SNIFFING_ENABLED = booleanPreferencesKey("sniffing_enabled")
        val ROUTE_ONLY = booleanPreferencesKey("route_only")
        val LOCAL_PROXY_ENABLED = booleanPreferencesKey("local_proxy_enabled")
        val ALLOW_LAN = booleanPreferencesKey("allow_lan")
        val DYNAMIC_PROXY_PORT_ENABLED = booleanPreferencesKey("dynamic_proxy_port_enabled")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val HTTP_PORT = intPreferencesKey("http_port")
        val LOCAL_PROXY_USERNAME = stringPreferencesKey("local_proxy_username")
        val LOCAL_PROXY_PASSWORD = stringPreferencesKey("local_proxy_password")
        val SOCKS5_UDP_ENABLED = booleanPreferencesKey("socks5_udp_enabled")
        val REMOTE_DNS = stringPreferencesKey("remote_dns")
        val DOMESTIC_DNS = stringPreferencesKey("domestic_dns")
        val DNS_HOSTS = stringPreferencesKey("dns_hosts")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val OUTBOUND_DOMAIN_RESOLVE = stringPreferencesKey("outbound_domain_resolve")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val DOMAIN_STRATEGY = stringPreferencesKey("domain_strategy")

        // Mux settings
        val MUX_ENABLED = booleanPreferencesKey("mux_enabled")
        val MUX_CONCURRENCY = intPreferencesKey("mux_concurrency")
        val MUX_XUDP_CONCURRENCY = intPreferencesKey("mux_xudp_concurrency")
        val MUX_XUDP_PROXY_UDP443 = stringPreferencesKey("mux_xudp_proxy_udp443")

        // Fragment settings
        val FRAGMENT_ENABLED = booleanPreferencesKey("fragment_enabled")
        val FRAGMENT_PACKETS = stringPreferencesKey("fragment_packets")
        val FRAGMENT_LENGTH = stringPreferencesKey("fragment_length")
        val FRAGMENT_INTERVAL = stringPreferencesKey("fragment_interval")
        val FRAGMENT_MAX_SPLIT = stringPreferencesKey("fragment_max_split")

        // Observatory settings
        val OBSERVATORY_MODE = stringPreferencesKey("observatory_mode")
        val OBSERVATORY_INTERVAL = stringPreferencesKey("observatory_interval")
        val BURST_OBSERVATORY_INTERVAL = stringPreferencesKey("burst_observatory_interval")
        val BURST_OBSERVATORY_HTTP_METHOD = stringPreferencesKey("burst_observatory_http_method")
        val BURST_OBSERVATORY_SAMPLING = intPreferencesKey("burst_observatory_sampling")
        val BURST_OBSERVATORY_TIMEOUT = stringPreferencesKey("burst_observatory_timeout")

        // Advanced settings
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val DELAY_TEST_URL = stringPreferencesKey("delay_test_url")
        val CONCURRENT_DELAY_TESTS = intPreferencesKey("concurrent_delay_tests")
        val CONN_INFO_TEST_URL = stringPreferencesKey("conn_info_test_url")
    }

    private fun readSettings(prefs: Preferences): AppSettings {
        val preLocal = when (prefs[Keys.PRE_AMIR_LOCAL_DNS] ?: -1) {
            1 -> true
            0 -> false
            else -> null
        }
        val preFake = when (prefs[Keys.PRE_AMIR_FAKE_DNS] ?: -1) {
            1 -> true
            0 -> false
            else -> null
        }
        val preferIpv6 = prefs[Keys.PREFER_IPV6] ?: false
        return AppSettings(
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID] ?: "",
            selectedGroupId = prefs[Keys.SELECTED_GROUP_ID] ?: AppSettings.GROUP_ALL,
            vpnMode = prefs[Keys.VPN_MODE] ?: true,

            speedDisplayEnabled = prefs[Keys.SPEED_DISPLAY_ENABLED] ?: true,
            confirmDeleteConfig = prefs[Keys.CONFIRM_DELETE_CONFIG] ?: true,
            twoColumnLayout = prefs[Keys.TWO_COLUMN_LAYOUT] ?: false,
            showAllGroupTab = prefs[Keys.SHOW_ALL_GROUP_TAB] ?: true,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: false,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.LIGHT,
            languageMode = prefs[Keys.LANGUAGE_MODE]?.let { name ->
                LanguageMode.entries.firstOrNull { it.name == name }
            } ?: LanguageMode.SYSTEM,

            enableIpv6 = prefs[Keys.ENABLE_IPV6] ?: preferIpv6,
            preferIpv6 = preferIpv6,
            localDnsEnabled = prefs[Keys.LOCAL_DNS_ENABLED] ?: false,
            fakeDnsEnabled = prefs[Keys.FAKE_DNS_ENABLED] ?: false,
            preAmirLocalDnsEnabled = preLocal,
            preAmirFakeDnsEnabled = preFake,
            vpnDns = prefs[Keys.VPN_DNS] ?: "1.1.1.1",
            appendHttpProxyToVpn = prefs[Keys.APPEND_HTTP_PROXY_TO_VPN] ?: false,
            bypassLan = prefs[Keys.BYPASS_LAN] ?: true,
            vpnInterfaceAddress = prefs[Keys.VPN_INTERFACE_ADDRESS] ?: "26.26.26.x",
            vpnMtu = (prefs[Keys.VPN_MTU] ?: 1500).coerceIn(1280, 9000),
            perAppProxyEnabled = prefs[Keys.PER_APP_PROXY_ENABLED] ?: false,
            perAppBypassMode = prefs[Keys.PER_APP_BYPASS_MODE] ?: true,
            perAppPackages = prefs[Keys.PER_APP_PACKAGES] ?: emptySet(),

            sniffingEnabled = prefs[Keys.SNIFFING_ENABLED] ?: true,
            routeOnly = prefs[Keys.ROUTE_ONLY] ?: false,
            localProxyEnabled = prefs[Keys.LOCAL_PROXY_ENABLED] ?: true,
            allowLan = prefs[Keys.ALLOW_LAN] ?: false,
            dynamicProxyPortEnabled = prefs[Keys.DYNAMIC_PROXY_PORT_ENABLED] ?: false,
            socksPort = prefs[Keys.SOCKS_PORT] ?: 10808,
            httpPort = prefs[Keys.HTTP_PORT] ?: 10809,
            localProxyUsername = prefs[Keys.LOCAL_PROXY_USERNAME] ?: "",
            localProxyPassword = prefs[Keys.LOCAL_PROXY_PASSWORD] ?: "",
            socks5UdpEnabled = prefs[Keys.SOCKS5_UDP_ENABLED] ?: true,
            remoteDns = prefs[Keys.REMOTE_DNS] ?: "https://1.1.1.1/dns-query",
            domesticDns = prefs[Keys.DOMESTIC_DNS] ?: "223.5.5.5",
            dnsHosts = prefs[Keys.DNS_HOSTS] ?: "",
            logLevel = prefs[Keys.LOG_LEVEL] ?: "warning",
            outboundDomainResolve = prefs[Keys.OUTBOUND_DOMAIN_RESOLVE] ?: AppSettings.OUTBOUND_RESOLVE_AND_ADD,
            routingMode = prefs[Keys.ROUTING_MODE]?.let { name ->
                RoutingMode.entries.firstOrNull { it.name == name }
            } ?: RoutingMode.BYPASS_LAN,
            domainStrategy = prefs[Keys.DOMAIN_STRATEGY] ?: "IPIfNonMatch",

            muxEnabled = prefs[Keys.MUX_ENABLED] ?: false,
            muxConcurrency = (prefs[Keys.MUX_CONCURRENCY] ?: 8).coerceIn(-1, 1024),
            muxXudpConcurrency = (prefs[Keys.MUX_XUDP_CONCURRENCY] ?: 8).coerceIn(-1, 1024),
            muxXudpProxyUdp443 = prefs[Keys.MUX_XUDP_PROXY_UDP443] ?: "reject",

            fragmentEnabled = prefs[Keys.FRAGMENT_ENABLED] ?: false,
            fragmentPackets = prefs[Keys.FRAGMENT_PACKETS] ?: "tlshello",
            fragmentLength = prefs[Keys.FRAGMENT_LENGTH] ?: "100-200",
            fragmentInterval = prefs[Keys.FRAGMENT_INTERVAL] ?: "10-20",
            fragmentMaxSplit = prefs[Keys.FRAGMENT_MAX_SPLIT] ?: "10",

            observatoryMode = prefs[Keys.OBSERVATORY_MODE] ?: AppSettings.OBSERVATORY_NONE,
            observatoryInterval = prefs[Keys.OBSERVATORY_INTERVAL] ?: "3m",
            burstObservatoryInterval = prefs[Keys.BURST_OBSERVATORY_INTERVAL] ?: "5m",
            burstObservatoryHttpMethod = prefs[Keys.BURST_OBSERVATORY_HTTP_METHOD] ?: "HEAD",
            burstObservatorySampling = (prefs[Keys.BURST_OBSERVATORY_SAMPLING] ?: 2).coerceIn(1, 20),
            burstObservatoryTimeout = prefs[Keys.BURST_OBSERVATORY_TIMEOUT] ?: "30s",

            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            delayTestUrl = prefs[Keys.DELAY_TEST_URL] ?: "https://www.gstatic.com/generate_204",
            concurrentDelayTests = (prefs[Keys.CONCURRENT_DELAY_TESTS] ?: 16).coerceIn(1, 64),
            connInfoTestUrl = prefs[Keys.CONN_INFO_TEST_URL] ?: "https://api.ip.sb/geoip"
        )
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map(::readSettings)

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun updateSelectedServerId(id: String) {
        context.dataStore.edit { it[Keys.SELECTED_SERVER_ID] = id }
    }

    suspend fun updateSelectedGroupId(groupId: String) {
        context.dataStore.edit { it[Keys.SELECTED_GROUP_ID] = groupId }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = readSettings(prefs)
            val updated = transform(current)
            prefs[Keys.SELECTED_SERVER_ID] = updated.selectedServerId
            prefs[Keys.SELECTED_GROUP_ID] = updated.selectedGroupId
            prefs[Keys.VPN_MODE] = updated.vpnMode

            prefs[Keys.SPEED_DISPLAY_ENABLED] = updated.speedDisplayEnabled
            prefs[Keys.CONFIRM_DELETE_CONFIG] = updated.confirmDeleteConfig
            prefs[Keys.TWO_COLUMN_LAYOUT] = updated.twoColumnLayout
            prefs[Keys.SHOW_ALL_GROUP_TAB] = updated.showAllGroupTab
            prefs[Keys.DYNAMIC_COLOR_ENABLED] = updated.dynamicColorEnabled
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.LANGUAGE_MODE] = updated.languageMode.name

            prefs[Keys.ENABLE_IPV6] = updated.enableIpv6
            prefs[Keys.PREFER_IPV6] = updated.preferIpv6
            prefs[Keys.LOCAL_DNS_ENABLED] = updated.localDnsEnabled
            prefs[Keys.FAKE_DNS_ENABLED] = updated.fakeDnsEnabled
            prefs[Keys.PRE_AMIR_LOCAL_DNS] = when (updated.preAmirLocalDnsEnabled) {
                true -> 1
                false -> 0
                null -> -1
            }
            prefs[Keys.PRE_AMIR_FAKE_DNS] = when (updated.preAmirFakeDnsEnabled) {
                true -> 1
                false -> 0
                null -> -1
            }
            prefs[Keys.VPN_DNS] = updated.vpnDns
            prefs[Keys.APPEND_HTTP_PROXY_TO_VPN] = updated.appendHttpProxyToVpn
            prefs[Keys.BYPASS_LAN] = updated.bypassLan
            prefs[Keys.VPN_INTERFACE_ADDRESS] = updated.vpnInterfaceAddress
            prefs[Keys.VPN_MTU] = updated.vpnMtu.coerceIn(1280, 9000)
            prefs[Keys.PER_APP_PROXY_ENABLED] = updated.perAppProxyEnabled
            prefs[Keys.PER_APP_BYPASS_MODE] = updated.perAppBypassMode
            prefs[Keys.PER_APP_PACKAGES] = updated.perAppPackages

            prefs[Keys.SNIFFING_ENABLED] = updated.sniffingEnabled
            prefs[Keys.ROUTE_ONLY] = updated.routeOnly
            prefs[Keys.LOCAL_PROXY_ENABLED] = updated.localProxyEnabled
            prefs[Keys.ALLOW_LAN] = updated.allowLan
            prefs[Keys.DYNAMIC_PROXY_PORT_ENABLED] = updated.dynamicProxyPortEnabled
            prefs[Keys.SOCKS_PORT] = updated.socksPort
            prefs[Keys.HTTP_PORT] = updated.httpPort
            prefs[Keys.LOCAL_PROXY_USERNAME] = updated.localProxyUsername
            prefs[Keys.LOCAL_PROXY_PASSWORD] = updated.localProxyPassword
            prefs[Keys.SOCKS5_UDP_ENABLED] = updated.socks5UdpEnabled
            prefs[Keys.REMOTE_DNS] = updated.remoteDns
            prefs[Keys.DOMESTIC_DNS] = updated.domesticDns
            prefs[Keys.DNS_HOSTS] = updated.dnsHosts
            prefs[Keys.LOG_LEVEL] = updated.logLevel
            prefs[Keys.OUTBOUND_DOMAIN_RESOLVE] = updated.outboundDomainResolve
            prefs[Keys.ROUTING_MODE] = updated.routingMode.name
            prefs[Keys.DOMAIN_STRATEGY] = updated.domainStrategy

            prefs[Keys.MUX_ENABLED] = updated.muxEnabled
            prefs[Keys.MUX_CONCURRENCY] = updated.muxConcurrency.coerceIn(-1, 1024)
            prefs[Keys.MUX_XUDP_CONCURRENCY] = updated.muxXudpConcurrency.coerceIn(-1, 1024)
            prefs[Keys.MUX_XUDP_PROXY_UDP443] = updated.muxXudpProxyUdp443

            prefs[Keys.FRAGMENT_ENABLED] = updated.fragmentEnabled
            prefs[Keys.FRAGMENT_PACKETS] = updated.fragmentPackets
            prefs[Keys.FRAGMENT_LENGTH] = updated.fragmentLength
            prefs[Keys.FRAGMENT_INTERVAL] = updated.fragmentInterval
            prefs[Keys.FRAGMENT_MAX_SPLIT] = updated.fragmentMaxSplit

            prefs[Keys.OBSERVATORY_MODE] = updated.observatoryMode
            prefs[Keys.OBSERVATORY_INTERVAL] = updated.observatoryInterval
            prefs[Keys.BURST_OBSERVATORY_INTERVAL] = updated.burstObservatoryInterval
            prefs[Keys.BURST_OBSERVATORY_HTTP_METHOD] = updated.burstObservatoryHttpMethod
            prefs[Keys.BURST_OBSERVATORY_SAMPLING] = updated.burstObservatorySampling.coerceIn(1, 20)
            prefs[Keys.BURST_OBSERVATORY_TIMEOUT] = updated.burstObservatoryTimeout

            prefs[Keys.AUTO_START_ON_BOOT] = updated.autoStartOnBoot
            prefs[Keys.DELAY_TEST_URL] = updated.delayTestUrl
            prefs[Keys.CONCURRENT_DELAY_TESTS] = updated.concurrentDelayTests.coerceIn(1, 64)
            prefs[Keys.CONN_INFO_TEST_URL] = updated.connInfoTestUrl
        }
    }
}

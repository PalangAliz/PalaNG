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
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val HTTP_PORT = intPreferencesKey("http_port")
        val ALLOW_LAN = booleanPreferencesKey("allow_lan")
        val SNIFFING_ENABLED = booleanPreferencesKey("sniffing_enabled")
        val ROUTE_ONLY = booleanPreferencesKey("route_only")
        val LOCAL_DNS_ENABLED = booleanPreferencesKey("local_dns_enabled")
        val FAKE_DNS_ENABLED = booleanPreferencesKey("fake_dns_enabled")
        val PRE_AMIR_LOCAL_DNS = intPreferencesKey("pre_amir_local_dns") // -1 unset, 0 false, 1 true
        val PRE_AMIR_FAKE_DNS = intPreferencesKey("pre_amir_fake_dns")   // -1 unset, 0 false, 1 true
        val MUX_ENABLED = booleanPreferencesKey("mux_enabled")
        val MUX_CONCURRENCY = intPreferencesKey("mux_concurrency")
        val FRAGMENT_ENABLED = booleanPreferencesKey("fragment_enabled")
        val FRAGMENT_LENGTH = stringPreferencesKey("fragment_length")
        val FRAGMENT_INTERVAL = stringPreferencesKey("fragment_interval")
        val FRAGMENT_PACKETS = stringPreferencesKey("fragment_packets")
        val REMOTE_DNS = stringPreferencesKey("remote_dns")
        val DOMESTIC_DNS = stringPreferencesKey("domestic_dns")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val DOMAIN_STRATEGY = stringPreferencesKey("domain_strategy")
        val DELAY_TEST_URL = stringPreferencesKey("delay_test_url")
        val PREFER_IPV6 = booleanPreferencesKey("prefer_ipv6")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val PER_APP_PROXY_ENABLED = booleanPreferencesKey("per_app_proxy_enabled")
        val PER_APP_BYPASS_MODE = booleanPreferencesKey("per_app_bypass_mode")
        val PER_APP_PACKAGES = stringSetPreferencesKey("per_app_packages")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
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
        return AppSettings(
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID] ?: "",
            selectedGroupId = prefs[Keys.SELECTED_GROUP_ID] ?: AppSettings.GROUP_ALL,
            vpnMode = prefs[Keys.VPN_MODE] ?: true,
            socksPort = prefs[Keys.SOCKS_PORT] ?: 10808,
            httpPort = prefs[Keys.HTTP_PORT] ?: 10809,
            allowLan = prefs[Keys.ALLOW_LAN] ?: false,
            sniffingEnabled = prefs[Keys.SNIFFING_ENABLED] ?: true,
            routeOnly = prefs[Keys.ROUTE_ONLY] ?: false,
            localDnsEnabled = prefs[Keys.LOCAL_DNS_ENABLED] ?: false,
            fakeDnsEnabled = prefs[Keys.FAKE_DNS_ENABLED] ?: false,
            preAmirLocalDnsEnabled = preLocal,
            preAmirFakeDnsEnabled = preFake,
            muxEnabled = prefs[Keys.MUX_ENABLED] ?: false,
            muxConcurrency = prefs[Keys.MUX_CONCURRENCY] ?: 8,
            fragmentEnabled = prefs[Keys.FRAGMENT_ENABLED] ?: false,
            fragmentLength = prefs[Keys.FRAGMENT_LENGTH] ?: "100-200",
            fragmentInterval = prefs[Keys.FRAGMENT_INTERVAL] ?: "10-20",
            fragmentPackets = prefs[Keys.FRAGMENT_PACKETS] ?: "tlshello",
            remoteDns = prefs[Keys.REMOTE_DNS] ?: "https://1.1.1.1/dns-query",
            domesticDns = prefs[Keys.DOMESTIC_DNS] ?: "223.5.5.5",
            routingMode = prefs[Keys.ROUTING_MODE]?.let { name ->
                RoutingMode.entries.firstOrNull { it.name == name }
            } ?: RoutingMode.BYPASS_LAN,
            domainStrategy = prefs[Keys.DOMAIN_STRATEGY] ?: "IPIfNonMatch",
            delayTestUrl = prefs[Keys.DELAY_TEST_URL] ?: "https://www.gstatic.com/generate_204",
            preferIpv6 = prefs[Keys.PREFER_IPV6] ?: false,
            bypassLan = prefs[Keys.BYPASS_LAN] ?: true,
            perAppProxyEnabled = prefs[Keys.PER_APP_PROXY_ENABLED] ?: false,
            perAppBypassMode = prefs[Keys.PER_APP_BYPASS_MODE] ?: true,
            perAppPackages = prefs[Keys.PER_APP_PACKAGES] ?: emptySet(),
            logLevel = prefs[Keys.LOG_LEVEL] ?: "warning",
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.LIGHT,
            languageMode = prefs[Keys.LANGUAGE_MODE]?.let { name ->
                LanguageMode.entries.firstOrNull { it.name == name }
            } ?: LanguageMode.SYSTEM
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
            prefs[Keys.SOCKS_PORT] = updated.socksPort
            prefs[Keys.HTTP_PORT] = updated.httpPort
            prefs[Keys.ALLOW_LAN] = updated.allowLan
            prefs[Keys.SNIFFING_ENABLED] = updated.sniffingEnabled
            prefs[Keys.ROUTE_ONLY] = updated.routeOnly
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
            prefs[Keys.MUX_ENABLED] = updated.muxEnabled
            prefs[Keys.MUX_CONCURRENCY] = updated.muxConcurrency
            prefs[Keys.FRAGMENT_ENABLED] = updated.fragmentEnabled
            prefs[Keys.FRAGMENT_LENGTH] = updated.fragmentLength
            prefs[Keys.FRAGMENT_INTERVAL] = updated.fragmentInterval
            prefs[Keys.FRAGMENT_PACKETS] = updated.fragmentPackets
            prefs[Keys.REMOTE_DNS] = updated.remoteDns
            prefs[Keys.DOMESTIC_DNS] = updated.domesticDns
            prefs[Keys.ROUTING_MODE] = updated.routingMode.name
            prefs[Keys.DOMAIN_STRATEGY] = updated.domainStrategy
            prefs[Keys.DELAY_TEST_URL] = updated.delayTestUrl
            prefs[Keys.PREFER_IPV6] = updated.preferIpv6
            prefs[Keys.BYPASS_LAN] = updated.bypassLan
            prefs[Keys.PER_APP_PROXY_ENABLED] = updated.perAppProxyEnabled
            prefs[Keys.PER_APP_BYPASS_MODE] = updated.perAppBypassMode
            prefs[Keys.PER_APP_PACKAGES] = updated.perAppPackages
            prefs[Keys.LOG_LEVEL] = updated.logLevel
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.LANGUAGE_MODE] = updated.languageMode.name
        }
    }
}

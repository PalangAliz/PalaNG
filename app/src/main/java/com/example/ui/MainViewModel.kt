package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.XrayNgApplication
import com.example.core.AmirLocalProxy
import com.example.core.AssetHelper
import com.example.core.ConfigParser
import com.example.core.GeoAssetInfo
import com.example.core.XrayConfigBuilder
import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.RoutingRule
import com.example.data.model.ServerConfig
import com.example.data.model.SubscriptionGroup
import com.example.service.ActiveRuntimeProxy
import com.example.service.ConnectionState
import com.example.service.VpnStateHolder
import com.example.service.XrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

sealed interface ConnectionInfoState {
    data object Idle : ConnectionInfoState
    data object Loading : ConnectionInfoState
    data class Success(
        val url: String,
        val elapsedMs: Long,
        val summaryLines: List<Pair<String, String>>,
        val rawBody: String,
        val countryFlag: String = "",
        val countryLabel: String = ""
    ) : ConnectionInfoState
    data class Error(val message: String) : ConnectionInfoState
}

data class HomeUiState(
    val servers: List<ServerConfig> = emptyList(),
    val visibleServers: List<ServerConfig> = emptyList(),
    val subscriptions: List<SubscriptionGroup> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val searchQuery: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val activeServerId: String = "",
    val isTestingBatch: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null,
    val isUpdatingSubs: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XrayNgApplication
    private val repository = app.repository

    private val _searchQuery = MutableStateFlow("")
    private val _isTestingBatch = MutableStateFlow(false)
    private val _batchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val _isUpdatingSubs = MutableStateFlow(false)
    private val _assetInfos = MutableStateFlow<List<GeoAssetInfo>>(emptyList())
    private val _updatingAssetName = MutableStateFlow<String?>(null)
    private val _connectionInfoState = MutableStateFlow<ConnectionInfoState>(ConnectionInfoState.Idle)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _pendingTunnelRestart = MutableStateFlow(false)
    val pendingTunnelRestart: StateFlow<Boolean> = _pendingTunnelRestart.asStateFlow()

    val settingsFlow: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val subscriptionsFlow: StateFlow<List<SubscriptionGroup>> = repository.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routingRulesFlow: StateFlow<List<RoutingRule>> = repository.routingRulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assetInfos: StateFlow<List<GeoAssetInfo>> = _assetInfos.asStateFlow()
    val updatingAssetName: StateFlow<String?> = _updatingAssetName.asStateFlow()
    val connectionInfoState: StateFlow<ConnectionInfoState> = _connectionInfoState.asStateFlow()

    val connectionState = VpnStateHolder.connectionState
    val trafficStats = VpnStateHolder.trafficStats
    val activeProxy: StateFlow<ActiveRuntimeProxy> = VpnStateHolder.activeProxy
    val lastTestedDelayMs = VpnStateHolder.lastTestedDelayMs
    val isTestingActiveDelay = VpnStateHolder.isTestingDelay
    val logs = VpnStateHolder.logs
    val vpnError = VpnStateHolder.errorMessage

    private var batchTestJob: Job? = null

    val homeUiState: StateFlow<HomeUiState> = combine(
        repository.serversFlow,
        repository.subscriptionsFlow,
        repository.settingsFlow,
        _searchQuery,
        VpnStateHolder.connectionState,
        VpnStateHolder.activeServerId,
        _isTestingBatch,
        _batchProgress,
        _isUpdatingSubs
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allServers = values[0] as List<ServerConfig>
        @Suppress("UNCHECKED_CAST")
        val subs = values[1] as List<SubscriptionGroup>
        val settings = values[2] as AppSettings
        val query = values[3] as String
        val connState = values[4] as ConnectionState
        val activeId = values[5] as String
        val testingBatch = values[6] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = values[7] as Pair<Int, Int>?
        val updatingSubs = values[8] as Boolean

        val groupFiltered = when (settings.selectedGroupId) {
            AppSettings.GROUP_ALL -> allServers
            else -> allServers.filter { it.subscriptionId == settings.selectedGroupId }
        }
        val queryFiltered = if (query.isBlank()) {
            groupFiltered
        } else {
            val q = query.trim().lowercase()
            groupFiltered.filter {
                it.remarks.lowercase().contains(q) ||
                    it.address.lowercase().contains(q) ||
                    it.protocol.displayName.lowercase().contains(q)
            }
        }

        HomeUiState(
            servers = allServers,
            visibleServers = queryFiltered,
            subscriptions = subs,
            settings = settings,
            searchQuery = query,
            connectionState = connState,
            activeServerId = activeId,
            isTestingBatch = testingBatch,
            batchProgress = progress,
            isUpdatingSubs = updatingSubs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshAssetInfos()
        viewModelScope.launch {
            val s = repository.getSettingsSnapshot()
            if (s.preAmirLocalDnsEnabled != null || s.preAmirFakeDnsEnabled != null) {
                repository.updateSettings { cur ->
                    cur.copy(preAmirLocalDnsEnabled = null, preAmirFakeDnsEnabled = null)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectGroup(groupId: String) {
        viewModelScope.launch {
            repository.selectGroup(groupId)
        }
    }

    fun selectServer(serverId: String) {
        viewModelScope.launch {
            repository.selectServer(serverId)
            if (VpnStateHolder.connectionState.value == ConnectionState.CONNECTED &&
                VpnStateHolder.activeServerId.value != serverId
            ) {
                _pendingTunnelRestart.value = false
                XrayVpnService.start(app, serverId)
            }
        }
    }

    fun toggleVpnConnection() {
        val state = VpnStateHolder.connectionState.value
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            _pendingTunnelRestart.value = false
            XrayVpnService.stop(app)
        } else {
            _pendingTunnelRestart.value = false
            viewModelScope.launch {
                val selectedId = repository.getSettingsSnapshot().selectedServerId
                val target = if (selectedId.isNotBlank()) {
                    repository.getServerById(selectedId)
                } else {
                    repository.getAllServers().firstOrNull()
                }
                if (target == null) {
                    _snackbarEvents.tryEmit("Please add or select a server configuration first")
                } else {
                    if (selectedId != target.id) {
                        repository.selectServer(target.id)
                    }
                    XrayVpnService.start(app, target.id)
                }
            }
        }
    }

    fun applyPendingTunnelRestartIfNeeded() {
        if (!_pendingTunnelRestart.value) return
        _pendingTunnelRestart.value = false
        val state = VpnStateHolder.connectionState.value
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            viewModelScope.launch {
                val activeId = VpnStateHolder.activeServerId.value.ifBlank {
                    repository.getSettingsSnapshot().selectedServerId
                }
                if (activeId.isNotBlank()) {
                    XrayVpnService.start(app, activeId)
                    _snackbarEvents.tryEmit("Reconnecting with updated settings…")
                }
            }
        }
    }

    private fun isTunnelActive(): Boolean {
        val state = VpnStateHolder.connectionState.value
        return state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
    }

    fun measureConnectedDelay() {
        viewModelScope.launch {
            val url = repository.getSettingsSnapshot().delayTestUrl
            XrayVpnService.measureDelay(app, url)
        }
    }

    fun clearConnectionInfo() {
        _connectionInfoState.value = ConnectionInfoState.Idle
    }

    fun testCurrentConnectionInfo() {
        if (VpnStateHolder.connectionState.value != ConnectionState.CONNECTED) {
            _snackbarEvents.tryEmit("Connect to a server first")
            return
        }
        val proxy = VpnStateHolder.activeProxy.value
        if (!proxy.enabled) {
            _connectionInfoState.value = ConnectionInfoState.Error(
                "Local proxy is disabled in Core Settings. Enable Local Proxy to run connection info queries through Xray."
            )
            return
        }
        _connectionInfoState.value = ConnectionInfoState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsSnapshot()
            val url = settings.connInfoTestUrl.trim().ifBlank { "https://api.ip.sb/geoip" }
            val startMs = System.currentTimeMillis()
            runCatching {
                val builder = OkHttpClient.Builder()
                    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.httpPort)))
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                if (proxy.username.isNotBlank() || proxy.password.isNotBlank()) {
                    val credential = Credentials.basic(proxy.username, proxy.password)
                    builder.proxyAuthenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
                val client = builder.build()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PalaNG/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty().trim()
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: ${resp.message}")
                    }
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val parsed = parseConnectionInfo(body)
                    _connectionInfoState.value = ConnectionInfoState.Success(
                        url = url,
                        elapsedMs = elapsed,
                        summaryLines = parsed.summaryLines,
                        rawBody = body,
                        countryFlag = parsed.countryFlag,
                        countryLabel = parsed.countryLabel
                    )
                }
            }.onFailure { err ->
                _connectionInfoState.value = ConnectionInfoState.Error(
                    err.message ?: "Failed to query connection info"
                )
            }
        }
    }

    internal data class ParsedConnectionInfo(
        val summaryLines: List<Pair<String, String>>,
        val countryFlag: String,
        val countryLabel: String
    )

    companion object {
        fun countryCodeToFlagEmoji(countryCode: String): String {
            val clean = countryCode.trim().uppercase()
            if (clean.length != 2) return ""
            val c0 = clean[0]
            val c1 = clean[1]
            if (c0 !in 'A'..'Z' || c1 !in 'A'..'Z') return ""
            val first = 0x1F1E6 + (c0.code - 'A'.code)
            val second = 0x1F1E6 + (c1.code - 'A'.code)
            return String(Character.toChars(first)) + String(Character.toChars(second))
        }

        internal fun parseConnectionInfo(body: String): ParsedConnectionInfo {
            val obj = runCatching { JSONObject(body) }.getOrNull()
                ?: return ParsedConnectionInfo(emptyList(), "", "")
            val pairs = mutableListOf<Pair<String, String>>()

            val ipVal = obj.optString("ip", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                ?: obj.optString("query", "").trim().takeIf { it.isNotEmpty() && it != "null" }
            if (ipVal != null) {
                pairs += "IP" to ipVal
            }

            val rawCountry = obj.optString("country", "").trim().takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
            val rawCode = (
                obj.optString("country_code", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                    ?: obj.optString("countryCode", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                    ?: rawCountry.takeIf { it.length == 2 && it[0].isLetter() && it[1].isLetter() }
                ).orEmpty().uppercase()
            val flag = countryCodeToFlagEmoji(rawCode)
            val countryText = when {
                rawCountry.isNotEmpty() && flag.isNotEmpty() -> "$flag $rawCountry"
                rawCountry.isNotEmpty() -> rawCountry
                rawCode.isNotEmpty() && flag.isNotEmpty() -> "$flag $rawCode"
                rawCode.isNotEmpty() -> rawCode
                else -> ""
            }
            val countryLabelOnly = rawCountry.ifEmpty { rawCode }

            if (countryText.isNotEmpty()) {
                pairs += "Country" to countryText
            }
            if (rawCode.isNotEmpty()) {
                pairs += "Code" to rawCode
            }

            val remainingKeys = listOf(
                "region" to "Region",
                "city" to "City",
                "isp" to "ISP",
                "organization" to "Organization",
                "org" to "Org",
                "asn" to "ASN",
                "asn_organization" to "ASN Org",
                "timezone" to "Timezone"
            )
            remainingKeys.forEach { (key, label) ->
                val v = obj.optString(key, "").trim()
                if (v.isNotEmpty() && v != "null") {
                    pairs += label to v
                }
            }
            return ParsedConnectionInfo(
                summaryLines = pairs,
                countryFlag = flag,
                countryLabel = countryLabelOnly
            )
        }
    }

    fun importFromClipboard() {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString().orEmpty()
        if (text.isBlank()) {
            _snackbarEvents.tryEmit("Clipboard is empty")
            return
        }
        importBatchText(text)
    }

    fun importBatchText(rawText: String, subscriptionId: String = "") {
        viewModelScope.launch {
            val targetGroup = if (subscriptionId.isNotEmpty()) subscriptionId else {
                val cur = repository.getSettingsSnapshot().selectedGroupId
                if (cur == AppSettings.GROUP_ALL) "" else cur
            }
            val count = repository.importBatchText(rawText, targetGroup)
            if (count > 0) {
                _snackbarEvents.tryEmit("Imported $count configuration(s)")
            } else {
                _snackbarEvents.tryEmit("No valid V2Ray/Xray configuration found")
            }
        }
    }

    fun exportAllVisibleToClipboard() {
        viewModelScope.launch {
            val list = homeUiState.value.visibleServers
            if (list.isEmpty()) {
                _snackbarEvents.tryEmit("No configurations to export")
                return@launch
            }
            val joined = list.joinToString("\n") { ConfigParser.toShareUri(it) }
            copyToClipboard("PalaNG Configs", joined)
            _snackbarEvents.tryEmit("Copied ${list.size} configuration(s) to clipboard")
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    suspend fun getServerById(id: String): ServerConfig? = repository.getServerById(id)

    fun saveServer(config: ServerConfig) {
        viewModelScope.launch {
            repository.saveServer(config)
            _snackbarEvents.tryEmit("Saved ${config.remarks}")
            if (isTunnelActive() && VpnStateHolder.activeServerId.value == config.id) {
                _pendingTunnelRestart.value = false
                XrayVpnService.start(app, config.id)
                _snackbarEvents.tryEmit("Reconnecting ${config.remarks}…")
            }
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            repository.deleteServer(id)
            _snackbarEvents.tryEmit("Configuration removed")
        }
    }

    fun deleteAllInCurrentGroup() {
        viewModelScope.launch {
            val groupId = homeUiState.value.settings.selectedGroupId
            repository.deleteServersInGroup(groupId)
            _snackbarEvents.tryEmit("Cleared configurations in group")
        }
    }

    fun removeDuplicates() {
        viewModelScope.launch {
            val groupId = homeUiState.value.settings.selectedGroupId
            val removed = repository.removeDuplicates(groupId)
            _snackbarEvents.tryEmit("Removed $removed duplicate configuration(s)")
        }
    }

    fun removeInvalidConfigs() {
        viewModelScope.launch {
            val removed = repository.removeInvalidTested()
            _snackbarEvents.tryEmit("Removed $removed invalid configuration(s)")
        }
    }

    fun sortByTestResults() {
        viewModelScope.launch {
            repository.sortServersByDelay()
            _snackbarEvents.tryEmit("Sorted servers by latency")
        }
    }

    fun testSingleServerDelay(server: ServerConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            if (server.protocol != ProtocolType.AMIR) {
                AssetHelper.ensureCoreAssets(app)
            }
            val settings = repository.getSettingsSnapshot()
            val delayMs = measureServerDelayInternal(server, settings)
            repository.updateLatency(server.id, delayMs)
        }
    }

    private suspend fun measureServerDelayInternal(server: ServerConfig, settings: AppSettings): Long {
        if (server.protocol == ProtocolType.AMIR) {
            val proxy = AmirLocalProxy(serverConfig = server, settings = settings)
            return try {
                proxy.measureDelay(settings.delayTestUrl)
            } finally {
                runCatching { proxy.stop() }
            }
        }
        val configJson = XrayConfigBuilder.buildOutboundDelayTestJson(server, settings)
        return AssetHelper.measureOutboundDelay(app, configJson, settings.delayTestUrl)
    }

    fun testAllVisibleDelay() {
        if (_isTestingBatch.value) {
            batchTestJob?.cancel()
            _isTestingBatch.value = false
            _batchProgress.value = null
            return
        }
        val targets = homeUiState.value.visibleServers.filter { it.protocol != ProtocolType.CUSTOM }
        if (targets.isEmpty()) {
            _snackbarEvents.tryEmit("No testable servers in current view")
            return
        }
        batchTestJob = viewModelScope.launch(Dispatchers.IO) {
            if (targets.any { it.protocol != ProtocolType.AMIR }) {
                AssetHelper.ensureCoreAssets(app)
            }
            _isTestingBatch.value = true
            _batchProgress.value = 0 to targets.size
            val settings = repository.getSettingsSnapshot()
            val concurrency = settings.concurrentDelayTests.coerceIn(1, 64)
            val semaphore = Semaphore(concurrency)
            var done = 0
            try {
                targets.map { server ->
                    async {
                        semaphore.withPermit {
                            val delayMs = measureServerDelayInternal(server, settings)
                            repository.updateLatency(server.id, delayMs)
                            synchronized(this@MainViewModel) {
                                done++
                                _batchProgress.value = done to targets.size
                            }
                        }
                    }
                }.awaitAll()
                _snackbarEvents.tryEmit("Delay test finished for ${targets.size} server(s)")
            } finally {
                _isTestingBatch.value = false
                _batchProgress.value = null
            }
        }
    }

    fun saveSubscription(group: SubscriptionGroup) {
        viewModelScope.launch {
            repository.saveSubscription(group)
        }
    }

    fun deleteSubscription(groupId: String, deleteConfigs: Boolean) {
        viewModelScope.launch {
            repository.deleteSubscription(groupId, deleteConfigs)
            _snackbarEvents.tryEmit("Subscription deleted")
        }
    }

    fun updateSubscription(group: SubscriptionGroup) {
        viewModelScope.launch {
            _isUpdatingSubs.value = true
            val result = repository.updateSubscription(group)
            _isUpdatingSubs.value = false
            result.onSuccess { count ->
                _snackbarEvents.tryEmit("Updated ${group.remarks}: $count server(s)")
            }.onFailure { err ->
                _snackbarEvents.tryEmit("Update failed (${group.remarks}): ${err.message}")
            }
        }
    }

    fun updateAllSubscriptions() {
        viewModelScope.launch {
            val enabledSubs = subscriptionsFlow.value.filter { it.enabled }
            if (enabledSubs.isEmpty()) {
                _snackbarEvents.tryEmit("No enabled subscription groups")
                return@launch
            }
            _isUpdatingSubs.value = true
            var totalConfigs = 0
            var successCount = 0
            enabledSubs.forEach { group ->
                repository.updateSubscription(group).onSuccess { count ->
                    totalConfigs += count
                    successCount++
                }
            }
            _isUpdatingSubs.value = false
            _snackbarEvents.tryEmit("Updated $successCount/${enabledSubs.size} subscription(s), $totalConfigs server(s)")
        }
    }

    fun saveRoutingRule(rule: RoutingRule) {
        viewModelScope.launch {
            repository.saveRoutingRule(rule)
            if (isTunnelActive()) {
                _pendingTunnelRestart.value = true
            }
        }
    }

    fun deleteRoutingRule(id: String) {
        viewModelScope.launch {
            repository.deleteRoutingRule(id)
            if (isTunnelActive()) {
                _pendingTunnelRestart.value = true
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            var tunnelChanged = false
            val activeId = VpnStateHolder.activeServerId.value
            val activeIsAmir = activeId.isNotBlank() && repository.getServerById(activeId)?.protocol == ProtocolType.AMIR
            repository.updateSettings { current ->
                val updated = transform(current)
                tunnelChanged = if (activeIsAmir) {
                    AppSettings.affectsActiveAmirProxy(current, updated)
                } else {
                    AppSettings.affectsActiveTunnel(current, updated)
                }
                updated
            }
            if (tunnelChanged && isTunnelActive()) {
                _pendingTunnelRestart.value = true
            }
        }
    }

    fun clearLogs() {
        VpnStateHolder.clearLogs()
    }

    fun clearVpnError() {
        VpnStateHolder.setError(null)
    }

    fun refreshAssetInfos() {
        viewModelScope.launch(Dispatchers.IO) {
            _assetInfos.value = AssetHelper.getAssetInfos(app)
        }
    }

    fun restoreBundledAssets() {
        viewModelScope.launch(Dispatchers.IO) {
            AssetHelper.ensureCoreAssets(app, forceOverwrite = true)
            _assetInfos.value = AssetHelper.getAssetInfos(app)
            _snackbarEvents.tryEmit("Restored bundled GeoIP & Geosite databases")
        }
    }

    fun downloadAssetUpdate(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _updatingAssetName.value = fileName
            val res = AssetHelper.downloadAsset(app, fileName)
            _updatingAssetName.value = null
            _assetInfos.value = AssetHelper.getAssetInfos(app)
            res.onSuccess { size ->
                _snackbarEvents.tryEmit("Updated $fileName (${XrayVpnService.formatBytes(size)})")
            }.onFailure { err ->
                _snackbarEvents.tryEmit("Failed updating $fileName: ${err.message}")
            }
        }
    }

    fun getCoreVersion(): String = repository.getCoreVersion()
}

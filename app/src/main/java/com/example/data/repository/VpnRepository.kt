package com.example.data.repository

import android.content.Context
import com.example.core.AssetHelper
import com.example.core.ConfigParser
import com.example.data.local.RoutingRuleDao
import com.example.data.local.ServerConfigDao
import com.example.data.local.SettingsDataStore
import com.example.data.local.SubscriptionGroupDao
import com.example.data.model.AppSettings
import com.example.data.model.RoutingRule
import com.example.data.model.ServerConfig
import com.example.data.model.SubscriptionGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VpnRepository(
    private val context: Context,
    private val serverDao: ServerConfigDao,
    private val subscriptionDao: SubscriptionGroupDao,
    private val routingRuleDao: RoutingRuleDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val serversFlow: Flow<List<ServerConfig>> = serverDao.observeAll()
    val subscriptionsFlow: Flow<List<SubscriptionGroup>> = subscriptionDao.observeAll()
    val routingRulesFlow: Flow<List<RoutingRule>> = routingRuleDao.observeAll()
    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow

    suspend fun getSettingsSnapshot(): AppSettings = settingsDataStore.getSnapshot()

    suspend fun getAllServers(): List<ServerConfig> = serverDao.getAll()

    suspend fun getServerById(id: String): ServerConfig? = serverDao.getById(id)

    suspend fun getRoutingRules(): List<RoutingRule> = routingRuleDao.getAll()

    suspend fun selectServer(id: String) {
        settingsDataStore.updateSelectedServerId(id)
    }

    suspend fun selectGroup(groupId: String) {
        settingsDataStore.updateSelectedGroupId(groupId)
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsDataStore.updateSettings(transform)
    }

    suspend fun saveServer(config: ServerConfig) {
        val existing = serverDao.getById(config.id)
        if (existing == null) {
            val maxOrder = (serverDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
            val inserted = config.copy(sortOrder = maxOrder)
            serverDao.insert(inserted)
            if (getSettingsSnapshot().selectedServerId.isBlank()) {
                selectServer(inserted.id)
            }
        } else {
            serverDao.update(config)
        }
    }

    suspend fun importBatchText(rawText: String, subscriptionId: String = ""): Int {
        val parsed = ConfigParser.parseBatch(rawText, subscriptionId)
        if (parsed.isEmpty()) return 0
        val currentCount = serverDao.getAll().size
        val toInsert = parsed.mapIndexed { idx, cfg ->
            cfg.copy(sortOrder = currentCount + idx)
        }
        serverDao.insertAll(toInsert)
        if (getSettingsSnapshot().selectedServerId.isBlank()) {
            selectServer(toInsert.first().id)
        }
        return toInsert.size
    }

    suspend fun deleteServer(id: String) {
        serverDao.deleteById(id)
        val settings = getSettingsSnapshot()
        if (settings.selectedServerId == id) {
            val next = serverDao.getAll().firstOrNull()?.id ?: ""
            selectServer(next)
        }
    }

    suspend fun deleteServersInGroup(groupId: String) {
        if (groupId == AppSettings.GROUP_ALL) {
            serverDao.deleteAll()
            selectServer("")
        } else {
            serverDao.deleteBySubscription(groupId)
            val all = serverDao.getAll()
            val selected = getSettingsSnapshot().selectedServerId
            if (all.none { it.id == selected }) {
                selectServer(all.firstOrNull()?.id ?: "")
            }
        }
    }

    suspend fun updateLatency(id: String, latencyMs: Long) {
        serverDao.updateLatency(id, latencyMs)
    }

    suspend fun sortServersByDelay() {
        val all = serverDao.getAll()
        val sorted = all.sortedWith(
            compareBy<ServerConfig>(
                { if (it.latencyMs >= 0) 0 else if (it.latencyMs == ServerConfig.UNTESTED_LATENCY) 1 else 2 },
                { if (it.latencyMs >= 0) it.latencyMs else Long.MAX_VALUE },
                { it.remarks.lowercase() }
            )
        )
        sorted.forEachIndexed { idx, cfg ->
            serverDao.updateSortOrder(cfg.id, idx)
        }
    }

    suspend fun removeDuplicates(groupId: String): Int {
        val list = if (groupId == AppSettings.GROUP_ALL) serverDao.getAll() else serverDao.getBySubscription(groupId)
        val seen = HashSet<String>()
        var removed = 0
        list.forEach { cfg ->
            val fp = cfg.uniqueFingerprint()
            if (!seen.add(fp)) {
                serverDao.deleteById(cfg.id)
                removed++
            }
        }
        return removed
    }

    suspend fun removeInvalidTested(): Int {
        val before = serverDao.getAll().size
        serverDao.deleteInvalidTested()
        val after = serverDao.getAll().size
        return (before - after).coerceAtLeast(0)
    }

    suspend fun saveSubscription(group: SubscriptionGroup) {
        subscriptionDao.insert(group)
    }

    suspend fun deleteSubscription(groupId: String, alsoDeleteConfigs: Boolean) {
        subscriptionDao.deleteById(groupId)
        if (alsoDeleteConfigs) {
            serverDao.deleteBySubscription(groupId)
        }
        val settings = getSettingsSnapshot()
        if (settings.selectedGroupId == groupId) {
            selectGroup(AppSettings.GROUP_ALL)
        }
    }

    suspend fun updateSubscription(group: SubscriptionGroup): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(group.url.trim())
                .header("User-Agent", group.userAgent.ifBlank { "v2rayNG/1.10.0" })
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string() ?: error("Empty response")
                val parsed = ConfigParser.parseBatch(body, group.id)
                if (parsed.isEmpty()) {
                    error("No valid configurations found in subscription")
                }

                val subInfo = ConfigParser.parseSubscriptionUserInfo(response.header("subscription-userinfo"))
                val updatedGroup = group.copy(
                    lastUpdatedAt = System.currentTimeMillis(),
                    uploadBytes = subInfo?.uploadBytes ?: group.uploadBytes,
                    downloadBytes = subInfo?.downloadBytes ?: group.downloadBytes,
                    totalBytes = subInfo?.totalBytes ?: group.totalBytes,
                    expireTimestamp = subInfo?.expireTimestamp ?: group.expireTimestamp
                )
                subscriptionDao.update(updatedGroup)

                val previousSelected = getSettingsSnapshot().selectedServerId
                val oldGroupServers = serverDao.getBySubscription(group.id)
                val wasSelectedInGroup = oldGroupServers.any { it.id == previousSelected }

                serverDao.deleteBySubscription(group.id)
                val numbered = parsed.mapIndexed { idx, cfg -> cfg.copy(sortOrder = idx) }
                serverDao.insertAll(numbered)

                if (wasSelectedInGroup || previousSelected.isBlank()) {
                    selectServer(numbered.first().id)
                }
                numbered.size
            }
        }
    }

    suspend fun ensureDefaultRoutingRules() {
        if (routingRuleDao.getAll().isNotEmpty()) return
        routingRuleDao.insertAll(
            listOf(
                RoutingRule(
                    remarks = "Block Ads (geosite)",
                    enabled = false,
                    outboundTag = "block",
                    domains = "geosite:category-ads-all",
                    sortOrder = 0
                ),
                RoutingRule(
                    remarks = "Bypass Private LAN",
                    enabled = true,
                    outboundTag = "direct",
                    ips = "geoip:private",
                    sortOrder = 1
                ),
                RoutingRule(
                    remarks = "Bypass Iran Domains & IPs",
                    enabled = false,
                    outboundTag = "direct",
                    domains = "geosite:ir,regexp:.*\\.ir$",
                    ips = "geoip:ir",
                    sortOrder = 2
                )
            )
        )
    }

    suspend fun saveRoutingRule(rule: RoutingRule) {
        routingRuleDao.insert(rule)
    }

    suspend fun deleteRoutingRule(id: String) {
        routingRuleDao.deleteById(id)
    }

    fun getCoreVersion(): String = AssetHelper.getCoreVersion(context)
}

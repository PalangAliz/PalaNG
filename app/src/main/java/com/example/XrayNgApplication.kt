package com.example

import android.app.Application
import com.example.core.AssetHelper
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.repository.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XrayNgApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }
    val repository: VpnRepository by lazy {
        VpnRepository(
            context = this,
            serverDao = database.serverConfigDao(),
            subscriptionDao = database.subscriptionGroupDao(),
            routingRuleDao = database.routingRuleDao(),
            settingsDataStore = settingsDataStore
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            AssetHelper.ensureCoreAssets(this@XrayNgApplication)
            repository.ensureDefaultRoutingRules()
        }
    }
}

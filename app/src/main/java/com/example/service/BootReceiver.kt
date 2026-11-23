package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.XrayNgApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? XrayNgApplication ?: return@launch
                val settings = app.repository.getSettingsSnapshot()
                if (!settings.autoStartOnBoot) return@launch
                val targetId = settings.selectedServerId.ifBlank {
                    app.repository.getAllServers().firstOrNull()?.id.orEmpty()
                }
                if (targetId.isBlank()) return@launch
                val canStart = !settings.vpnMode || VpnService.prepare(appContext) == null
                if (canStart) {
                    runCatching {
                        XrayVpnService.start(appContext, targetId)
                    }.onFailure { err ->
                        VpnStateHolder.appendLog("Auto-start on boot blocked by system: ${err.message}")
                    }
                } else {
                    VpnStateHolder.appendLog("Auto-start on boot skipped: VPN consent required")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

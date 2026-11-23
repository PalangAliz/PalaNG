package com.example.service

import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.XrayNgApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class XrayTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = scope.launch {
            VpnStateHolder.connectionState.collect { state ->
                val tile = qsTile ?: return@collect
                when (state) {
                    ConnectionState.CONNECTED -> {
                        tile.state = Tile.STATE_ACTIVE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = VpnStateHolder.activeServerRemarks.value.ifBlank { "Connected" }
                        }
                    }
                    ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                        tile.state = Tile.STATE_UNAVAILABLE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = "Connecting…"
                        }
                    }
                    ConnectionState.DISCONNECTED -> {
                        tile.state = Tile.STATE_INACTIVE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = "Off"
                        }
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        super.onStopListening()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val state = VpnStateHolder.connectionState.value
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            XrayVpnService.stop(this)
        } else {
            scope.launch {
                val app = application as XrayNgApplication
                val settings = app.repository.getSettingsSnapshot()
                val selectedId = settings.selectedServerId
                val server = app.repository.getServerById(selectedId)
                    ?: app.repository.getAllServers().firstOrNull()
                val needsVpnConsent = settings.vpnMode && server?.protocol != com.example.data.model.ProtocolType.AMIR
                val prepareIntent = if (needsVpnConsent) VpnService.prepare(this@XrayTileService) else null
                if (prepareIntent == null) {
                    XrayVpnService.start(this@XrayTileService, selectedId)
                } else {
                    // Need user VPN consent inside MainActivity
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val pi = android.app.PendingIntent.getActivity(
                                this@XrayTileService, 0, launchIntent, android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            startActivityAndCollapse(pi)
                        } else {
                            @Suppress("DEPRECATION")
                            startActivityAndCollapse(launchIntent)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

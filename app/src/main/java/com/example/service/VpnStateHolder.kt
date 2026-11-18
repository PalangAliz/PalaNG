package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class TrafficStats(
    val uplinkSpeedBps: Long = 0L,
    val downlinkSpeedBps: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val connectedSinceMs: Long = 0L
)

data class LogEntry(
    val timestamp: String,
    val message: String
)

object VpnStateHolder {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _activeServerId = MutableStateFlow("")
    val activeServerId: StateFlow<String> = _activeServerId.asStateFlow()

    private val _activeServerRemarks = MutableStateFlow("")
    val activeServerRemarks: StateFlow<String> = _activeServerRemarks.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private val _lastTestedDelayMs = MutableStateFlow<Long?>(null)
    val lastTestedDelayMs: StateFlow<Long?> = _lastTestedDelayMs.asStateFlow()

    private val _isTestingDelay = MutableStateFlow(false)
    val isTestingDelay: StateFlow<Boolean> = _isTestingDelay.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setState(state: ConnectionState, serverId: String = _activeServerId.value, remarks: String = _activeServerRemarks.value) {
        _connectionState.value = state
        _activeServerId.value = serverId
        _activeServerRemarks.value = remarks
        if (state == ConnectionState.DISCONNECTED) {
            _trafficStats.value = TrafficStats()
            _lastTestedDelayMs.value = null
            _isTestingDelay.value = false
        } else if (state == ConnectionState.CONNECTED && _trafficStats.value.connectedSinceMs == 0L) {
            _trafficStats.update { it.copy(connectedSinceMs = System.currentTimeMillis()) }
        }
    }

    fun updateTraffic(uplinkDelta: Long, downlinkDelta: Long) {
        _trafficStats.update { current ->
            current.copy(
                uplinkSpeedBps = uplinkDelta.coerceAtLeast(0L),
                downlinkSpeedBps = downlinkDelta.coerceAtLeast(0L),
                totalUploadBytes = current.totalUploadBytes + uplinkDelta.coerceAtLeast(0L),
                totalDownloadBytes = current.totalDownloadBytes + downlinkDelta.coerceAtLeast(0L)
            )
        }
    }

    fun setTestingDelay(testing: Boolean) {
        _isTestingDelay.value = testing
    }

    fun setLastTestedDelay(delayMs: Long?) {
        _lastTestedDelayMs.value = delayMs
        _isTestingDelay.value = false
    }

    fun setError(message: String?) {
        _errorMessage.value = message
    }

    fun appendLog(message: String) {
        if (message.isBlank()) return
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            message = message.trim()
        )
        _logs.update { current ->
            (current + entry).takeLast(400)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

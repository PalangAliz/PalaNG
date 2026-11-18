package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "server_configs")
data class ServerConfig(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subscriptionId: String = "",
    val remarks: String = "Server",
    val protocol: ProtocolType = ProtocolType.VLESS,
    val address: String = "",
    val port: Int = 443,
    val passwordOrId: String = "",
    val security: String = "auto",
    val alterId: Int = 0,
    val flow: String = "",
    val network: String = "tcp",
    val headerType: String = "none",
    val host: String = "",
    val path: String = "",
    val grpcMode: String = "gun",
    val streamSecurity: String = "",
    val sni: String = "",
    val fingerprint: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val extraParam1: String = "",
    val extraParam2: String = "",
    val extraParam3: String = "",
    val rawCustomJson: String = "",
    val latencyMs: Long = UNTESTED_LATENCY,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun uniqueFingerprint(): String {
        return if (protocol == ProtocolType.CUSTOM) {
            "CUSTOM:${rawCustomJson.hashCode()}"
        } else {
            "${protocol.name}|$address|$port|$passwordOrId|$network|$streamSecurity|$sni|$path|$host|$publicKey|$shortId"
        }
    }

    fun maskedAddress(): String {
        if (protocol == ProtocolType.CUSTOM) return "Custom Xray JSON"
        if (protocol == ProtocolType.AMIR) {
            val clean = extraParam2.trim()
            val worker = address.ifBlank { extraParam1 }
            return if (clean.isNotBlank()) "$worker (CF: $clean)" else worker
        }
        val hostPart = if (address.contains(":")) "[$address]" else address
        return "$hostPart:$port"
    }

    fun transportSummary(): String {
        if (protocol == ProtocolType.CUSTOM) return "JSON"
        if (protocol == ProtocolType.WIREGUARD) return "WG • UDP"
        if (protocol == ProtocolType.HYSTERIA2) return "QUIC • TLS"
        if (protocol == ProtocolType.AMIR) return "WSS • chat.v2"
        val net = network.ifBlank { "tcp" }.uppercase()
        val sec = when (streamSecurity.lowercase()) {
            "reality" -> "REALITY"
            "tls" -> "TLS"
            else -> "NONE"
        }
        return "$net • $sec"
    }

    companion object {
        const val UNTESTED_LATENCY = -2L
        const val ERROR_LATENCY = -1L
    }
}

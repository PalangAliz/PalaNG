package com.example.data.model

enum class ProtocolType(val displayName: String, val scheme: String) {
    VMESS("VMess", "vmess"),
    VLESS("VLESS", "vless"),
    TROJAN("Trojan", "trojan"),
    SHADOWSOCKS("Shadowsocks", "ss"),
    SOCKS("SOCKS", "socks"),
    HTTP("HTTP", "http"),
    WIREGUARD("WireGuard", "wireguard"),
    HYSTERIA2("Hysteria2", "hysteria2"),
    AMIR("Amir", "amir"),
    CUSTOM("Custom", "custom");

    companion object {
        fun fromScheme(scheme: String): ProtocolType? = when (scheme.lowercase()) {
            "vmess" -> VMESS
            "vless" -> VLESS
            "trojan" -> TROJAN
            "ss" -> SHADOWSOCKS
            "socks", "socks5" -> SOCKS
            "http", "https" -> HTTP
            "wireguard", "wg" -> WIREGUARD
            "hysteria2", "hy2" -> HYSTERIA2
            "amir" -> AMIR
            else -> null
        }
    }
}

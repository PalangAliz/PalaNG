package com.example.core

import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.RoutingMode
import com.example.data.model.RoutingRule
import com.example.data.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    fun buildFullConfig(
        server: ServerConfig,
        settings: AppSettings,
        customRules: List<RoutingRule>,
        amirLocalPort: Int = 0
    ): String {
        if (server.protocol == ProtocolType.CUSTOM && server.rawCustomJson.isNotBlank()) {
            return buildCustomConfigWithInjectedSettings(server.rawCustomJson, settings)
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", settings.logLevel))
        root.put("stats", JSONObject())
        root.put("policy", buildPolicy())
        root.put("inbounds", buildInbounds(settings))
        root.put("outbounds", buildOutbounds(server, settings, amirLocalPort))
        if (settings.localDnsEnabled || settings.fakeDnsEnabled) {
            root.put("dns", buildDns(settings))
        }
        root.put("routing", buildRouting(settings, customRules))
        if (settings.fakeDnsEnabled) {
            root.put(
                "fakedns",
                JSONArray().put(
                    JSONObject()
                        .put("ipPool", "198.18.0.0/15")
                        .put("poolSize", 65535)
                )
            )
        }
        return root.toString(2)
    }

    fun buildOutboundDelayTestJson(server: ServerConfig, settings: AppSettings): String {
        if (server.protocol == ProtocolType.CUSTOM && server.rawCustomJson.isNotBlank()) {
            return server.rawCustomJson
        }
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "none"))
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(server, settings))
        if (settings.fragmentEnabled) {
            outbounds.put(buildFragmentOutbound(settings))
        }
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        root.put("outbounds", outbounds)
        return root.toString()
    }

    private fun buildCustomConfigWithInjectedSettings(rawJson: String, settings: AppSettings): String {
        return runCatching {
            val root = JSONObject(rawJson)
            if (!root.has("stats")) root.put("stats", JSONObject())
            if (!root.has("policy")) root.put("policy", buildPolicy())
            if (settings.vpnMode) {
                val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
                var hasTun = false
                for (i in 0 until inbounds.length()) {
                    if (inbounds.optJSONObject(i)?.optString("protocol") == "tun") {
                        hasTun = true
                        break
                    }
                }
                if (!hasTun) {
                    inbounds.put(buildTunInbound(settings))
                    root.put("inbounds", inbounds)
                }
            }
            root.toString(2)
        }.getOrElse { rawJson }
    }

    private fun buildPolicy(): JSONObject {
        return JSONObject().apply {
            put(
                "levels",
                JSONObject().put(
                    "8",
                    JSONObject()
                        .put("connIdle", 300)
                        .put("downlinkOnly", 1)
                        .put("handshake", 4)
                        .put("uplinkOnly", 1)
                )
            )
            put(
                "system",
                JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true)
            )
        }
    }

    private fun buildInbounds(settings: AppSettings): JSONArray {
        val inbounds = JSONArray()
        val listenHost = if (settings.allowLan) "0.0.0.0" else "127.0.0.1"
        val sniffing = buildSniffing(settings)

        if (settings.vpnMode) {
            inbounds.put(buildTunInbound(settings))
        }

        inbounds.put(
            JSONObject().apply {
                put("tag", "socks")
                put("port", settings.socksPort)
                put("listen", listenHost)
                put("protocol", "socks")
                put("settings", JSONObject().put("auth", "noauth").put("udp", true).put("userLevel", 8))
                put("sniffing", sniffing)
            }
        )

        inbounds.put(
            JSONObject().apply {
                put("tag", "http")
                put("port", settings.httpPort)
                put("listen", listenHost)
                put("protocol", "http")
                put("settings", JSONObject().put("userLevel", 8))
                put("sniffing", sniffing)
            }
        )
        return inbounds
    }

    private fun buildTunInbound(settings: AppSettings): JSONObject {
        return JSONObject().apply {
            put("tag", "tun")
            put("port", 0)
            put("protocol", "tun")
            put(
                "settings",
                JSONObject()
                    .put("name", "xray0")
                    .put("MTU", 1500)
                    .put("userLevel", 8)
            )
            put("sniffing", buildSniffing(settings))
        }
    }

    private fun buildSniffing(settings: AppSettings): JSONObject {
        val destOverride = JSONArray().apply {
            put("http")
            put("tls")
            put("quic")
            if (settings.fakeDnsEnabled) put("fakedns")
        }
        return JSONObject().apply {
            put("enabled", settings.sniffingEnabled)
            put("destOverride", destOverride)
            put("routeOnly", settings.routeOnly)
        }
    }

    private fun buildOutbounds(
        server: ServerConfig,
        settings: AppSettings,
        amirLocalPort: Int = 0
    ): JSONArray {
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(server, settings, amirLocalPort))
        if (settings.fragmentEnabled && server.protocol != ProtocolType.WIREGUARD && server.protocol != ProtocolType.AMIR) {
            outbounds.put(buildFragmentOutbound(settings))
        }
        outbounds.put(
            JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject().put("domainStrategy", "UseIP"))
            }
        )
        outbounds.put(
            JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject().put("response", JSONObject().put("type", "http")))
            }
        )
        outbounds.put(
            JSONObject().apply {
                put("tag", "dns-out")
                put("protocol", "dns")
            }
        )
        return outbounds
    }

    private fun buildFragmentOutbound(settings: AppSettings): JSONObject {
        return JSONObject().apply {
            put("tag", "fragment")
            put("protocol", "freedom")
            put(
                "settings",
                JSONObject().put(
                    "fragment",
                    JSONObject()
                        .put("packets", settings.fragmentPackets.ifBlank { "tlshello" })
                        .put("length", settings.fragmentLength.ifBlank { "100-200" })
                        .put("interval", settings.fragmentInterval.ifBlank { "10-20" })
                )
            )
        }
    }

    private fun buildProxyOutbound(
        server: ServerConfig,
        settings: AppSettings,
        amirLocalPort: Int = 0
    ): JSONObject {
        val outbound = JSONObject().apply {
            put("tag", "proxy")
        }

        when (server.protocol) {
            ProtocolType.VMESS -> {
                outbound.put("protocol", "vmess")
                val user = JSONObject()
                    .put("id", server.passwordOrId)
                    .put("alterId", server.alterId)
                    .put("security", server.security.ifBlank { "auto" })
                    .put("level", 8)
                val vnext = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("users", JSONArray().put(user))
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
                outbound.put("streamSettings", buildStreamSettings(server, settings))
            }

            ProtocolType.VLESS -> {
                outbound.put("protocol", "vless")
                val user = JSONObject()
                    .put("id", server.passwordOrId)
                    .put("encryption", server.security.ifBlank { "none" })
                    .put("level", 8)
                if (server.flow.isNotBlank()) user.put("flow", server.flow)
                val vnext = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("users", JSONArray().put(user))
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
                outbound.put("streamSettings", buildStreamSettings(server, settings))
            }

            ProtocolType.TROJAN -> {
                outbound.put("protocol", "trojan")
                val srv = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("password", server.passwordOrId)
                    .put("level", 8)
                if (server.flow.isNotBlank()) srv.put("flow", server.flow)
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srv)))
                outbound.put("streamSettings", buildStreamSettings(server, settings))
            }

            ProtocolType.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                val srv = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("password", server.passwordOrId)
                    .put("method", server.security.ifBlank { "aes-256-gcm" })
                    .put("level", 8)
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srv)))
                outbound.put("streamSettings", buildStreamSettings(server, settings))
            }

            ProtocolType.SOCKS -> {
                outbound.put("protocol", "socks")
                val srv = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                if (server.extraParam1.isNotBlank() || server.passwordOrId.isNotBlank()) {
                    srv.put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("user", server.extraParam1)
                                .put("pass", server.passwordOrId)
                                .put("level", 8)
                        )
                    )
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srv)))
            }

            ProtocolType.HTTP -> {
                outbound.put("protocol", "http")
                val srv = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                if (server.extraParam1.isNotBlank() || server.passwordOrId.isNotBlank()) {
                    srv.put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("user", server.extraParam1)
                                .put("pass", server.passwordOrId)
                                .put("level", 8)
                        )
                    )
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srv)))
            }

            ProtocolType.WIREGUARD -> {
                outbound.put("protocol", "wireguard")
                val addresses = JSONArray()
                server.extraParam1.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    addresses.put(it)
                }
                if (addresses.length() == 0) addresses.put("10.0.0.2/32")

                val peer = JSONObject()
                    .put("endpoint", "${server.address}:${server.port}")
                    .put("publicKey", server.publicKey)
                val wgSettings = JSONObject()
                    .put("secretKey", server.passwordOrId)
                    .put("address", addresses)
                    .put("peers", JSONArray().put(peer))
                    .put("mtu", server.extraParam2.toIntOrNull() ?: 1420)
                if (server.extraParam3.isNotBlank()) {
                    val reservedArr = JSONArray()
                    server.extraParam3.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach {
                        reservedArr.put(it)
                    }
                    if (reservedArr.length() > 0) wgSettings.put("reserved", reservedArr)
                }
                outbound.put("settings", wgSettings)
            }

            ProtocolType.HYSTERIA2 -> {
                outbound.put("protocol", "hysteria")
                val srv = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("level", 8)
                outbound.put("settings", JSONObject().put("version", 2).put("servers", JSONArray().put(srv)))

                val hysteriaTransport = JSONObject()
                    .put("version", 2)
                    .put("auth", server.passwordOrId)
                if (server.extraParam1.isNotBlank()) {
                    hysteriaTransport.put(
                        "udphop",
                        JSONObject().put("port", server.extraParam3)
                    )
                }

                val stream = JSONObject()
                    .put("network", "hysteria")
                    .put("security", "tls")
                    .put("hysteriaSettings", hysteriaTransport)
                    .put("tlsSettings", buildTlsSettings(server))
                outbound.put("streamSettings", stream)
            }

            ProtocolType.AMIR -> {
                outbound.put("protocol", "socks")
                val targetPort = if (amirLocalPort > 0) amirLocalPort else 40446
                val srv = JSONObject()
                    .put("address", "127.0.0.1")
                    .put("port", targetPort)
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srv)))
            }

            ProtocolType.CUSTOM -> {
                outbound.put("protocol", "freedom")
            }
        }

        if (settings.muxEnabled && server.flow.isBlank() &&
            server.protocol != ProtocolType.WIREGUARD &&
            server.protocol != ProtocolType.HYSTERIA2 &&
            server.protocol != ProtocolType.AMIR
        ) {
            outbound.put(
                "mux",
                JSONObject()
                    .put("enabled", true)
                    .put("concurrency", settings.muxConcurrency.coerceIn(1, 1024))
            )
        }

        return outbound
    }

    private fun buildStreamSettings(server: ServerConfig, settings: AppSettings): JSONObject {
        val network = server.network.ifBlank { "tcp" }.lowercase()
        val stream = JSONObject().put("network", network)

        val sec = server.streamSecurity.lowercase()
        if (sec == "tls") {
            stream.put("security", "tls")
            stream.put("tlsSettings", buildTlsSettings(server))
        } else if (sec == "reality") {
            stream.put("security", "reality")
            stream.put("realitySettings", buildRealitySettings(server))
        } else {
            stream.put("security", "none")
        }

        when (network) {
            "tcp" -> {
                if (server.headerType == "http") {
                    val reqHeaders = JSONObject()
                    if (server.host.isNotBlank()) {
                        val hosts = JSONArray()
                        server.host.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { hosts.put(it) }
                        reqHeaders.put("Host", hosts)
                    }
                    val paths = JSONArray()
                    val pList = server.path.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (pList.isEmpty()) paths.put("/") else pList.forEach { paths.put(it) }
                    stream.put(
                        "tcpSettings",
                        JSONObject().put(
                            "header",
                            JSONObject()
                                .put("type", "http")
                                .put(
                                    "request",
                                    JSONObject()
                                        .put("version", "1.1")
                                        .put("method", "GET")
                                        .put("path", paths)
                                        .put("headers", reqHeaders)
                                )
                        )
                    )
                }
            }

            "ws" -> {
                val ws = JSONObject().put("path", server.path.ifBlank { "/" })
                if (server.host.isNotBlank()) {
                    ws.put("host", server.host)
                    ws.put("headers", JSONObject().put("Host", server.host))
                }
                stream.put("wsSettings", ws)
            }

            "httpupgrade" -> {
                val hu = JSONObject().put("path", server.path.ifBlank { "/" })
                if (server.host.isNotBlank()) hu.put("host", server.host)
                stream.put("httpupgradeSettings", hu)
            }

            "xhttp", "splithttp" -> {
                val xhttp = JSONObject().put("path", server.path.ifBlank { "/" })
                if (server.host.isNotBlank()) xhttp.put("host", server.host)
                stream.put("xhttpSettings", xhttp)
            }

            "grpc" -> {
                val grpc = JSONObject()
                    .put("serviceName", server.path)
                    .put("multiMode", server.grpcMode == "multi")
                if (server.host.isNotBlank()) grpc.put("authority", server.host)
                stream.put("grpcSettings", grpc)
            }

            "h2", "http" -> {
                val h2 = JSONObject().put("path", server.path.ifBlank { "/" })
                if (server.host.isNotBlank()) {
                    val hosts = JSONArray()
                    server.host.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { hosts.put(it) }
                    h2.put("host", hosts)
                }
                stream.put("httpSettings", h2)
            }

            "kcp" -> {
                stream.put(
                    "kcpSettings",
                    JSONObject()
                        .put("mtu", 1350)
                        .put("tti", 50)
                        .put("header", JSONObject().put("type", server.headerType.ifBlank { "none" }))
                        .put("seed", server.path)
                )
            }
        }

        if (settings.fragmentEnabled && server.protocol != ProtocolType.WIREGUARD) {
            stream.put("sockopt", JSONObject().put("dialerProxy", "fragment"))
        }

        return stream
    }

    private fun buildTlsSettings(server: ServerConfig): JSONObject {
        return JSONObject().apply {
            put("allowInsecure", server.allowInsecure)
            val effectiveSni = server.sni.ifBlank { server.host }.ifBlank { server.address }
            if (effectiveSni.isNotBlank()) put("serverName", effectiveSni)
            if (server.fingerprint.isNotBlank()) put("fingerprint", server.fingerprint)
            if (server.alpn.isNotBlank()) {
                val alpnArr = JSONArray()
                server.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { alpnArr.put(it) }
                if (alpnArr.length() > 0) put("alpn", alpnArr)
            }
        }
    }

    private fun buildRealitySettings(server: ServerConfig): JSONObject {
        return JSONObject().apply {
            put("show", false)
            put("fingerprint", server.fingerprint.ifBlank { "chrome" })
            val effectiveSni = server.sni.ifBlank { server.host }
            if (effectiveSni.isNotBlank()) put("serverName", effectiveSni)
            put("publicKey", server.publicKey)
            put("shortId", server.shortId)
            put("spiderX", server.spiderX.ifBlank { "/" })
        }
    }

    private fun buildDns(settings: AppSettings): JSONObject {
        val servers = JSONArray()
        if (settings.fakeDnsEnabled) {
            servers.put("fakedns")
        }
        servers.put(settings.remoteDns.ifBlank { "https://1.1.1.1/dns-query" })
        val domestic = settings.domesticDns.ifBlank { "223.5.5.5" }
        when (settings.routingMode) {
            RoutingMode.BYPASS_LAN_AND_MAINLAND -> {
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put("domains", JSONArray().put("geosite:cn"))
                        .put("expectIPs", JSONArray().put("geoip:cn"))
                )
            }
            RoutingMode.BYPASS_LAN_AND_IR -> {
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put("domains", JSONArray().put("geosite:ir").put("regexp:.*\\.ir$"))
                        .put("expectIPs", JSONArray().put("geoip:ir"))
                )
            }
            else -> Unit
        }
        servers.put("localhost")
        return JSONObject().put("servers", servers)
    }

    private fun buildRouting(settings: AppSettings, customRules: List<RoutingRule>): JSONObject {
        val rules = JSONArray()

        // DNS hijack / resolution routing when Local DNS or FakeDNS is enabled
        if (settings.localDnsEnabled || settings.fakeDnsEnabled) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun").put("socks").put("http"))
                    .put("port", "53")
                    .put("outboundTag", "dns-out")
            )
        }

        when (settings.routingMode) {
            RoutingMode.BYPASS_LAN -> {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray().put("geoip:private"))
                        .put("outboundTag", "direct")
                )
            }
            RoutingMode.BYPASS_LAN_AND_MAINLAND -> {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray().put("geoip:private").put("geoip:cn"))
                        .put("outboundTag", "direct")
                )
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("domain", JSONArray().put("geosite:cn"))
                        .put("outboundTag", "direct")
                )
            }
            RoutingMode.BYPASS_LAN_AND_IR -> {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray().put("geoip:private").put("geoip:ir"))
                        .put("outboundTag", "direct")
                )
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("domain", JSONArray().put("geosite:ir").put("regexp:.*\\.ir$"))
                        .put("outboundTag", "direct")
                )
            }
            RoutingMode.GLOBAL_PROXY -> {
                // Only keep LAN bypass if explicitly enabled in settings
                if (settings.bypassLan) {
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("ip", JSONArray().put("geoip:private"))
                            .put("outboundTag", "direct")
                    )
                }
            }
            RoutingMode.CUSTOM -> {
                customRules.filter { it.enabled }.forEach { rule ->
                    val ruleObj = JSONObject()
                        .put("type", "field")
                        .put("outboundTag", rule.outboundTag)
                    val domainList = rule.domains.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val ipList = rule.ips.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (domainList.isNotEmpty()) {
                        val arr = JSONArray()
                        domainList.forEach { arr.put(it) }
                        ruleObj.put("domain", arr)
                    }
                    if (ipList.isNotEmpty()) {
                        val arr = JSONArray()
                        ipList.forEach { arr.put(it) }
                        ruleObj.put("ip", arr)
                    }
                    if (rule.port.isNotBlank()) ruleObj.put("port", rule.port.trim())
                    if (rule.network.isNotBlank()) ruleObj.put("network", rule.network.trim())
                    if (domainList.isNotEmpty() || ipList.isNotEmpty() || rule.port.isNotBlank()) {
                        rules.put(ruleObj)
                    }
                }
            }
        }

        // Default fallback rule to proxy
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("port", "0-65535")
                .put("outboundTag", "proxy")
        )

        return JSONObject()
            .put("domainStrategy", settings.domainStrategy.ifBlank { "IPIfNonMatch" })
            .put("rules", rules)
    }
}

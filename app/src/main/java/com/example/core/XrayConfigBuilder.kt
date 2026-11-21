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
        amirLocalPort: Int = 0,
        resolvedServerHosts: Map<String, List<String>> = emptyMap(),
        runtimeSocksPort: Int? = null,
        runtimeHttpPort: Int? = null
    ): String {
        if (server.protocol == ProtocolType.CUSTOM && server.rawCustomJson.isNotBlank()) {
            return buildCustomConfigWithInjectedSettings(server.rawCustomJson, settings, resolvedServerHosts)
        }
        if (server.protocol == ProtocolType.AMIR) {
            return buildAmirFullConfig(
                server = server,
                settings = settings,
                amirLocalPort = amirLocalPort,
                resolvedServerHosts = resolvedServerHosts,
                runtimeSocksPort = runtimeSocksPort,
                runtimeHttpPort = runtimeHttpPort
            )
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", settings.logLevel))
        root.put("stats", JSONObject())
        root.put("policy", buildPolicy())
        root.put("inbounds", buildInbounds(settings, runtimeSocksPort, runtimeHttpPort))
        root.put("outbounds", buildOutbounds(server, settings, amirLocalPort))

        val isAmir = server.protocol == ProtocolType.AMIR
        val effectiveLocalDns = settings.localDnsEnabled || settings.fakeDnsEnabled
        val hostsObj = buildDnsHostsObject(
            rawDnsHosts = settings.dnsHosts,
            resolvedServerHosts = resolvedServerHosts,
            includeDoHBootstrap = effectiveLocalDns
        )
        if (effectiveLocalDns || hostsObj.length() > 0) {
            root.put("dns", buildDns(settings, hostsObj))
        }
        root.put("routing", buildRouting(settings, customRules, isAmir))

        if (settings.fakeDnsEnabled) {
            val fakeDnsArr = JSONArray().put(
                JSONObject()
                    .put("ipPool", "198.18.0.0/15")
                    .put("poolSize", 65535)
            )
            if (settings.enableIpv6 || settings.preferIpv6) {
                fakeDnsArr.put(
                    JSONObject()
                        .put("ipPool", "fc00::/18")
                        .put("poolSize", 65535)
                )
            }
            root.put("fakedns", fakeDnsArr)
        }

        val testUrl = settings.delayTestUrl.trim().ifBlank { "https://www.gstatic.com/generate_204" }
        when (settings.observatoryMode) {
            AppSettings.OBSERVATORY_LEAST_PING -> {
                root.put(
                    "observatory",
                    JSONObject()
                        .put("subjectSelector", JSONArray().put("proxy"))
                        .put("probeURL", testUrl)
                        .put("probeInterval", settings.observatoryInterval.trim().ifBlank { "3m" })
                )
            }
            AppSettings.OBSERVATORY_LEAST_LOAD -> {
                root.put(
                    "burstObservatory",
                    JSONObject()
                        .put("subjectSelector", JSONArray().put("proxy"))
                        .put(
                            "pingConfig",
                            JSONObject()
                                .put("destination", testUrl)
                                .put("httpMethod", settings.burstObservatoryHttpMethod.ifBlank { "HEAD" })
                                .put("interval", settings.burstObservatoryInterval.trim().ifBlank { "5m" })
                                .put("sampling", settings.burstObservatorySampling.coerceIn(1, 20))
                                .put("timeout", settings.burstObservatoryTimeout.trim().ifBlank { "30s" })
                        )
                )
            }
        }

        return root.toString(2)
    }

    private fun buildAmirFullConfig(
        server: ServerConfig,
        settings: AppSettings,
        amirLocalPort: Int,
        resolvedServerHosts: Map<String, List<String>>,
        runtimeSocksPort: Int?,
        runtimeHttpPort: Int?
    ): String {
        val listenHost = if (settings.allowLan) "0.0.0.0" else "127.0.0.1"
        val amirSniffing = JSONObject()
            .put("destOverride", JSONArray().put("http").put("tls").put("quic").put("fakedns"))
            .put("enabled", true)
            .put("routeOnly", false)

        val hasAuth = settings.localProxyUsername.isNotBlank() || settings.localProxyPassword.isNotBlank()
        val accountsArr = if (hasAuth) {
            JSONArray().put(
                JSONObject()
                    .put("user", settings.localProxyUsername)
                    .put("pass", settings.localProxyPassword)
            )
        } else {
            null
        }

        val inbounds = JSONArray()
        val activeInboundTags = JSONArray()

        if (settings.vpnMode) {
            activeInboundTags.put("tun")
            inbounds.put(
                JSONObject()
                    .put("tag", "tun")
                    .put("port", 0)
                    .put("protocol", "tun")
                    .put(
                        "settings",
                        JSONObject()
                            .put("name", "xray0")
                            .put("MTU", settings.vpnMtu.coerceIn(1280, 9000))
                            .put("userLevel", 8)
                    )
                    .put("sniffing", JSONObject(amirSniffing.toString()))
            )
        }

        activeInboundTags.put("socks")
        val socksSettings = JSONObject()
            .put("auth", if (hasAuth) "password" else "noauth")
            .put("udp", true)
            .put("userLevel", 8)
        if (accountsArr != null) {
            socksSettings.put("accounts", accountsArr)
        }
        inbounds.put(
            JSONObject()
                .put("tag", "socks")
                .put("listen", listenHost)
                .put("port", runtimeSocksPort ?: settings.socksPort)
                .put("protocol", "socks")
                .put("settings", socksSettings)
                .put("sniffing", JSONObject(amirSniffing.toString()))
        )

        if (settings.effectiveLocalProxyEnabled()) {
            activeInboundTags.put("http")
            val httpSettings = JSONObject().put("userLevel", 8)
            if (accountsArr != null) {
                httpSettings.put("accounts", accountsArr)
            }
            inbounds.put(
                JSONObject()
                    .put("tag", "http")
                    .put("listen", listenHost)
                    .put("port", runtimeHttpPort ?: settings.httpPort)
                    .put("protocol", "http")
                    .put("settings", httpSettings)
                    .put("sniffing", JSONObject(amirSniffing.toString()))
            )
        }

        val outbounds = JSONArray()
            .put(buildProxyOutbound(server, settings, amirLocalPort))
            .put(
                JSONObject()
                    .put("tag", "direct")
                    .put("protocol", "freedom")
                    .put(
                        "streamSettings",
                        JSONObject()
                            .put("network", "tcp")
                            .put(
                                "sockopt",
                                JSONObject()
                                    .put("domainStrategy", "UseIP")
                                    .put(
                                        "happyEyeballs",
                                        JSONObject()
                                            .put("interleave", 2)
                                            .put("maxConcurrentTry", 4)
                                            .put("tryDelayMs", 250)
                                    )
                            )
                    )
            )
            .put(
                JSONObject()
                    .put("tag", "block")
                    .put("protocol", "blackhole")
                    .put("settings", JSONObject())
            )
            .put(
                JSONObject()
                    .put("tag", "dns-out")
                    .put("protocol", "dns")
            )

        val hostsObj = buildDnsHostsObject(
            rawDnsHosts = settings.dnsHosts,
            resolvedServerHosts = resolvedServerHosts,
            includeDoHBootstrap = true
        )
        val dnsServers = JSONArray()
            .put(
                JSONObject()
                    .put("address", "fakedns")
                    .put(
                        "domains",
                        JSONArray()
                            .put("geosite:cn")
                            .put("geosite:google")
                            .put("geosite:private")
                            .put("domain:alidns.com")
                            .put("domain:doh.pub")
                            .put("domain:dot.pub")
                            .put("domain:360.cn")
                            .put("domain:onedns.net")
                            .put("geosite:cn")
                    )
            )
            .put("https://cloudflare-dns.com/dns-query")
            .put(
                JSONObject()
                    .put("address", "223.5.5.5")
                    .put("domains", JSONArray().put("geosite:cn"))
                    .put("expectIPs", JSONArray().put("geoip:cn"))
                    .put("skipFallback", true)
                    .put("tag", "domestic-dns_cn_expect_0")
            )
            .put(
                JSONObject()
                    .put("address", "https://cloudflare-dns.com/dns-query")
                    .put("domains", JSONArray().put("geosite:google"))
            )
            .put(
                JSONObject()
                    .put("address", "223.5.5.5")
                    .put("domains", JSONArray().put("geosite:private"))
                    .put("skipFallback", true)
                    .put("tag", "domestic-dns_1_0")
            )
            .put(
                JSONObject()
                    .put("address", "223.5.5.5")
                    .put(
                        "domains",
                        JSONArray()
                            .put("domain:alidns.com")
                            .put("domain:doh.pub")
                            .put("domain:dot.pub")
                            .put("domain:360.cn")
                            .put("domain:onedns.net")
                    )
                    .put("skipFallback", true)
                    .put("tag", "domestic-dns_2_0")
            )
            .put(
                JSONObject()
                    .put("address", "223.5.5.5")
                    .put("domains", JSONArray().put("geosite:cn"))
                    .put("skipFallback", true)
                    .put("tag", "domestic-dns_3_0")
            )
            .put("https://dns.google/dns-query")

        val dnsObj = JSONObject()
            .put("hosts", hostsObj)
            .put("queryStrategy", if (settings.preferIpv6) "UseIP" else "UseIPv4")
            .put("servers", dnsServers)
            .put("tag", "dns-module")

        val fakeDnsArr = JSONArray().put(
            JSONObject()
                .put("ipPool", "198.18.0.0/15")
                .put("poolSize", 10000)
        )
        if (settings.enableIpv6 || settings.preferIpv6) {
            fakeDnsArr.put(
                JSONObject()
                    .put("ipPool", "fc00::/18")
                    .put("poolSize", 10000)
            )
        }

        val cnDnsIps = JSONArray()
            .put("223.5.5.5").put("223.6.6.6").put("2400:3200::1").put("2400:3200:baba::1")
            .put("119.29.29.29").put("1.12.12.12").put("120.53.53.53").put("2402:4e00::").put("2402:4e00:1::")
            .put("180.76.76.76").put("2400:da00::6666")
            .put("114.114.114.114").put("114.114.115.115")
            .put("114.114.114.119").put("114.114.115.119")
            .put("114.114.114.110").put("114.114.115.110")
            .put("180.184.1.1").put("180.184.2.2")
            .put("101.226.4.6").put("218.30.118.6").put("123.125.81.6").put("140.207.198.6")
            .put("1.2.4.8").put("210.2.4.8")
            .put("52.80.66.66").put("117.50.22.22")
            .put("2400:7fc0:849e:200::4").put("2404:c2c0:85d8:901::4")
            .put("117.50.10.10").put("52.80.52.52")
            .put("2400:7fc0:849e:200::8").put("2404:c2c0:85d8:901::8")
            .put("117.50.60.30").put("52.80.60.30")

        val rules = JSONArray()
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", activeInboundTags)
                    .put("port", "53")
                    .put("outboundTag", "dns-out")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "udp")
                    .put("port", "443")
                    .put("outboundTag", "block")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray().put("geosite:google"))
                    .put("outboundTag", "proxy")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray().put("ext:geoip-only-cn-private.dat:private"))
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray().put("geosite:private"))
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", cnDnsIps)
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put(
                        "domain",
                        JSONArray()
                            .put("domain:alidns.com")
                            .put("domain:doh.pub")
                            .put("domain:dot.pub")
                            .put("domain:360.cn")
                            .put("domain:onedns.net")
                    )
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray().put("ext:geoip-only-cn-private.dat:cn"))
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray().put("geosite:cn"))
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put(
                        "inboundTag",
                        JSONArray()
                            .put("domestic-dns_1_0")
                            .put("domestic-dns_2_0")
                            .put("domestic-dns_3_0")
                            .put("domestic-dns_cn_expect_0")
                    )
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("dns-module"))
                    .put("outboundTag", "proxy")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("port", "0-65535")
                    .put("outboundTag", "proxy")
            )

        return JSONObject()
            .put("dns", dnsObj)
            .put("fakedns", fakeDnsArr)
            .put("inbounds", inbounds)
            .put("log", JSONObject().put("loglevel", settings.logLevel.ifBlank { "warning" }))
            .put("outbounds", outbounds)
            .put("policy", buildPolicy())
            .put(
                "routing",
                JSONObject()
                    .put("domainStrategy", "AsIs")
                    .put("rules", rules)
            )
            .put("stats", JSONObject())
            .toString(2)
    }

    fun buildOutboundDelayTestJson(
        server: ServerConfig,
        settings: AppSettings,
        amirLocalPort: Int = 0
    ): String {
        if (server.protocol == ProtocolType.CUSTOM && server.rawCustomJson.isNotBlank()) {
            return server.rawCustomJson
        }
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "none"))
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(server, settings, amirLocalPort))
        if (settings.fragmentEnabled && server.protocol != ProtocolType.WIREGUARD && server.protocol != ProtocolType.AMIR) {
            outbounds.put(buildFragmentOutbound(settings))
        }
        outbounds.put(
            JSONObject()
                .put("tag", "direct")
                .put("protocol", "freedom")
                .put(
                    "streamSettings",
                    JSONObject().put("sockopt", JSONObject().put("domainStrategy", resolveSockoptDomainStrategy(settings)))
                )
        )
        root.put("outbounds", outbounds)
        return root.toString()
    }

    private fun buildCustomConfigWithInjectedSettings(
        rawJson: String,
        settings: AppSettings,
        resolvedServerHosts: Map<String, List<String>>
    ): String {
        return runCatching {
            val root = JSONObject(rawJson)
            if (!root.has("stats")) root.put("stats", JSONObject())
            if (!root.has("policy")) root.put("policy", buildPolicy())
            if (settings.vpnMode) {
                val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
                var hasTun = false
                for (i in 0 until inbounds.length()) {
                    val ib = inbounds.optJSONObject(i)
                    if (ib?.optString("protocol") == "tun") {
                        hasTun = true
                        ib.optJSONObject("settings")?.put("MTU", settings.vpnMtu.coerceIn(1280, 9000))
                        break
                    }
                }
                if (!hasTun) {
                    inbounds.put(buildTunInbound(settings))
                    root.put("inbounds", inbounds)
                }
            }
            val hostsObj = buildDnsHostsObject(settings.dnsHosts, resolvedServerHosts)
            if (hostsObj.length() > 0) {
                val dnsObj = root.optJSONObject("dns") ?: JSONObject()
                val existingHosts = dnsObj.optJSONObject("hosts") ?: JSONObject()
                val keys = hostsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (!existingHosts.has(k)) {
                        existingHosts.put(k, hostsObj.get(k))
                    }
                }
                dnsObj.put("hosts", existingHosts)
                root.put("dns", dnsObj)
            }
            val testUrl = settings.delayTestUrl.trim().ifBlank { "https://www.gstatic.com/generate_204" }
            root.optJSONObject("log")?.put("loglevel", settings.logLevel)
            root.optJSONObject("observatory")?.let { obs ->
                obs.put("probeInterval", settings.observatoryInterval.trim().ifBlank { "3m" })
                if ((!obs.has("probeURL") || obs.optString("probeURL").isBlank()) &&
                    (!obs.has("probeUrl") || obs.optString("probeUrl").isBlank())
                ) {
                    obs.put("probeURL", testUrl)
                }
            }
            root.optJSONObject("burstObservatory")?.optJSONObject("pingConfig")?.let { ping ->
                ping.put("interval", settings.burstObservatoryInterval.trim().ifBlank { "5m" })
                ping.put("httpMethod", settings.burstObservatoryHttpMethod.ifBlank { "HEAD" })
                ping.put("sampling", settings.burstObservatorySampling.coerceIn(1, 20))
                ping.put("timeout", settings.burstObservatoryTimeout.trim().ifBlank { "30s" })
                if (!ping.has("destination") || ping.optString("destination").isBlank()) {
                    ping.put("destination", testUrl)
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

    private fun buildInbounds(
        settings: AppSettings,
        runtimeSocksPort: Int? = null,
        runtimeHttpPort: Int? = null
    ): JSONArray {
        val inbounds = JSONArray()
        val listenHost = if (settings.allowLan) "0.0.0.0" else "127.0.0.1"
        val sniffing = buildSniffing(settings)

        if (settings.vpnMode) {
            inbounds.put(buildTunInbound(settings))
        }

        if (settings.effectiveLocalProxyEnabled()) {
            val hasAuth = settings.localProxyUsername.isNotBlank() || settings.localProxyPassword.isNotBlank()
            val accountsArr = if (hasAuth) {
                JSONArray().put(
                    JSONObject()
                        .put("user", settings.localProxyUsername)
                        .put("pass", settings.localProxyPassword)
                )
            } else {
                null
            }

            val socksSettings = JSONObject()
                .put("auth", if (hasAuth) "password" else "noauth")
                .put("udp", settings.socks5UdpEnabled)
                .put("userLevel", 8)
            if (accountsArr != null) {
                socksSettings.put("accounts", accountsArr)
            }

            inbounds.put(
                JSONObject().apply {
                    put("tag", "socks")
                    put("port", runtimeSocksPort ?: settings.socksPort)
                    put("listen", listenHost)
                    put("protocol", "socks")
                    put("settings", socksSettings)
                    put("sniffing", sniffing)
                }
            )

            val httpSettings = JSONObject().put("userLevel", 8)
            if (accountsArr != null) {
                httpSettings.put("accounts", accountsArr)
            }

            inbounds.put(
                JSONObject().apply {
                    put("tag", "http")
                    put("port", runtimeHttpPort ?: settings.httpPort)
                    put("listen", listenHost)
                    put("protocol", "http")
                    put("settings", httpSettings)
                    put("sniffing", sniffing)
                }
            )
        }
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
                    .put("MTU", settings.vpnMtu.coerceIn(1280, 9000))
                    .put("userLevel", 8)
            )
            put("sniffing", buildSniffing(settings))
        }
    }

    private fun buildSniffing(settings: AppSettings): JSONObject {
        val destOverride = JSONArray().apply {
            if (settings.fakeDnsEnabled) put("fakedns")
            put("http")
            put("tls")
            put("quic")
        }
        return JSONObject().apply {
            put("enabled", settings.sniffingEnabled || settings.fakeDnsEnabled)
            put("destOverride", destOverride)
            put("routeOnly", if (settings.fakeDnsEnabled) false else settings.routeOnly)
        }
    }

    fun resolveSockoptDomainStrategy(settings: AppSettings): String {
        return when (settings.outboundDomainResolve) {
            AppSettings.OUTBOUND_AS_IS -> "AsIs"
            AppSettings.OUTBOUND_USE_IP -> "UseIP"
            AppSettings.OUTBOUND_USE_IPV4 -> "UseIPv4"
            AppSettings.OUTBOUND_USE_IPV6 -> "UseIPv6"
            else -> if (settings.preferIpv6) "UseIP" else "UseIPv4"
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
        val directDomainStrategy = resolveSockoptDomainStrategy(settings)
        val directSockopt = JSONObject()
            .put("domainStrategy", directDomainStrategy)
            .put(
                "happyEyeballs",
                JSONObject()
                    .put("interleave", 2)
                    .put("maxConcurrentTry", 4)
                    .put("tryDelayMs", 250)
            )
        outbounds.put(
            JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put(
                    "streamSettings",
                    JSONObject()
                        .put("network", "tcp")
                        .put("sockopt", directSockopt)
                )
            }
        )
        outbounds.put(
            JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject())
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
        val fragmentObj = JSONObject()
            .put("packets", settings.fragmentPackets.ifBlank { "tlshello" })
            .put("length", settings.fragmentLength.ifBlank { "100-200" })
            .put("interval", settings.fragmentInterval.ifBlank { "10-20" })
        val maxSplit = settings.fragmentMaxSplit.trim()
        if (maxSplit.isNotEmpty()) {
            fragmentObj.put("maxSplit", maxSplit)
        }
        return JSONObject().apply {
            put("tag", "fragment")
            put("protocol", "freedom")
            put("settings", JSONObject().put("fragment", fragmentObj))
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
                val isLoopback = isLoopbackAddress(server.address)
                outbound.put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        buildOutboundSockopt(settings, allowFragment = !isLoopback)
                    )
                )
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
                val isLoopback = isLoopbackAddress(server.address)
                outbound.put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        buildOutboundSockopt(settings, allowFragment = !isLoopback)
                    )
                )
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
                    .put("sockopt", buildOutboundSockopt(settings, allowFragment = true))
                outbound.put("streamSettings", stream)
            }

            ProtocolType.AMIR -> {
                outbound.put("protocol", "socks")
                val targetPort = if (amirLocalPort > 0) amirLocalPort else 40446
                outbound.put(
                    "settings",
                    JSONObject()
                        .put("address", "127.0.0.1")
                        .put("level", 8)
                        .put("port", targetPort)
                )
                outbound.put("streamSettings", JSONObject().put("network", "tcp"))
                outbound.put(
                    "mux",
                    JSONObject()
                        .put("concurrency", -1)
                        .put("enabled", false)
                )
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
                    .put("concurrency", settings.muxConcurrency.coerceIn(-1, 1024))
                    .put("xudpConcurrency", settings.muxXudpConcurrency.coerceIn(-1, 1024))
                    .put("xudpProxyUDP443", settings.muxXudpProxyUdp443.ifBlank { "reject" })
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

        val allowFrag = server.protocol != ProtocolType.WIREGUARD &&
            server.protocol != ProtocolType.AMIR &&
            !isLoopbackAddress(server.address)
        stream.put("sockopt", buildOutboundSockopt(settings, allowFragment = allowFrag))

        return stream
    }

    private fun buildOutboundSockopt(settings: AppSettings, allowFragment: Boolean): JSONObject {
        return JSONObject().apply {
            put("domainStrategy", resolveSockoptDomainStrategy(settings))
            if (settings.fragmentEnabled && allowFragment) {
                put("dialerProxy", "fragment")
            }
        }
    }

    private fun isLoopbackAddress(host: String): Boolean {
        val clean = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        return clean == "127.0.0.1" || clean == "localhost" || clean == "::1"
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

    fun parseDnsHosts(raw: String): Map<String, List<String>> {
        if (raw.isBlank()) return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        val prefixes = listOf("domain:", "full:", "keyword:", "regexp:", "geosite:")
        raw.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { entry ->
                val matchedPrefix = prefixes.firstOrNull { entry.startsWith(it, ignoreCase = true) }
                val searchStart = matchedPrefix?.length ?: 0
                val colonIdx = entry.indexOf(':', startIndex = searchStart)
                if (colonIdx > 0 && colonIdx < entry.length - 1) {
                    val domain = entry.substring(0, colonIdx).trim()
                    val targetRaw = entry.substring(colonIdx + 1).trim()
                    val targets = targetRaw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
                    if (domain.isNotEmpty() && targets.isNotEmpty()) {
                        result[domain] = targets
                    }
                }
            }
        return result
    }

    private fun buildDnsHostsObject(
        rawDnsHosts: String,
        resolvedServerHosts: Map<String, List<String>>,
        includeDoHBootstrap: Boolean = false
    ): JSONObject {
        val merged = linkedMapOf<String, List<String>>()

        if (includeDoHBootstrap) {
            merged["domain:googleapis.cn"] = listOf("googleapis.com")
            merged["dns.alidns.com"] = listOf("223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1")
            merged["dns.sse.cisco.com"] = listOf("208.67.220.220", "208.67.222.222", "2620:119:35::35", "2620:119:53::53")
            merged["dns.umbrella.com"] = listOf("208.67.220.220", "208.67.222.222", "2620:119:35::35", "2620:119:53::53")
            merged["one.one.one.one"] = listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
            merged["1dot1dot1dot1.cloudflare-dns.com"] = listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
            merged["dns.cloudflare.com"] = listOf("162.159.61.8", "172.64.41.8", "2a06:98c1:52::8", "2803:f800:53::8")
            merged["cloudflare-dns.com"] = listOf("104.16.248.249", "104.16.249.249", "2606:4700::6810:f8f9", "2606:4700::6810:f9f9")
            merged["engage.cloudflareclient.com"] = listOf("162.159.192.1", "2606:4700:d0::a29f:c001")
            merged["doh.pub"] = listOf("1.12.12.12", "120.53.53.53")
            merged["dot.pub"] = listOf("1.12.12.12", "120.53.53.53")
            merged["dns.google"] = listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
            merged["dns.quad9.net"] = listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
            merged["dns.sb"] = listOf("45.11.45.11", "185.222.222.222", "2a09::", "2a11::")
            merged["common.dot.dns.yandex.net"] = listOf("77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff")
        }

        resolvedServerHosts.forEach { (domain, ips) ->
            val cleanIps = ips.map { it.trim() }.filter { it.isNotEmpty() }
            if (domain.isNotBlank() && cleanIps.isNotEmpty()) {
                merged[domain.trim()] = cleanIps
            }
        }
        // User-configured DNS host mappings override auto-resolved server host mappings
        parseDnsHosts(rawDnsHosts).forEach { (domain, ips) ->
            merged[domain] = ips
        }
        return JSONObject().apply {
            merged.forEach { (domain, ips) ->
                if (ips.size == 1) {
                    put(domain, ips.first())
                } else {
                    val arr = JSONArray()
                    ips.forEach { arr.put(it) }
                    put(domain, arr)
                }
            }
        }
    }

    private fun buildDns(
        settings: AppSettings,
        hostsObj: JSONObject = JSONObject()
    ): JSONObject {
        val servers = JSONArray()
        val domestic = settings.domesticDns.trim().ifBlank { "223.5.5.5" }
        val configuredRemote = settings.remoteDns.trim().ifBlank { "https://1.1.1.1/dns-query" }

        if (settings.fakeDnsEnabled) {
            servers.put("fakedns")
        }

        servers.put(configuredRemote)

        when (settings.routingMode) {
            RoutingMode.BYPASS_LAN_AND_MAINLAND -> {
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put("domains", JSONArray().put("geosite:cn"))
                        .put("expectIPs", JSONArray().put("geoip:cn"))
                        .put("skipFallback", true)
                        .put("tag", "domestic-dns_cn_expect_0")
                )
                servers.put(
                    JSONObject()
                        .put("address", configuredRemote)
                        .put("domains", JSONArray().put("geosite:google"))
                )
                if (settings.bypassLan) {
                    servers.put(
                        JSONObject()
                            .put("address", domestic)
                            .put("domains", JSONArray().put("geosite:private"))
                            .put("skipFallback", true)
                            .put("tag", "domestic-dns_1_0")
                    )
                }
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put(
                            "domains",
                            JSONArray()
                                .put("domain:alidns.com")
                                .put("domain:doh.pub")
                                .put("domain:dot.pub")
                                .put("domain:360.cn")
                                .put("domain:onedns.net")
                        )
                        .put("skipFallback", true)
                        .put("tag", "domestic-dns_2_0")
                )
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put("domains", JSONArray().put("geosite:cn"))
                        .put("skipFallback", true)
                        .put("tag", "domestic-dns_3_0")
                )
            }
            RoutingMode.BYPASS_LAN_AND_IR -> {
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put("domains", JSONArray().put("geosite:ir").put("regexp:.*\\.ir$"))
                        .put("expectIPs", JSONArray().put("geoip:ir"))
                        .put("skipFallback", true)
                        .put("tag", "domestic-dns_ir_expect_0")
                )
                servers.put(
                    JSONObject()
                        .put("address", configuredRemote)
                        .put("domains", JSONArray().put("geosite:google"))
                )
                if (settings.bypassLan) {
                    servers.put(
                        JSONObject()
                            .put("address", domestic)
                            .put("domains", JSONArray().put("geosite:private"))
                            .put("skipFallback", true)
                            .put("tag", "domestic-dns_1_0")
                    )
                }
                servers.put(
                    JSONObject()
                        .put("address", domestic)
                        .put("domains", JSONArray().put("geosite:ir").put("regexp:.*\\.ir$"))
                        .put("skipFallback", true)
                        .put("tag", "domestic-dns_2_0")
                )
            }
            else -> {
                servers.put(
                    JSONObject()
                        .put("address", configuredRemote)
                        .put("domains", JSONArray().put("geosite:google"))
                )
                if (settings.bypassLan) {
                    servers.put(
                        JSONObject()
                            .put("address", domestic)
                            .put("domains", JSONArray().put("geosite:private"))
                            .put("skipFallback", true)
                            .put("tag", "domestic-dns_1_0")
                    )
                }
            }
        }

        // Bootstrap fallback if Remote DNS uses a custom hostname not in hostsObj
        val remoteDnsHost = extractDnsHost(configuredRemote)
        if (remoteDnsHost != null && !hostsObj.has(remoteDnsHost)) {
            servers.put(
                JSONObject()
                    .put("address", if (isIpLiteral(domestic)) domestic else "223.5.5.5")
                    .put("domains", JSONArray().put("full:$remoteDnsHost"))
                    .put("skipFallback", true)
            )
        }

        val dnsObj = JSONObject()
            .put("tag", "dns-module")
            .put("queryStrategy", if (settings.preferIpv6) "UseIP" else "UseIPv4")
            .put("disableFallbackIfMatch", true)
            .put("servers", servers)
        if (hostsObj.length() > 0) {
            dnsObj.put("hosts", hostsObj)
        }
        return dnsObj
    }

    fun extractDnsHost(dnsUrl: String): String? {
        val trimmed = dnsUrl.trim()
        val afterScheme = trimmed.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        val host = if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }.trim()
        if (host.isEmpty() || isIpLiteral(host) || host.equals("localhost", ignoreCase = true)) {
            return null
        }
        return host
    }

    private fun isIpLiteral(value: String): Boolean {
        val clean = value.removePrefix("[").removeSuffix("]")
        val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        if (ipv4.matches(clean)) return true
        return clean.contains(':') && !clean.contains('/')
    }

    private fun buildRouting(
        settings: AppSettings,
        customRules: List<RoutingRule>,
        isAmir: Boolean = false
    ): JSONObject {
        val rules = JSONArray()

        if (settings.localDnsEnabled || settings.fakeDnsEnabled) {
            val dnsInboundTags = JSONArray().apply {
                if (settings.vpnMode) put("tun")
                if (settings.effectiveLocalProxyEnabled()) {
                    put("socks")
                    put("http")
                }
            }
            if (dnsInboundTags.length() > 0) {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", dnsInboundTags)
                        .put("port", "53")
                        .put("outboundTag", "dns-out")
                )
            }

            val domesticTags = JSONArray().apply {
                when (settings.routingMode) {
                    RoutingMode.BYPASS_LAN_AND_MAINLAND -> {
                        if (settings.bypassLan) put("domestic-dns_1_0")
                        put("domestic-dns_2_0")
                        put("domestic-dns_3_0")
                        put("domestic-dns_cn_expect_0")
                    }
                    RoutingMode.BYPASS_LAN_AND_IR -> {
                        if (settings.bypassLan) put("domestic-dns_1_0")
                        put("domestic-dns_2_0")
                        put("domestic-dns_ir_expect_0")
                    }
                    else -> {
                        if (settings.bypassLan) put("domestic-dns_1_0")
                    }
                }
            }
            if (domesticTags.length() > 0) {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", domesticTags)
                        .put("outboundTag", "direct")
                )
            }

            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("dns-module"))
                    .put("outboundTag", "proxy")
            )
        }

        if (settings.fakeDnsEnabled) {
            val fakeIps = JSONArray().put("198.18.0.0/15")
            if (settings.enableIpv6 || settings.preferIpv6) {
                fakeIps.put("fc00::/18")
            }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", fakeIps)
                    .put("outboundTag", "proxy")
            )
        }

        if (isAmir) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "udp")
                    .put("port", "443")
                    .put("outboundTag", "block")
            )
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray().put("geosite:google"))
                    .put("outboundTag", "proxy")
            )
        }

        when (settings.routingMode) {
            RoutingMode.BYPASS_LAN -> {
                if (settings.bypassLan) {
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("ip", JSONArray().put("geoip:private"))
                            .put("outboundTag", "direct")
                    )
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("domain", JSONArray().put("geosite:private"))
                            .put("outboundTag", "direct")
                    )
                }
            }
            RoutingMode.BYPASS_LAN_AND_MAINLAND -> {
                val ipArr = JSONArray().apply {
                    if (settings.bypassLan) put("geoip:private")
                    put("geoip:cn")
                }
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", ipArr)
                        .put("outboundTag", "direct")
                )
                val domainArr = JSONArray().apply {
                    if (settings.bypassLan) put("geosite:private")
                    put("geosite:cn")
                }
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("domain", domainArr)
                        .put("outboundTag", "direct")
                )
            }
            RoutingMode.BYPASS_LAN_AND_IR -> {
                val ipArr = JSONArray().apply {
                    if (settings.bypassLan) put("geoip:private")
                    put("geoip:ir")
                }
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", ipArr)
                        .put("outboundTag", "direct")
                )
                val domainArr = JSONArray().apply {
                    if (settings.bypassLan) put("geosite:private")
                    put("geosite:ir")
                    put("regexp:.*\\.ir$")
                }
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("domain", domainArr)
                        .put("outboundTag", "direct")
                )
            }
            RoutingMode.GLOBAL_PROXY -> {
                if (settings.bypassLan) {
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("ip", JSONArray().put("geoip:private"))
                            .put("outboundTag", "direct")
                    )
                    rules.put(
                        JSONObject()
                            .put("type", "field")
                            .put("domain", JSONArray().put("geosite:private"))
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
                    if (domainList.isNotEmpty() || ipList.isNotEmpty() || rule.port.isNotBlank() || rule.network.isNotBlank()) {
                        rules.put(ruleObj)
                    }
                }
            }
        }

        rules.put(
            JSONObject()
                .put("type", "field")
                .put("port", "0-65535")
                .put("outboundTag", "proxy")
        )

        val needsRouterIpResolution = settings.routingMode == RoutingMode.BYPASS_LAN_AND_IR ||
            settings.routingMode == RoutingMode.BYPASS_LAN_AND_MAINLAND ||
            (settings.routingMode == RoutingMode.CUSTOM && customRules.any { it.enabled && it.ips.isNotBlank() })
        val configuredStrategy = settings.domainStrategy.ifBlank { "IPIfNonMatch" }
        val effectiveDomainStrategy = if (!needsRouterIpResolution && configuredStrategy == "IPIfNonMatch") {
            "AsIs"
        } else {
            configuredStrategy
        }

        return JSONObject()
            .put("domainStrategy", effectiveDomainStrategy)
            .put("rules", rules)
    }
}

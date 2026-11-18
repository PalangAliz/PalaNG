package com.example.core

import android.net.Uri
import android.util.Base64
import com.example.data.model.ProtocolType
import com.example.data.model.ServerConfig
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ConfigParser {

    fun parseBatch(rawInput: String, subscriptionId: String = ""): List<ServerConfig> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Check if raw Xray JSON object
        if (trimmed.startsWith("{")) {
            parseCustomJson(trimmed, subscriptionId)?.let { return listOf(it) }
        }

        val normalizedText = if (isLikelyUriList(trimmed)) {
            trimmed
        } else {
            decodeBase64Safe(trimmed) ?: trimmed
        }

        val results = mutableListOf<ServerConfig>()
        normalizedText.lines().forEach { line ->
            val cleanLine = line.trim()
            if (cleanLine.isNotEmpty()) {
                parseSingleUri(cleanLine, subscriptionId)?.let { results.add(it) }
            }
        }
        return results
    }

    fun parseSingleUri(uriString: String, subscriptionId: String = ""): ServerConfig? {
        val raw = uriString.trim()
        if (raw.startsWith("{")) {
            return parseCustomJson(raw, subscriptionId)
        }
        return when {
            raw.startsWith("vmess://", ignoreCase = true) -> parseVmess(raw, subscriptionId)
            raw.startsWith("vless://", ignoreCase = true) -> parseVlessOrTrojan(raw, ProtocolType.VLESS, subscriptionId)
            raw.startsWith("trojan://", ignoreCase = true) -> parseVlessOrTrojan(raw, ProtocolType.TROJAN, subscriptionId)
            raw.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(raw, subscriptionId)
            raw.startsWith("socks://", ignoreCase = true) || raw.startsWith("socks5://", ignoreCase = true) ->
                parseSocksOrHttp(raw, ProtocolType.SOCKS, subscriptionId)
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) ->
                parseSocksOrHttp(raw, ProtocolType.HTTP, subscriptionId)
            raw.startsWith("wireguard://", ignoreCase = true) || raw.startsWith("wg://", ignoreCase = true) ->
                parseWireGuard(raw, subscriptionId)
            raw.startsWith("hysteria2://", ignoreCase = true) || raw.startsWith("hy2://", ignoreCase = true) ->
                parseHysteria2(raw, subscriptionId)
            raw.startsWith("amir://", ignoreCase = true) ->
                parseAmir(raw, subscriptionId)
            else -> null
        }
    }

    private fun parseCustomJson(rawJson: String, subscriptionId: String): ServerConfig? {
        return runCatching {
            val obj = JSONObject(rawJson)
            if (!obj.has("outbounds") && !obj.has("inbounds")) return null
            val remarks = obj.optString("remarks").ifBlank { "Custom Config" }
            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks,
                protocol = ProtocolType.CUSTOM,
                rawCustomJson = rawJson
            )
        }.getOrNull()
    }

    private fun parseVmess(raw: String, subscriptionId: String): ServerConfig? {
        val payload = raw.substringAfter("://").trim()
        val decoded = decodeBase64Safe(payload)
        if (decoded != null && decoded.trim().startsWith("{")) {
            return runCatching {
                val json = JSONObject(decoded)
                val add = json.optString("add")
                val port = json.opt("port")?.toString()?.toIntOrNull() ?: 443
                if (add.isBlank()) return null
                ServerConfig(
                    subscriptionId = subscriptionId,
                    remarks = json.optString("ps").ifBlank { "$add:$port" },
                    protocol = ProtocolType.VMESS,
                    address = add,
                    port = port,
                    passwordOrId = json.optString("id"),
                    alterId = json.opt("aid")?.toString()?.toIntOrNull() ?: 0,
                    security = json.optString("scy").ifBlank { "auto" },
                    network = json.optString("net").ifBlank { "tcp" },
                    headerType = json.optString("type").ifBlank { "none" },
                    host = json.optString("host"),
                    path = json.optString("path"),
                    streamSecurity = json.optString("tls"),
                    sni = json.optString("sni"),
                    fingerprint = json.optString("fp"),
                    alpn = json.optString("alpn"),
                    allowInsecure = json.optString("allowInsecure") == "1" || json.optBoolean("allowInsecure", false)
                )
            }.getOrNull()
        }
        // Fallback standard URI form vmess://uuid@host:port?...#remarks
        return parseVlessOrTrojan(raw, ProtocolType.VMESS, subscriptionId)
    }

    private fun parseVlessOrTrojan(raw: String, protocol: ProtocolType, subscriptionId: String): ServerConfig? {
        return runCatching {
            val uri = Uri.parse(raw)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 443
            val userInfo = uri.userInfo ?: ""
            val remarks = urlDecode(uri.fragment ?: "").ifBlank { "$host:$port" }
            val securityParam = uri.getQueryParameter("security") ?: if (protocol == ProtocolType.TROJAN) "tls" else ""
            val typeParam = uri.getQueryParameter("type") ?: "tcp"
            val pathOrService = uri.getQueryParameter("path")
                ?: uri.getQueryParameter("serviceName")
                ?: ""
            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks,
                protocol = protocol,
                address = host,
                port = port,
                passwordOrId = urlDecode(userInfo),
                security = uri.getQueryParameter("encryption") ?: if (protocol == ProtocolType.VLESS) "none" else "auto",
                flow = uri.getQueryParameter("flow") ?: "",
                network = typeParam,
                headerType = uri.getQueryParameter("headerType") ?: "none",
                host = uri.getQueryParameter("host") ?: uri.getQueryParameter("authority") ?: "",
                path = pathOrService,
                grpcMode = uri.getQueryParameter("mode") ?: "gun",
                streamSecurity = securityParam,
                sni = uri.getQueryParameter("sni") ?: "",
                fingerprint = uri.getQueryParameter("fp") ?: "",
                alpn = uri.getQueryParameter("alpn") ?: "",
                allowInsecure = uri.getQueryParameter("allowInsecure") == "1" || uri.getQueryParameter("insecure") == "1",
                publicKey = uri.getQueryParameter("pbk") ?: "",
                shortId = uri.getQueryParameter("sid") ?: "",
                spiderX = uri.getQueryParameter("spx") ?: ""
            )
        }.getOrNull()
    }

    private fun parseShadowsocks(raw: String, subscriptionId: String): ServerConfig? {
        return runCatching {
            val withoutScheme = raw.substringAfter("://")
            val remarksSplit = withoutScheme.split("#", limit = 2)
            val remarks = if (remarksSplit.size > 1) urlDecode(remarksSplit[1]) else ""
            val mainAndQuery = remarksSplit[0].split("?", limit = 2)
            val mainPart = mainAndQuery[0]
            val queryStr = if (mainAndQuery.size > 1) mainAndQuery[1] else ""
            val pluginParam = if (queryStr.isNotBlank()) {
                Uri.parse("ss://dummy?$queryStr").getQueryParameter("plugin") ?: ""
            } else ""

            val method: String
            val password: String
            val host: String
            val port: Int

            if (mainPart.contains("@")) {
                val atIdx = mainPart.lastIndexOf("@")
                val userPartRaw = mainPart.substring(0, atIdx)
                val hostPortRaw = mainPart.substring(atIdx + 1)
                val decodedUser = if (userPartRaw.contains(":")) userPartRaw else (decodeBase64Safe(userPartRaw) ?: userPartRaw)
                val colonIdx = decodedUser.indexOf(":")
                if (colonIdx <= 0) return null
                method = decodedUser.substring(0, colonIdx)
                password = decodedUser.substring(colonIdx + 1)
                val parsedUri = Uri.parse("ss://user@$hostPortRaw")
                host = parsedUri.host ?: return null
                port = if (parsedUri.port > 0) parsedUri.port else return null
            } else {
                // Legacy ss://base64(method:password@host:port)
                val decoded = decodeBase64Safe(mainPart) ?: return null
                val atIdx = decoded.lastIndexOf("@")
                if (atIdx <= 0) return null
                val cred = decoded.substring(0, atIdx)
                val hostPort = decoded.substring(atIdx + 1)
                val colonIdx = cred.indexOf(":")
                if (colonIdx <= 0) return null
                method = cred.substring(0, colonIdx)
                password = cred.substring(colonIdx + 1)
                val parsedUri = Uri.parse("ss://user@$hostPort")
                host = parsedUri.host ?: return null
                port = if (parsedUri.port > 0) parsedUri.port else return null
            }

            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks.ifBlank { "$host:$port" },
                protocol = ProtocolType.SHADOWSOCKS,
                address = host,
                port = port,
                passwordOrId = password,
                security = method,
                extraParam1 = pluginParam
            )
        }.getOrNull()
    }

    private fun parseSocksOrHttp(raw: String, protocol: ProtocolType, subscriptionId: String): ServerConfig? {
        return runCatching {
            val uri = Uri.parse(raw)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else if (protocol == ProtocolType.SOCKS) 1080 else 8080
            val rawUser = uri.userInfo ?: ""
            val decodedCred = if (rawUser.isNotBlank() && !rawUser.contains(":")) {
                decodeBase64Safe(rawUser) ?: rawUser
            } else rawUser
            val remarks = urlDecode(uri.fragment ?: "").ifBlank { "$host:$port" }
            val username = decodedCred.substringBefore(":", "")
            val password = if (decodedCred.contains(":")) decodedCred.substringAfter(":") else ""
            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks,
                protocol = protocol,
                address = host,
                port = port,
                passwordOrId = password,
                extraParam1 = username
            )
        }.getOrNull()
    }

    private fun parseWireGuard(raw: String, subscriptionId: String): ServerConfig? {
        return runCatching {
            val normalized = raw.replaceFirst("wg://", "wireguard://", ignoreCase = true)
            val uri = Uri.parse(normalized)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 51820
            val privateKey = urlDecode(uri.userInfo ?: "")
            val remarks = urlDecode(uri.fragment ?: "").ifBlank { "$host:$port" }
            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks,
                protocol = ProtocolType.WIREGUARD,
                address = host,
                port = port,
                passwordOrId = privateKey,
                publicKey = uri.getQueryParameter("publickey") ?: uri.getQueryParameter("pk") ?: "",
                extraParam1 = uri.getQueryParameter("address") ?: "10.0.0.2/32",
                extraParam2 = uri.getQueryParameter("mtu") ?: "1420",
                extraParam3 = uri.getQueryParameter("reserved") ?: ""
            )
        }.getOrNull()
    }

    private fun parseHysteria2(raw: String, subscriptionId: String): ServerConfig? {
        return runCatching {
            val normalized = raw.replaceFirst("hy2://", "hysteria2://", ignoreCase = true)
            val uri = Uri.parse(normalized)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 443
            val password = urlDecode(uri.userInfo ?: "")
            val remarks = urlDecode(uri.fragment ?: "").ifBlank { "$host:$port" }
            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks,
                protocol = ProtocolType.HYSTERIA2,
                address = host,
                port = port,
                passwordOrId = password,
                streamSecurity = "tls",
                sni = uri.getQueryParameter("sni") ?: "",
                alpn = uri.getQueryParameter("alpn") ?: "",
                allowInsecure = uri.getQueryParameter("insecure") == "1" || uri.getQueryParameter("allowInsecure") == "1",
                extraParam1 = uri.getQueryParameter("obfs") ?: "",
                extraParam2 = uri.getQueryParameter("obfs-password") ?: "",
                extraParam3 = uri.getQueryParameter("mport") ?: ""
            )
        }.getOrNull()
    }

    private fun parseAmir(raw: String, subscriptionId: String): ServerConfig? {
        return runCatching {
            val uri = Uri.parse(raw)
            val uuid = urlDecode(uri.userInfo ?: "")
            val rawWorkerUrl = uri.getQueryParameter("workerUrl")
                ?: uri.getQueryParameter("worker_url")
                ?: uri.getQueryParameter("worker")
                ?: ""
            val hostFromUri = uri.host ?: ""
            val normalizedWorkerUrl = normalizeAmirWorkerUrl(
                rawWorkerUrl.ifBlank {
                    if (hostFromUri.isNotBlank()) {
                        val portSuffix = if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""
                        val pathPart = uri.path?.takeIf { it.isNotBlank() } ?: "/"
                        "wss://$hostFromUri$portSuffix$pathPart"
                    } else ""
                }
            )
            val workerUri = Uri.parse(normalizedWorkerUrl)
            val workerHost = workerUri.host ?: hostFromUri
            if (workerHost.isBlank() || uuid.isBlank()) return null
            val port = if (workerUri.port > 0) workerUri.port else if (uri.port > 0) uri.port else 443
            val cleanIp = (uri.getQueryParameter("cleanIp")
                ?: uri.getQueryParameter("clean_ip")
                ?: uri.getQueryParameter("cfCleanIp")
                ?: uri.getQueryParameter("ip")
                ?: "").trim()
            val remarks = urlDecode(uri.fragment ?: "").ifBlank { "Amir ($workerHost)" }
            ServerConfig(
                subscriptionId = subscriptionId,
                remarks = remarks,
                protocol = ProtocolType.AMIR,
                address = workerHost,
                port = port,
                passwordOrId = uuid,
                extraParam1 = normalizedWorkerUrl,
                extraParam2 = cleanIp
            )
        }.getOrNull()
    }

    fun normalizeAmirWorkerUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> "wss://" + trimmed.substring(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "ws://" + trimmed.substring(5)
            trimmed.startsWith("https://", ignoreCase = true) -> "wss://" + trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> "ws://" + trimmed.substring(7)
            else -> "wss://$trimmed"
        }
        val parsed = Uri.parse(withScheme)
        val path = parsed.encodedPath
        return if (path.isNullOrEmpty()) "$withScheme/" else withScheme
    }

    fun toShareUri(config: ServerConfig): String {
        return when (config.protocol) {
            ProtocolType.VMESS -> {
                val json = JSONObject().apply {
                    put("v", "2")
                    put("ps", config.remarks)
                    put("add", config.address)
                    put("port", config.port.toString())
                    put("id", config.passwordOrId)
                    put("aid", config.alterId.toString())
                    put("scy", config.security.ifBlank { "auto" })
                    put("net", config.network.ifBlank { "tcp" })
                    put("type", config.headerType.ifBlank { "none" })
                    put("host", config.host)
                    put("path", config.path)
                    put("tls", config.streamSecurity)
                    put("sni", config.sni)
                    put("fp", config.fingerprint)
                    put("alpn", config.alpn)
                    if (config.allowInsecure) put("allowInsecure", "1")
                }
                val encoded = Base64.encodeToString(
                    json.toString().toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                )
                "vmess://$encoded"
            }

            ProtocolType.VLESS, ProtocolType.TROJAN -> {
                val scheme = config.protocol.scheme
                val hostPart = if (config.address.contains(":")) "[${config.address}]" else config.address
                val params = linkedMapOf<String, String>()
                if (config.protocol == ProtocolType.VLESS) {
                    params["encryption"] = config.security.ifBlank { "none" }
                }
                if (config.flow.isNotBlank()) params["flow"] = config.flow
                params["type"] = config.network.ifBlank { "tcp" }
                if (config.headerType.isNotBlank() && config.headerType != "none") params["headerType"] = config.headerType
                if (config.host.isNotBlank()) params["host"] = config.host
                if (config.path.isNotBlank()) {
                    if (config.network == "grpc") params["serviceName"] = config.path else params["path"] = config.path
                }
                if (config.network == "grpc" && config.grpcMode.isNotBlank()) params["mode"] = config.grpcMode
                if (config.streamSecurity.isNotBlank()) params["security"] = config.streamSecurity
                if (config.sni.isNotBlank()) params["sni"] = config.sni
                if (config.fingerprint.isNotBlank()) params["fp"] = config.fingerprint
                if (config.alpn.isNotBlank()) params["alpn"] = config.alpn
                if (config.allowInsecure) params["allowInsecure"] = "1"
                if (config.publicKey.isNotBlank()) params["pbk"] = config.publicKey
                if (config.shortId.isNotBlank()) params["sid"] = config.shortId
                if (config.spiderX.isNotBlank()) params["spx"] = config.spiderX
                val query = params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
                "$scheme://${urlEncode(config.passwordOrId)}@$hostPart:${config.port}?$query#${urlEncode(config.remarks)}"
            }

            ProtocolType.SHADOWSOCKS -> {
                val hostPart = if (config.address.contains(":")) "[${config.address}]" else config.address
                val user = "${config.security.ifBlank { "aes-256-gcm" }}:${config.passwordOrId}"
                val encodedUser = Base64.encodeToString(
                    user.toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
                )
                val pluginQuery = if (config.extraParam1.isNotBlank()) "?plugin=${urlEncode(config.extraParam1)}" else ""
                "ss://$encodedUser@$hostPart:${config.port}$pluginQuery#${urlEncode(config.remarks)}"
            }

            ProtocolType.SOCKS, ProtocolType.HTTP -> {
                val hostPart = if (config.address.contains(":")) "[${config.address}]" else config.address
                val auth = if (config.extraParam1.isNotBlank() || config.passwordOrId.isNotBlank()) {
                    val rawCred = "${config.extraParam1}:${config.passwordOrId}"
                    val b64 = Base64.encodeToString(rawCred.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    "$b64@"
                } else ""
                "${config.protocol.scheme}://$auth$hostPart:${config.port}#${urlEncode(config.remarks)}"
            }

            ProtocolType.WIREGUARD -> {
                val hostPart = if (config.address.contains(":")) "[${config.address}]" else config.address
                val params = linkedMapOf<String, String>()
                if (config.publicKey.isNotBlank()) params["publickey"] = config.publicKey
                if (config.extraParam1.isNotBlank()) params["address"] = config.extraParam1
                if (config.extraParam2.isNotBlank()) params["mtu"] = config.extraParam2
                if (config.extraParam3.isNotBlank()) params["reserved"] = config.extraParam3
                val query = params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
                "wireguard://${urlEncode(config.passwordOrId)}@$hostPart:${config.port}?$query#${urlEncode(config.remarks)}"
            }

            ProtocolType.HYSTERIA2 -> {
                val hostPart = if (config.address.contains(":")) "[${config.address}]" else config.address
                val params = linkedMapOf<String, String>()
                if (config.sni.isNotBlank()) params["sni"] = config.sni
                if (config.allowInsecure) params["insecure"] = "1"
                if (config.extraParam1.isNotBlank()) params["obfs"] = config.extraParam1
                if (config.extraParam2.isNotBlank()) params["obfs-password"] = config.extraParam2
                if (config.extraParam3.isNotBlank()) params["mport"] = config.extraParam3
                val query = params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
                "hysteria2://${urlEncode(config.passwordOrId)}@$hostPart:${config.port}?$query#${urlEncode(config.remarks)}"
            }

            ProtocolType.AMIR -> {
                val workerUrl = normalizeAmirWorkerUrl(config.extraParam1.ifBlank { config.address })
                val workerHost = Uri.parse(workerUrl).host ?: config.address
                val hostPart = if (workerHost.contains(":")) "[$workerHost]" else workerHost
                val params = linkedMapOf<String, String>()
                if (workerUrl.isNotBlank()) params["workerUrl"] = workerUrl
                if (config.extraParam2.isNotBlank()) params["cleanIp"] = config.extraParam2.trim()
                val query = params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
                "amir://${urlEncode(config.passwordOrId)}@$hostPart:${config.port}?$query#${urlEncode(config.remarks)}"
            }

            ProtocolType.CUSTOM -> config.rawCustomJson
        }
    }

    data class SubUserInfo(
        val uploadBytes: Long = -1L,
        val downloadBytes: Long = -1L,
        val totalBytes: Long = -1L,
        val expireTimestamp: Long = -1L
    )

    fun parseSubscriptionUserInfo(header: String?): SubUserInfo? {
        if (header.isNullOrBlank()) return null
        var upload = -1L
        var download = -1L
        var total = -1L
        var expire = -1L
        header.split(";").forEach { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2) {
                val value = kv[1].trim().toLongOrNull() ?: return@forEach
                when (kv[0].trim().lowercase()) {
                    "upload" -> upload = value
                    "download" -> download = value
                    "total" -> total = value
                    "expire" -> expire = value
                }
            }
        }
        return SubUserInfo(upload, download, total, expire)
    }

    private fun isLikelyUriList(text: String): Boolean {
        val prefixes = listOf("vmess://", "vless://", "trojan://", "ss://", "socks://", "socks5://", "http://", "https://", "wireguard://", "wg://", "hysteria2://", "hy2://", "amir://")
        return text.lines().any { line ->
            val l = line.trim().lowercase()
            prefixes.any { l.startsWith(it) }
        }
    }

    private fun decodeBase64Safe(input: String): String? {
        val clean = input.trim().replace("-", "+").replace("_", "/")
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        return runCatching {
            String(Base64.decode(padded, Base64.DEFAULT), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun urlEncode(value: String): String =
        runCatching { URLEncoder.encode(value, "UTF-8").replace("+", "%20") }.getOrDefault(value)
}

package com.example

import com.example.core.ConfigParser
import com.example.core.XrayConfigBuilder
import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.RoutingMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConfigParserTest {

    @Test
    fun `parse and build VLESS REALITY config`() {
        val uri = "vless://11111111-1111-1111-1111-111111111111@example.com:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.google.com&fp=chrome&pbk=pubkey123&sid=abcd&type=tcp#MyVless"
        val parsed = ConfigParser.parseSingleUri(uri)
        assertNotNull(parsed)
        assertEquals(ProtocolType.VLESS, parsed!!.protocol)
        assertEquals("MyVless", parsed.remarks)
        assertEquals("example.com", parsed.address)
        assertEquals(443, parsed.port)
        assertEquals("reality", parsed.streamSecurity)
        assertEquals("xtls-rprx-vision", parsed.flow)
        assertEquals("pubkey123", parsed.publicKey)

        val jsonStr = XrayConfigBuilder.buildFullConfig(
            server = parsed,
            settings = AppSettings(vpnMode = true, routingMode = RoutingMode.BYPASS_LAN_AND_IR),
            customRules = emptyList()
        )
        val root = JSONObject(jsonStr)
        assertTrue(root.has("inbounds"))
        assertTrue(root.has("outbounds"))
        assertTrue(root.has("routing"))
        assertEquals("tun", root.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
    }

    @Test
    fun `parse Shadowsocks SIP002 and WireGuard and Hysteria2`() {
        val ssUri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@1.2.3.4:8388#MySS"
        val ss = ConfigParser.parseSingleUri(ssUri)
        assertNotNull(ss)
        assertEquals(ProtocolType.SHADOWSOCKS, ss!!.protocol)
        assertEquals("aes-256-gcm", ss.security)
        assertEquals("password", ss.passwordOrId)

        val wgUri = "wireguard://privkey==@5.6.7.8:51820?address=10.0.0.2/32&publickey=pubkey==&mtu=1420#MyWG"
        val wg = ConfigParser.parseSingleUri(wgUri)
        assertNotNull(wg)
        assertEquals(ProtocolType.WIREGUARD, wg!!.protocol)
        assertEquals(51820, wg.port)

        val hy2Uri = "hysteria2://secret@hy.example.com:443?sni=hy.example.com&insecure=1#MyHY2"
        val hy2 = ConfigParser.parseSingleUri(hy2Uri)
        assertNotNull(hy2)
        assertEquals(ProtocolType.HYSTERIA2, hy2!!.protocol)
        assertTrue(hy2.allowInsecure)
    }

    @Test
    fun `parse subscription userinfo header`() {
        val info = ConfigParser.parseSubscriptionUserInfo("upload=1024; download=2048; total=1048576; expire=1799999999")
        assertNotNull(info)
        assertEquals(1024L, info!!.uploadBytes)
        assertEquals(2048L, info.downloadBytes)
        assertEquals(1048576L, info.totalBytes)
        assertEquals(1799999999L, info.expireTimestamp)
    }

    @Test
    fun `parse share and build Amir protocol config`() {
        val uri = "amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev:443?workerUrl=wss%3A%2F%2Ftwilight-dream-9e3c.aliahmadiphotoofficial.workers.dev%2F&cleanIp=172.67.70.244#AmirNode"
        val parsed = ConfigParser.parseSingleUri(uri)
        assertNotNull(parsed)
        assertEquals(ProtocolType.AMIR, parsed!!.protocol)
        assertEquals("AmirNode", parsed.remarks)
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", parsed.passwordOrId)
        assertEquals("wss://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/", parsed.extraParam1)
        assertEquals("172.67.70.244", parsed.extraParam2)

        val exported = ConfigParser.toShareUri(parsed)
        val reparsed = ConfigParser.parseSingleUri(exported)
        assertNotNull(reparsed)
        assertEquals(parsed.passwordOrId, reparsed!!.passwordOrId)
        assertEquals(parsed.extraParam2, reparsed.extraParam2)

        val jsonStr = XrayConfigBuilder.buildFullConfig(
            server = parsed,
            settings = AppSettings(
                vpnMode = true,
                localDnsEnabled = true,
                fakeDnsEnabled = true,
                routeOnly = true,
                remoteDns = "https://1.1.1.1/dns-query"
            ),
            customRules = emptyList(),
            amirLocalPort = 40446
        )
        val root = JSONObject(jsonStr)
        assertTrue(root.has("dns"))
        assertTrue(root.has("fakedns"))
        val dns = root.getJSONObject("dns")
        assertEquals("dns-internal", dns.getString("tag"))
        assertEquals("UseIPv4", dns.getString("queryStrategy"))
        val dnsServers = dns.getJSONArray("servers")
        assertEquals("fakedns", dnsServers.getString(0))
        // User's configured remoteDns must never be silently overwritten
        assertEquals("https://1.1.1.1/dns-query", dnsServers.getString(1))

        val tunSniff = root.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing")
        assertTrue(tunSniff.getBoolean("enabled"))
        assertEquals(false, tunSniff.getBoolean("routeOnly"))
        assertEquals("fakedns", tunSniff.getJSONArray("destOverride").getString(0))

        val routing = root.getJSONObject("routing")
        assertEquals("IPIfNonMatch", routing.getString("domainStrategy"))
        val rules = routing.getJSONArray("rules")
        var dnsDirectIndex = -1
        var dnsProxyIndex = -1
        var udpBlockIndex = -1
        var hasFakeDnsIpRoute = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            val inboundTag = r.optJSONArray("inboundTag")
            if (inboundTag != null && inboundTag.optString(0) == "dns-internal") {
                when (r.optString("outboundTag")) {
                    "direct" -> dnsDirectIndex = i
                    "proxy" -> dnsProxyIndex = i
                }
            }
            if (r.optString("network") == "udp" && r.optString("outboundTag") == "block") {
                udpBlockIndex = i
            }
            val ips = r.optJSONArray("ip")
            if (ips != null && ips.optString(0) == "198.18.0.0/15" && r.optString("outboundTag") == "proxy") {
                hasFakeDnsIpRoute = true
            }
        }
        assertTrue("Expected dns-internal direct rule before UDP block", dnsDirectIndex in 0 until udpBlockIndex)
        assertTrue("Expected dns-internal proxy rule before UDP block", dnsProxyIndex in 0 until udpBlockIndex)
        assertTrue(hasFakeDnsIpRoute)

        val proxyOutbound = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("socks", proxyOutbound.getString("protocol"))
        assertEquals(40446, proxyOutbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0).getInt("port"))
    }

    @Test
    fun `verify Amir init packet XOR and HMAC format`() {
        val uuidBytes = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd".toByteArray()
        val (packet, key) = com.example.core.AmirLocalProxy.buildInitPacket(uuidBytes, "1.1.1.1", 443)
        // nonce(16) + [cmd(1)+port(2)+atyp(1)+ipv4(4)](8) + hmac(8) = 32 bytes
        assertEquals(32, packet.size)
        val nonce = packet.copyOfRange(0, 16)
        assertEquals(16, key.size)
        assertTrue(com.example.core.AmirLocalProxy.deriveKey(uuidBytes, nonce).contentEquals(key))
        val enc = packet.copyOfRange(16, 24)
        val mac = packet.copyOfRange(24, 32)
        val expectedMac = com.example.core.AmirLocalProxy.hmacSha256(key, enc).copyOfRange(0, 8)
        assertTrue(mac.contentEquals(expectedMac))
        val dec = com.example.core.AmirLocalProxy.xorBytes(enc, key)
        assertEquals(1, dec[0].toInt())
        assertEquals(1, dec[3].toInt()) // IPv4 type = 1
    }

    @Test
    fun `AmirLocalProxy relays SOCKS5 traffic over ws loopback`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val serverSock = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = serverSock.localPort

        val serverThread = Thread {
            val client = serverSock.accept()
            client.use { s ->
                val inp = java.io.BufferedInputStream(s.getInputStream())
                val out = s.getOutputStream()
                var wsKey = ""
                while (true) {
                    val sb = StringBuilder()
                    while (true) {
                        val c = inp.read()
                        if (c < 0 || c == '\n'.code) break
                        if (c != '\r'.code) sb.append(c.toChar())
                    }
                    val line = sb.toString()
                    if (line.isEmpty()) break
                    if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                        wsKey = line.substringAfter(':').trim()
                    }
                }
                val sha1 = java.security.MessageDigest.getInstance("SHA-1")
                    .digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
                val accept = android.util.Base64.encodeToString(sha1, android.util.Base64.NO_WRAP)
                val resp = (
                    "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n" +
                        "Sec-WebSocket-Protocol: chat.v2\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
                out.write(resp)
                out.flush()

                fun readFrame(): ByteArray {
                    val b0 = inp.read()
                    val b1 = inp.read()
                    val masked = (b1 and 0x80) != 0
                    val lenByte = b1 and 0x7F
                    val len = when (lenByte) {
                        126 -> ((inp.read() shl 8) or inp.read())
                        else -> lenByte
                    }
                    val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                    val data = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                    if (mask != null) {
                        for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                    }
                    return data
                }

                fun writeFrame(data: ByteArray) {
                    out.write(0x82)
                    if (data.size <= 125) {
                        out.write(data.size)
                    } else {
                        out.write(126)
                        out.write((data.size ushr 8) and 0xFF)
                        out.write(data.size and 0xFF)
                    }
                    out.write(data)
                    out.flush()
                }

                val init = readFrame()
                val nonce = init.copyOfRange(0, 16)
                val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, nonce)
                val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                    .copyOfRange(0, 16)
                writeFrame(com.example.core.AmirLocalProxy.xorBytes(expectedAck, key))

                val reqData = com.example.core.AmirLocalProxy.xorBytes(readFrame(), key)
                val reply = ("ECHO:" + String(reqData, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                writeFrame(com.example.core.AmirLocalProxy.xorBytes(reply, key))
                out.write(byteArrayOf(0x88.toByte(), 0x00))
                out.flush()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "TestAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = wsPort,
            passwordOrId = uuid,
            extraParam1 = "ws://127.0.0.1:$wsPort/",
            extraParam2 = ""
        )
        val proxy = com.example.core.AmirLocalProxy(cfg)
        val localPort = proxy.start()
        try {
            java.net.Socket("127.0.0.1", localPort).use { sock ->
                sock.soTimeout = 5000
                val out = sock.getOutputStream()
                val inp = java.io.DataInputStream(sock.getInputStream())
                // SOCKS5 greeting
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val g = ByteArray(2)
                inp.readFully(g)
                assertEquals(5, g[0].toInt())
                assertEquals(0, g[1].toInt())

                // SOCKS5 CONNECT example.com:443
                val host = "example.com".toByteArray(Charsets.UTF_8)
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()) + host + byteArrayOf(0x01, 0xBB.toByte()))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())

                out.write("PING".toByteArray(Charsets.UTF_8))
                out.flush()
                val buf = ByteArray(64)
                val n = inp.read(buf)
                assertTrue(n > 0)
                assertEquals("ECHO:PING", String(buf, 0, n, Charsets.UTF_8))
            }
        } finally {
            proxy.stop()
            serverSock.close()
        }
    }

    @Test
    fun `buildFullConfig wires Core, Mux, Fragment, Observatory, DNS Hosts, and VPN MTU settings`() {
        val vlessUri = "vless://11111111-1111-1111-1111-111111111111@vpn.example.com:443?encryption=none&security=tls&sni=vpn.example.com&type=tcp#EdgeNode"
        val server = ConfigParser.parseSingleUri(vlessUri)!!

        val settings = AppSettings(
            vpnMode = true,
            vpnMtu = 1400,
            enableIpv6 = true,
            preferIpv6 = true,
            localDnsEnabled = true,
            fakeDnsEnabled = true,
            localProxyEnabled = true,
            socks5UdpEnabled = false,
            localProxyUsername = "admin",
            localProxyPassword = "secret",
            dnsHosts = "example.com:1.2.3.4,domain:test.org:9.9.9.9,v6.org:2001:db8::1,multi.com:1.1.1.1;1.0.0.1",
            outboundDomainResolve = AppSettings.OUTBOUND_AS_IS,
            muxEnabled = true,
            muxConcurrency = -1,
            muxXudpConcurrency = 16,
            muxXudpProxyUdp443 = "skip",
            fragmentEnabled = true,
            fragmentPackets = "1-3",
            fragmentLength = "50-100",
            fragmentInterval = "10-20",
            fragmentMaxSplit = "5-10",
            observatoryMode = AppSettings.OBSERVATORY_LEAST_LOAD,
            burstObservatoryInterval = "2m",
            burstObservatoryHttpMethod = "GET",
            burstObservatorySampling = 4,
            burstObservatoryTimeout = "15s",
            delayTestUrl = "https://cp.cloudflare.com/generate_204"
        )

        val jsonStr = XrayConfigBuilder.buildFullConfig(
            server = server,
            settings = settings,
            customRules = emptyList(),
            resolvedServerHosts = mapOf("vpn.example.com" to listOf("203.0.113.10")),
            runtimeSocksPort = 41001,
            runtimeHttpPort = 41002
        )
        val root = JSONObject(jsonStr)

        val inbounds = root.getJSONArray("inbounds")
        assertEquals(3, inbounds.length())
        assertEquals(1400, inbounds.getJSONObject(0).getJSONObject("settings").getInt("MTU"))

        val socksInbound = inbounds.getJSONObject(1)
        assertEquals(41001, socksInbound.getInt("port"))
        val socksSettings = socksInbound.getJSONObject("settings")
        assertEquals("password", socksSettings.getString("auth"))
        assertEquals(false, socksSettings.getBoolean("udp"))
        assertEquals("admin", socksSettings.getJSONArray("accounts").getJSONObject(0).getString("user"))
        assertEquals("secret", socksSettings.getJSONArray("accounts").getJSONObject(0).getString("pass"))

        val httpInbound = inbounds.getJSONObject(2)
        assertEquals(41002, httpInbound.getInt("port"))
        assertEquals("admin", httpInbound.getJSONObject("settings").getJSONArray("accounts").getJSONObject(0).getString("user"))

        val dns = root.getJSONObject("dns")
        assertEquals("UseIP", dns.getString("queryStrategy"))
        val hosts = dns.getJSONObject("hosts")
        assertEquals("203.0.113.10", hosts.getString("vpn.example.com"))
        assertEquals("1.2.3.4", hosts.getString("example.com"))
        assertEquals("9.9.9.9", hosts.getString("domain:test.org"))
        assertEquals("2001:db8::1", hosts.getString("v6.org"))
        assertEquals("1.1.1.1", hosts.getJSONArray("multi.com").getString(0))
        assertEquals("1.0.0.1", hosts.getJSONArray("multi.com").getString(1))

        val outbounds = root.getJSONArray("outbounds")
        val proxyMux = outbounds.getJSONObject(0).getJSONObject("mux")
        assertTrue(proxyMux.getBoolean("enabled"))
        assertEquals(-1, proxyMux.getInt("concurrency"))
        assertEquals(16, proxyMux.getInt("xudpConcurrency"))
        assertEquals("skip", proxyMux.getString("xudpProxyUDP443"))

        val fragmentObj = outbounds.getJSONObject(1).getJSONObject("settings").getJSONObject("fragment")
        assertEquals("1-3", fragmentObj.getString("packets"))
        assertEquals("50-100", fragmentObj.getString("length"))
        assertEquals("5-10", fragmentObj.getString("maxSplit"))

        val directOutbound = outbounds.getJSONObject(2)
        assertEquals(
            "AsIs",
            directOutbound.getJSONObject("streamSettings").getJSONObject("sockopt").getString("domainStrategy")
        )
        val proxySockopt = outbounds.getJSONObject(0).getJSONObject("streamSettings").getJSONObject("sockopt")
        assertEquals("AsIs", proxySockopt.getString("domainStrategy"))
        assertEquals("fragment", proxySockopt.getString("dialerProxy"))

        assertTrue(root.has("burstObservatory"))
        assertEquals(false, root.has("observatory"))
        val pingConfig = root.getJSONObject("burstObservatory").getJSONObject("pingConfig")
        assertEquals("GET", pingConfig.getString("httpMethod"))
        assertEquals("2m", pingConfig.getString("interval"))
        assertEquals(4, pingConfig.getInt("sampling"))
        assertEquals("15s", pingConfig.getString("timeout"))
        assertEquals("https://cp.cloudflare.com/generate_204", pingConfig.getString("destination"))

        val fakeDns = root.getJSONArray("fakedns")
        assertEquals(2, fakeDns.length())
        assertEquals("fc00::/18", fakeDns.getJSONObject(1).getString("ipPool"))
    }

    @Test
    fun `buildFullConfig omits local proxy inbounds when disabled in VPN mode and supports leastPing observatory`() {
        val vlessUri = "vless://11111111-1111-1111-1111-111111111111@vpn.example.com:443?encryption=none&security=tls&type=tcp#Node"
        val server = ConfigParser.parseSingleUri(vlessUri)!!

        val settings = AppSettings(
            vpnMode = true,
            localProxyEnabled = false,
            observatoryMode = AppSettings.OBSERVATORY_LEAST_PING,
            observatoryInterval = "4m",
            delayTestUrl = "https://example.org/generate_204"
        )
        val root = JSONObject(XrayConfigBuilder.buildFullConfig(server, settings, emptyList()))
        val inbounds = root.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("tun", inbounds.getJSONObject(0).getString("protocol"))
        assertTrue(root.has("observatory"))
        assertEquals(false, root.has("burstObservatory"))
        val obs = root.getJSONObject("observatory")
        assertEquals("4m", obs.getString("probeInterval"))
        assertEquals("https://example.org/generate_204", obs.getString("probeURL"))
        assertFalse(obs.has("probeUrl"))
    }

    @Test
    fun `bypassLan false omits geoip private rule across routing modes and socks skips fragment on loopback`() {
        val vlessServer = ConfigParser.parseSingleUri(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example.com:443?encryption=none&security=tls&type=tcp#Node"
        )!!
        listOf(
            RoutingMode.BYPASS_LAN,
            RoutingMode.BYPASS_LAN_AND_IR,
            RoutingMode.BYPASS_LAN_AND_MAINLAND,
            RoutingMode.GLOBAL_PROXY
        ).forEach { mode ->
            val root = JSONObject(
                XrayConfigBuilder.buildFullConfig(
                    server = vlessServer,
                    settings = AppSettings(routingMode = mode, bypassLan = false),
                    customRules = emptyList()
                )
            )
            val rules = root.getJSONObject("routing").getJSONArray("rules")
            for (i in 0 until rules.length()) {
                val ips = rules.getJSONObject(i).optJSONArray("ip") ?: continue
                for (j in 0 until ips.length()) {
                    assertFalse(
                        "Found geoip:private even though bypassLan=false in $mode",
                        ips.getString(j) == "geoip:private"
                    )
                }
            }
        }

        val loopbackSocks = ConfigParser.parseSingleUri("socks://127.0.0.1:40446#LocalSocks")!!
        val socksRoot = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                server = loopbackSocks,
                settings = AppSettings(fragmentEnabled = true, outboundDomainResolve = AppSettings.OUTBOUND_USE_IPV4),
                customRules = emptyList()
            )
        )
        val socksSockopt = socksRoot.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getJSONObject("sockopt")
        assertEquals("UseIPv4", socksSockopt.getString("domainStrategy"))
        assertFalse(socksSockopt.has("dialerProxy"))
    }

    @Test
    fun `affectsActiveTunnel distinguishes UI preferences from connection settings`() {
        val base = AppSettings()
        assertFalse(
            AppSettings.affectsActiveTunnel(
                base,
                base.copy(
                    speedDisplayEnabled = !base.speedDisplayEnabled,
                    twoColumnLayout = !base.twoColumnLayout,
                    confirmDeleteConfig = !base.confirmDeleteConfig
                )
            )
        )
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(vpnMtu = 1400)))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(localDnsEnabled = !base.localDnsEnabled)))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(fakeDnsEnabled = !base.fakeDnsEnabled)))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(remoteDns = "https://cloudflare-dns.com/dns-query")))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(fragmentEnabled = !base.fragmentEnabled)))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(muxEnabled = !base.muxEnabled)))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(bypassLan = !base.bypassLan)))
        assertTrue(AppSettings.affectsActiveTunnel(base, base.copy(perAppProxyEnabled = !base.perAppProxyEnabled)))
    }

    @Test
    fun `AmirLocalProxy honors dnsHosts mapping for unresolvable worker domain`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val serverSock = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = serverSock.localPort

        val serverThread = Thread {
            val client = serverSock.accept()
            client.use { s ->
                val inp = java.io.BufferedInputStream(s.getInputStream())
                val out = s.getOutputStream()
                var wsKey = ""
                while (true) {
                    val sb = StringBuilder()
                    while (true) {
                        val c = inp.read()
                        if (c < 0 || c == '\n'.code) break
                        if (c != '\r'.code) sb.append(c.toChar())
                    }
                    val line = sb.toString()
                    if (line.isEmpty()) break
                    if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                        wsKey = line.substringAfter(':').trim()
                    }
                }
                val sha1 = java.security.MessageDigest.getInstance("SHA-1")
                    .digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
                val accept = android.util.Base64.encodeToString(sha1, android.util.Base64.NO_WRAP)
                out.write(
                    (
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: $accept\r\n" +
                            "Sec-WebSocket-Protocol: chat.v2\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                )
                out.flush()

                fun readFrame(): ByteArray {
                    inp.read()
                    val b1 = inp.read()
                    val masked = (b1 and 0x80) != 0
                    val len = b1 and 0x7F
                    val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                    val data = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                    if (mask != null) {
                        for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                    }
                    return data
                }

                fun writeFrame(data: ByteArray) {
                    out.write(0x82)
                    out.write(data.size)
                    out.write(data)
                    out.flush()
                }

                val init = readFrame()
                val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                    .copyOfRange(0, 16)
                writeFrame(com.example.core.AmirLocalProxy.xorBytes(expectedAck, key))
                val reqData = com.example.core.AmirLocalProxy.xorBytes(readFrame(), key)
                writeFrame(com.example.core.AmirLocalProxy.xorBytes(("DNSOK:" + String(reqData)).toByteArray(), key))
                out.write(byteArrayOf(0x88.toByte(), 0x00))
                out.flush()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "DnsMappedAmir",
            protocol = ProtocolType.AMIR,
            address = "worker.invalid",
            port = wsPort,
            passwordOrId = uuid,
            extraParam1 = "ws://worker.invalid:$wsPort/",
            extraParam2 = ""
        )
        val settings = AppSettings(
            dnsHosts = "worker.invalid:127.0.0.1",
            outboundDomainResolve = AppSettings.OUTBOUND_USE_IPV4,
            preferIpv6 = false
        )
        val proxy = com.example.core.AmirLocalProxy(serverConfig = cfg, settings = settings)
        val localPort = proxy.start()
        try {
            java.net.Socket("127.0.0.1", localPort).use { sock ->
                sock.soTimeout = 5000
                val out = sock.getOutputStream()
                val inp = java.io.DataInputStream(sock.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                val host = "example.com".toByteArray(Charsets.UTF_8)
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()) + host + byteArrayOf(0x00, 0x50))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())
                out.write("HI".toByteArray())
                out.flush()
                val buf = ByteArray(32)
                val n = inp.read(buf)
                assertTrue(n > 0)
                assertEquals("DNSOK:HI", String(buf, 0, n))
            }
        } finally {
            proxy.stop()
            serverSock.close()
        }
    }
}

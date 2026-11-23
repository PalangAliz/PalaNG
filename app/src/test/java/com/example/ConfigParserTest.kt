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
            amirLocalPort = 40446,
            amirDnsPort = 55353
        )
        assertFalse("AMIR config must not depend on unbundled .dat files", jsonStr.contains("ext:geoip"))
        assertFalse("AMIR config must not depend on unbundled geosite rules", jsonStr.contains("geosite:"))

        val root = JSONObject(jsonStr)
        assertTrue(root.has("dns"))
        assertTrue(root.has("fakedns"))
        val dns = root.getJSONObject("dns")
        assertEquals("dns-module", dns.getString("tag"))
        val dnsServers = dns.getJSONArray("servers")
        assertFalse("AMIR dns.servers must not use Alibaba DNS", dnsServers.toString().contains("223.5.5.5"))
        assertFalse("AMIR dns.servers must not use cloudflare-dns.com DoH", dnsServers.toString().contains("cloudflare-dns.com"))
        assertEquals("fakedns", dnsServers.getString(0))
        val amirDnsObj = dnsServers.getJSONObject(1)
        assertEquals("127.0.0.1", amirDnsObj.getString("address"))
        assertEquals(55353, amirDnsObj.getInt("port"))
        assertEquals("amir-dns", amirDnsObj.getString("tag"))
        assertTrue(amirDnsObj.getBoolean("skipFallback"))
        val dnsHosts = dns.getJSONObject("hosts")
        assertEquals("8.8.8.8", dnsHosts.getJSONArray("dns.google").getString(0))

        // In vpnMode=true, AMIR Xray config opens ONLY the tun inbound so it never collides with AmirLocalProxy ports
        val inbounds = root.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("tun", inbounds.getJSONObject(0).getString("protocol"))
        val tunSniff = inbounds.getJSONObject(0).getJSONObject("sniffing")
        assertTrue(tunSniff.getBoolean("enabled"))
        assertEquals(false, tunSniff.getBoolean("routeOnly"))
        val destOverrideList = (0 until tunSniff.getJSONArray("destOverride").length()).map {
            tunSniff.getJSONArray("destOverride").getString(it)
        }
        assertEquals(listOf("http", "tls", "quic", "fakedns"), destOverrideList)

        val routing = root.getJSONObject("routing")
        assertEquals("AsIs", routing.getString("domainStrategy"))
        val rules = routing.getJSONArray("rules")
        var dnsOutIndex = -1
        var amirDnsDirectIndex = -1
        var udpBlockIndex = -1
        var fakeIpProxyIndex = -1
        var lanCidrDirectIndex = -1
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (r.optString("port") == "53" && r.optString("outboundTag") == "dns-out") {
                dnsOutIndex = i
            }
            val inboundTag = r.optJSONArray("inboundTag")
            if (inboundTag != null && inboundTag.optString(0) == "amir-dns" && r.optString("outboundTag") == "direct") {
                amirDnsDirectIndex = i
            }
            if (r.optString("network") == "udp" && r.optString("outboundTag") == "block") {
                udpBlockIndex = i
            }
            val ips = r.optJSONArray("ip")
            if (ips != null && ips.optString(0) == "198.18.0.0/15" && r.optString("outboundTag") == "proxy") {
                fakeIpProxyIndex = i
            }
            if (ips != null && ips.optString(0) == "10.0.0.0/8" && r.optString("outboundTag") == "direct") {
                lanCidrDirectIndex = i
            }
        }
        assertTrue("Expected port 53 -> dns-out rule", dnsOutIndex >= 0)
        assertTrue("Expected amir-dns -> direct rule", amirDnsDirectIndex >= 0)
        assertTrue("Expected non-DNS udp block rule", udpBlockIndex >= 0)
        assertTrue("Expected FakeDNS 198.18.0.0/15 -> proxy rule", fakeIpProxyIndex >= 0)
        assertTrue("Expected pure CIDR LAN bypass -> direct rule", lanCidrDirectIndex >= 0)

        val outbounds = root.getJSONArray("outbounds")
        val proxyOutbound = outbounds.getJSONObject(0)
        assertEquals("socks", proxyOutbound.getString("protocol"))
        val amirSettings = proxyOutbound.getJSONObject("settings")
        assertEquals("127.0.0.1", amirSettings.getString("address"))
        assertEquals(8, amirSettings.getInt("level"))
        assertEquals(40446, amirSettings.getInt("port"))
        assertEquals("tcp", proxyOutbound.getJSONObject("streamSettings").getString("network"))
        assertEquals(-1, proxyOutbound.getJSONObject("mux").getInt("concurrency"))
        assertFalse(proxyOutbound.getJSONObject("mux").getBoolean("enabled"))

        val directOutbound = outbounds.getJSONObject(1)
        assertEquals("direct", directOutbound.getString("tag"))
        assertEquals("tcp", directOutbound.getJSONObject("streamSettings").getString("network"))
        assertEquals(2, directOutbound.getJSONObject("streamSettings").getJSONObject("sockopt").getJSONObject("happyEyeballs").getInt("interleave"))
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

    @Test
    fun `Amir full config isolates standalone AMIR core and feeds SOCKS5 output into Xray without fragment or mux interference`() {
        val amirServer = ConfigParser.parseSingleUri(
            "amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@twilight-dream.workers.dev:443?cleanIp=172.67.70.244#AmirStandalone"
        )!!
        val settings = AppSettings(
            vpnMode = true,
            localDnsEnabled = true,
            fakeDnsEnabled = true,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "80-160",
            muxEnabled = true,
            muxConcurrency = 8,
            outboundDomainResolve = AppSettings.OUTBOUND_USE_IPV4
        )
        val root = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                server = amirServer,
                settings = settings,
                customRules = emptyList(),
                amirLocalPort = 40446
            )
        )

        val inbounds = root.getJSONArray("inbounds")
        for (i in 0 until inbounds.length()) {
            assertFalse(
                "Xray must not create amir-upstream inbound for isolated AMIR core",
                inbounds.getJSONObject(i).optString("tag") == "amir-upstream"
            )
        }

        val outbounds = root.getJSONArray("outbounds")
        var proxyOutbound: JSONObject? = null
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.getJSONObject(i)
            val tag = ob.optString("tag")
            assertFalse("Xray must not create fragment outbound for AMIR", tag == "fragment")
            assertFalse("Xray must not create amir-out freedom outbound for AMIR", tag == "amir-out")
            if (tag == "proxy") proxyOutbound = ob
        }
        assertNotNull("Expected Xray 'proxy' SOCKS outbound pointing to standalone AMIR core", proxyOutbound)
        assertEquals("socks", proxyOutbound!!.getString("protocol"))
        assertFalse("Xray must keep mux disabled for AMIR SOCKS outbound", proxyOutbound.getJSONObject("mux").getBoolean("enabled"))
        assertFalse(
            "Xray must not attach dialerProxy/fragment sockopt to AMIR SOCKS outbound",
            proxyOutbound.getJSONObject("streamSettings").has("sockopt")
        )
        val proxySettings = proxyOutbound.getJSONObject("settings")
        assertEquals("127.0.0.1", proxySettings.getString("address"))
        assertEquals(8, proxySettings.getInt("level"))
        assertEquals(40446, proxySettings.getInt("port"))

        val delayRoot = JSONObject(
            XrayConfigBuilder.buildOutboundDelayTestJson(
                server = amirServer,
                settings = settings,
                amirLocalPort = 41234
            )
        )
        val delayProxy = delayRoot.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("socks", delayProxy.getString("protocol"))
        assertEquals(41234, delayProxy.getJSONObject("settings").getInt("port"))
    }

    @Test
    fun `AmirLocalProxy runs standalone and ignores Xray fragment or mux settings`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)

        val wsServer = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = wsServer.localPort
        val wsThread = Thread {
            wsServer.accept().use { s ->
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
                writeFrame(com.example.core.AmirLocalProxy.xorBytes(("ISO:" + String(reqData)).toByteArray(), key))
                out.write(byteArrayOf(0x88.toByte(), 0x00))
                out.flush()
            }
        }
        wsThread.isDaemon = true
        wsThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "StandaloneAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = wsPort,
            passwordOrId = uuid,
            extraParam1 = "ws://127.0.0.1:$wsPort/",
            extraParam2 = ""
        )
        val settings = AppSettings(
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "1-2",
            muxEnabled = true,
            muxConcurrency = 16
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
                out.write("OK".toByteArray())
                out.flush()
                val buf = ByteArray(32)
                val n = inp.read(buf)
                assertTrue(n > 0)
                assertEquals("ISO:OK", String(buf, 0, n))
            }
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }

    @Test
    fun `AmirLocalProxy matches cl4 defaults, rejects unsupported auth, and accepts padded ACK`() {
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", com.example.core.AmirLocalProxy.DEFAULT_UUID)
        assertEquals("wss://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/", com.example.core.AmirLocalProxy.DEFAULT_WORKER_URL)
        assertEquals("172.67.70.244", com.example.core.AmirLocalProxy.DEFAULT_CLEAN_IP)
        assertEquals(16384, com.example.core.AmirLocalProxy.MAX_CHUNK)

        val uuidBytes = com.example.core.AmirLocalProxy.DEFAULT_UUID.toByteArray(Charsets.UTF_8)
        val serverSock = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = serverSock.localPort
        val verifiedPaddedAck = java.util.concurrent.CountDownLatch(1)

        val serverThread = Thread {
            serverSock.accept().use { s ->
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

                fun readWsFrame(): Pair<Int, ByteArray> {
                    val b0 = inp.read()
                    val b1 = inp.read()
                    val opcode = b0 and 0x0F
                    val masked = (b1 and 0x80) != 0
                    val len = b1 and 0x7F
                    val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                    val data = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                    if (mask != null) {
                        for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                    }
                    return opcode to data
                }

                val (_, init) = readWsFrame()
                val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                    .copyOfRange(0, 16)
                // Send 20-byte ACK (>=16 bytes) matching spec
                val paddedAckPlain = expectedAck + byteArrayOf(0x01, 0x02, 0x03, 0x04)
                val encAck = com.example.core.AmirLocalProxy.xorBytes(paddedAckPlain, key)
                out.write(0x82)
                out.write(encAck.size)
                out.write(encAck)
                out.flush()

                val (_, reqPayload) = readWsFrame()
                val reqText = String(com.example.core.AmirLocalProxy.xorBytes(reqPayload, key))
                if (reqText == "PING") {
                    verifiedPaddedAck.countDown()
                    val encResp = com.example.core.AmirLocalProxy.xorBytes("PONG".toByteArray(), key)
                    out.write(0x82)
                    out.write(encResp.size)
                    out.write(encResp)
                    out.write(byteArrayOf(0x88.toByte(), 0x00))
                    out.flush()
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "Cl4ParityAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = wsPort,
            passwordOrId = com.example.core.AmirLocalProxy.DEFAULT_UUID,
            extraParam1 = "ws://127.0.0.1:$wsPort/",
            extraParam2 = ""
        )
        val proxy = com.example.core.AmirLocalProxy(cfg)
        val localPort = proxy.start()
        try {
            // 1. Reject auth method 0x02 (username/password only) with 0x05 0xFF
            java.net.Socket("127.0.0.1", localPort).use { badAuth ->
                badAuth.soTimeout = 3000
                badAuth.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x02))
                badAuth.getOutputStream().flush()
                val resp = ByteArray(2)
                java.io.DataInputStream(badAuth.getInputStream()).readFully(resp)
                assertEquals(0x05, resp[0].toInt() and 0xFF)
                assertEquals(0xFF, resp[1].toInt() and 0xFF)
            }

            // 2. Accept >=16-byte ACK and relay data cleanly
            java.net.Socket("127.0.0.1", localPort).use { sock ->
                sock.soTimeout = 3000
                val out = sock.getOutputStream()
                val inp = java.io.DataInputStream(sock.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 8, 8, 8, 8, 0x01, 0xBB.toByte()))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())
                out.write("PING".toByteArray())
                out.flush()
                val body = ByteArray(4)
                inp.readFully(body)
                assertEquals("PONG", String(body))
            }
            assertTrue(
                "Expected padded ACK tunnel to relay request cleanly",
                verifiedPaddedAck.await(3, java.util.concurrent.TimeUnit.SECONDS)
            )
        } finally {
            proxy.stop()
            serverSock.close()
        }
    }

    @Test
    fun `AppSettings keeps DNS switches unlocked while AMIR emits exact v2rayNG DNS and routing schema`() {
        val defaults = AppSettings()
        assertFalse(defaults.localDnsEnabled)
        assertFalse(defaults.fakeDnsEnabled)
        assertEquals("https://1.1.1.1/dns-query", defaults.remoteDns)

        // Non-AMIR protocols continue to respect default localDnsEnabled=false / fakeDnsEnabled=false
        val vlessServer = ConfigParser.parseSingleUri(
            "vless://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@example.com:443?security=tls&type=ws#VlessDefault"
        )!!
        val vlessDefaultRoot = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                server = vlessServer,
                settings = defaults,
                customRules = emptyList()
            )
        )
        assertFalse("Non-AMIR must not inject dns when localDnsEnabled=false and fakeDnsEnabled=false", vlessDefaultRoot.has("dns"))
        assertFalse(vlessDefaultRoot.has("fakedns"))

        val amirServer = ConfigParser.parseSingleUri(
            "amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@twilight-dream.workers.dev:443?cleanIp=172.67.70.244#AmirNode"
        )!!
        val amirDirectRoot = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                server = amirServer,
                settings = defaults.copy(vpnMode = true),
                customRules = emptyList(),
                amirLocalPort = 40446
            )
        )
        assertFalse("AMIR must respect fakeDnsEnabled=false", amirDirectRoot.has("fakedns"))
        assertFalse("AMIR must respect localDnsEnabled=false & fakeDnsEnabled=false", amirDirectRoot.has("dns"))

        val amirRoot = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                server = amirServer,
                settings = defaults.copy(vpnMode = true, localDnsEnabled = true, fakeDnsEnabled = true),
                customRules = emptyList(),
                amirLocalPort = 40446,
                amirDnsPort = 0
            )
        )
        assertTrue(amirRoot.has("dns"))
        assertTrue(amirRoot.has("fakedns"))
        assertTrue(amirRoot.has("stats"))
        assertTrue(amirRoot.has("policy"))

        val fakeDnsArr = amirRoot.getJSONArray("fakedns")
        assertEquals("198.18.0.0/15", fakeDnsArr.getJSONObject(0).getString("ipPool"))
        assertEquals(10000, fakeDnsArr.getJSONObject(0).getInt("poolSize"))

        val dnsObj = amirRoot.getJSONObject("dns")
        assertEquals("dns-module", dnsObj.getString("tag"))
        assertEquals("UseIPv4", dnsObj.getString("queryStrategy"))
        assertEquals("8.8.8.8", dnsObj.getJSONObject("hosts").getJSONArray("dns.google").getString(0))
        val serversArr = dnsObj.getJSONArray("servers")
        assertEquals("fakedns", serversArr.getString(0))
        assertEquals("https://dns.google/dns-query", serversArr.getString(1))

        val routingObj = amirRoot.getJSONObject("routing")
        assertEquals("AsIs", routingObj.getString("domainStrategy"))
        val amirRules = routingObj.getJSONArray("rules")
        assertEquals("dns-out", amirRules.getJSONObject(0).getString("outboundTag"))
        assertEquals("53", amirRules.getJSONObject(0).getString("port"))
        assertEquals("tun", amirRules.getJSONObject(0).getJSONArray("inboundTag").getString(0))

        var hasUdpBlock = false
        var hasLanCidr = false
        for (i in 0 until amirRules.length()) {
            val r = amirRules.getJSONObject(i)
            if (r.optString("network") == "udp" && r.optString("outboundTag") == "block") {
                hasUdpBlock = true
            }
            val ips = r.optJSONArray("ip")
            if (ips != null && ips.length() > 0 && ips.getString(0) == "10.0.0.0/8" && r.optString("outboundTag") == "direct") {
                hasLanCidr = true
            }
        }
        assertTrue("Expected udp block rule for AMIR", hasUdpBlock)
        assertTrue("Expected CIDR LAN bypass direct rule for AMIR", hasLanCidr)
    }

    @Test
    fun `AmirLocalProxy drains delayed server response after SOCKS5 client half-closes uplink`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val serverSock = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = serverSock.localPort

        val serverThread = Thread {
            serverSock.accept().use { s ->
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

                fun readWsFrame(): Pair<Int, ByteArray> {
                    val b0 = inp.read()
                    val b1 = inp.read()
                    val opcode = b0 and 0x0F
                    val masked = (b1 and 0x80) != 0
                    val len = b1 and 0x7F
                    val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                    val data = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                    if (mask != null) {
                        for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                    }
                    return opcode to data
                }

                val (_, init) = readWsFrame()
                val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                    .copyOfRange(0, 16)
                val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                out.write(0x82)
                out.write(encAck.size)
                out.write(encAck)
                out.flush()

                // Read HTTP request; relayUp must NOT emit premature 0x88 when client calls shutdownOutput()
                val (_, reqPayload) = readWsFrame()
                val reqText = String(com.example.core.AmirLocalProxy.xorBytes(reqPayload, key))

                // Wait briefly before sending delayed response while client awaits download
                Thread.sleep(150)
                val reply = com.example.core.AmirLocalProxy.xorBytes(("HALFCLOSE:" + reqText).toByteArray(), key)
                out.write(0x82)
                out.write(reply.size)
                out.write(reply)
                out.write(byteArrayOf(0x88.toByte(), 0x00))
                out.flush()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "HalfCloseAmir",
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
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 8, 8, 8, 8, 0x00, 0x50))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())

                out.write("GET /".toByteArray())
                out.flush()
                // Half-close uplink just like HTTP clients / OkHttp / curl do
                sock.shutdownOutput()

                val response = sock.getInputStream().readBytes()
                assertEquals("HALFCLOSE:GET /", String(response))
            }
        } finally {
            proxy.stop()
            serverSock.close()
        }
    }

    @Test
    fun `AmirLocalProxy tunnels SOCKS5 UDP DNS over TCP, redirects 1_1_1_1 to 8_8_8_8, and caches responses`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val serverSock = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = serverSock.localPort
        val dialedDnsHost = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)

        val serverThread = Thread {
            serverSock.accept().use { s ->
                upstreamCalls.incrementAndGet()
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

                fun readWsFrame(): ByteArray {
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

                val init = readWsFrame()
                val nonce = init.copyOfRange(0, 16)
                val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, nonce)
                val encBody = init.copyOfRange(16, init.size - 8)
                val plainBody = com.example.core.AmirLocalProxy.xorBytes(encBody, key)
                // body = [cmd=1][port(2)][atyp=1][4 ipv4 bytes]
                if ((plainBody[3].toInt() and 0xFF) == 1) {
                    val ipBytes = plainBody.copyOfRange(4, 8)
                    dialedDnsHost.set(java.net.InetAddress.getByAddress(ipBytes).hostAddress)
                }

                val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                    .copyOfRange(0, 16)
                val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                out.write(0x82)
                out.write(encAck.size)
                out.write(encAck)
                out.flush()

                val tcpDnsReq = com.example.core.AmirLocalProxy.xorBytes(readWsFrame(), key)
                val dnsLen = ((tcpDnsReq[0].toInt() and 0xFF) shl 8) or (tcpDnsReq[1].toInt() and 0xFF)
                val dnsReq = tcpDnsReq.copyOfRange(2, 2 + dnsLen)
                // Build valid DNS response with RCODE=0 and matching TxID
                val dnsResp = dnsReq.copyOf()
                dnsResp[2] = 0x81.toByte() // QR=1, RD=1
                dnsResp[3] = 0x80.toByte() // RA=1, RCODE=0
                val tcpDnsResp = ByteArray(2 + dnsResp.size)
                tcpDnsResp[0] = ((dnsResp.size ushr 8) and 0xFF).toByte()
                tcpDnsResp[1] = (dnsResp.size and 0xFF).toByte()
                System.arraycopy(dnsResp, 0, tcpDnsResp, 2, dnsResp.size)
                val encResp = com.example.core.AmirLocalProxy.xorBytes(tcpDnsResp, key)
                out.write(0x82)
                out.write(encResp.size)
                out.write(encResp)
                out.write(byteArrayOf(0x88.toByte(), 0x00))
                out.flush()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "UdpDnsAmir",
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
            java.net.Socket("127.0.0.1", localPort).use { ctrlSock ->
                ctrlSock.soTimeout = 5000
                val out = ctrlSock.getOutputStream()
                val inp = java.io.DataInputStream(ctrlSock.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))

                // SOCKS5 UDP ASSOCIATE (0x03)
                out.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())
                val relayPort = ((rep[8].toInt() and 0xFF) shl 8) or (rep[9].toInt() and 0xFF)

                // Craft standard DNS A query for example.com targeted at 1.1.1.1:53
                fun buildDnsQuery(txHi: Byte, txLo: Byte): ByteArray {
                    val label1 = "example".toByteArray(Charsets.US_ASCII)
                    val label2 = "com".toByteArray(Charsets.US_ASCII)
                    val dns = java.io.ByteArrayOutputStream()
                    dns.write(byteArrayOf(txHi, txLo, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    dns.write(label1.size); dns.write(label1)
                    dns.write(label2.size); dns.write(label2)
                    dns.write(0x00)
                    dns.write(byteArrayOf(0x00, 0x01, 0x00, 0x01)) // QTYPE=A, QCLASS=IN
                    return dns.toByteArray()
                }
                val socksHeader = byteArrayOf(0x00, 0x00, 0x00, 0x01, 1, 1, 1, 1, 0x00, 0x35)

                java.net.DatagramSocket().use { udp ->
                    udp.soTimeout = 4000
                    val q1 = socksHeader + buildDnsQuery(0x12, 0x34)
                    udp.send(java.net.DatagramPacket(q1, q1.size, java.net.InetAddress.getByName("127.0.0.1"), relayPort))

                    val buf1 = ByteArray(512)
                    val pkt1 = java.net.DatagramPacket(buf1, buf1.size)
                    udp.receive(pkt1)
                    val resp1 = pkt1.data.copyOfRange(0, pkt1.length)
                    // Original SOCKS header (1.1.1.1:53) must be preserved for TUN matching
                    assertEquals(1, resp1[4].toInt() and 0xFF)
                    assertEquals(1, resp1[5].toInt() and 0xFF)
                    assertEquals(0x12, resp1[10].toInt() and 0xFF)
                    assertEquals(0x34, resp1[11].toInt() and 0xFF)
                    assertEquals(0x81, resp1[12].toInt() and 0xFF)

                    // Worker target must have been transparently redirected to 8.8.8.8
                    assertEquals("8.8.8.8", dialedDnsHost.get())

                    // 2nd identical query with new TxID must hit DNS cache (upstreamCalls remains 1)
                    val q2 = socksHeader + buildDnsQuery(0x56, 0x78)
                    udp.send(java.net.DatagramPacket(q2, q2.size, java.net.InetAddress.getByName("127.0.0.1"), relayPort))
                    val pkt2 = java.net.DatagramPacket(buf1, buf1.size)
                    udp.receive(pkt2)
                    val resp2 = pkt2.data.copyOfRange(0, pkt2.length)
                    assertEquals(0x56, resp2[10].toInt() and 0xFF)
                    assertEquals(0x78, resp2[11].toInt() and 0xFF)
                    assertEquals(1, upstreamCalls.get())
                }
            }
        } finally {
            proxy.stop()
            serverSock.close()
        }
    }

    @Test
    fun `AmirLocalProxy binds preferred port when free and falls back when occupied`() {
        assertEquals(40446, com.example.core.AmirLocalProxy.DEFAULT_SOCKS_PORT)
        val probe = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val candidatePort = probe.localPort
        probe.close()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "PreferredPortAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = 443,
            passwordOrId = com.example.core.AmirLocalProxy.DEFAULT_UUID,
            extraParam1 = "ws://127.0.0.1:65530/",
            extraParam2 = ""
        )
        val proxy1 = com.example.core.AmirLocalProxy(cfg)
        val boundPreferred = proxy1.start(preferredPort = candidatePort)
        try {
            assertEquals(candidatePort, boundPreferred)
        } finally {
            proxy1.stop()
        }

        // Now occupy candidatePort and ensure fallback binds another free port cleanly
        java.net.ServerSocket(candidatePort, 1, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            val proxy2 = com.example.core.AmirLocalProxy(cfg)
            val fallbackPort = proxy2.start(preferredPort = candidatePort)
            try {
                assertTrue(fallbackPort > 0)
                assertTrue(fallbackPort != occupied.localPort)
            } finally {
                proxy2.stop()
            }
        }
    }

    @Test
    fun `AmirLocalProxy retries with fresh WebSocket when first socket closes silently before ACK`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val wsServer = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = wsServer.localPort
        val connectionAttempts = java.util.concurrent.atomic.AtomicInteger(0)

        val wsThread = Thread {
            while (!wsServer.isClosed) {
                val s = try {
                    wsServer.accept()
                } catch (_: Exception) {
                    break
                }
                val attempt = connectionAttempts.incrementAndGet()
                Thread {
                    s.use { sock ->
                        val inp = java.io.BufferedInputStream(sock.getInputStream())
                        val out = sock.getOutputStream()
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

                        fun readWsFrame(): ByteArray {
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

                        val init = readWsFrame()
                        if (attempt == 1) {
                            // Simulate a stale warm-pooled WS silently sending Close/EOF instead of ACK
                            out.write(byteArrayOf(0x88.toByte(), 0x00))
                            out.flush()
                            return@use
                        }

                        val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                        val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                            .copyOfRange(0, 16)
                        val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                        out.write(0x82)
                        out.write(encAck.size)
                        out.write(encAck)
                        out.flush()

                        val reqPayload = com.example.core.AmirLocalProxy.xorBytes(readWsFrame(), key)
                        val resp = com.example.core.AmirLocalProxy.xorBytes(("RETRYOK:" + String(reqPayload)).toByteArray(), key)
                        out.write(0x82)
                        out.write(resp.size)
                        out.write(resp)
                        out.write(byteArrayOf(0x88.toByte(), 0x00))
                        out.flush()
                    }
                }.start()
            }
        }
        wsThread.isDaemon = true
        wsThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "StaleRetryAmir",
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
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                val host = "example.com".toByteArray(Charsets.UTF_8)
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()) + host + byteArrayOf(0x00, 0x50))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())
                out.write("HELLO".toByteArray())
                out.flush()
                val buf = ByteArray(32)
                val n = inp.read(buf)
                assertTrue(n > 0)
                assertEquals("RETRYOK:HELLO", String(buf, 0, n))
                assertEquals(2, connectionAttempts.get())
            }
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }

    @Test
    fun `AmirLocalProxy bypasses Worker WS on Cloudflare DoH port 443 and does not leak WS when client closes`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val wsServer = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = wsServer.localPort
        val wsHitCount = java.util.concurrent.atomic.AtomicInteger(0)
        val wsClosedLatch = java.util.concurrent.CountDownLatch(1)

        val wsThread = Thread {
            while (!wsServer.isClosed) {
                val s = try {
                    wsServer.accept()
                } catch (_: Exception) {
                    break
                }
                wsHitCount.incrementAndGet()
                Thread {
                    s.use { sock ->
                        val inp = java.io.BufferedInputStream(sock.getInputStream())
                        val out = sock.getOutputStream()
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

                        val b0 = inp.read()
                        val b1 = inp.read()
                        if (b0 < 0 || b1 < 0) return@use
                        val masked = (b1 and 0x80) != 0
                        val len = b1 and 0x7F
                        val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                        val init = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                        if (mask != null) {
                            for (i in init.indices) init[i] = (init[i].toInt() xor mask[i and 3].toInt()).toByte()
                        }
                        val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                        val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                            .copyOfRange(0, 16)
                        val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                        out.write(0x82)
                        out.write(encAck.size)
                        out.write(encAck)
                        out.flush()

                        // Wait silently until AmirLocalProxy closes the WS after client closes/resets
                        try {
                            while (inp.read() >= 0) {
                                // Drain payload until EOF/close
                            }
                        } catch (_: Exception) {
                        } finally {
                            wsClosedLatch.countDown()
                        }
                    }
                }.start()
            }
        }
        wsThread.isDaemon = true
        wsThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "FastPathAmir",
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
            // 1. Abruptly closing a SOCKS5 client TCP connection (SO_LINGER=0 reset) must close upstream WS immediately
            java.net.Socket("127.0.0.1", localPort).use { sock ->
                sock.soTimeout = 3000
                sock.setSoLinger(true, 0)
                val out = sock.getOutputStream()
                val inp = java.io.DataInputStream(sock.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 8, 8, 8, 8, 0x01, 0xBB.toByte()))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())
                out.write("PARTIAL".toByteArray())
                out.flush()
            }
            assertTrue(
                "Aborted client socket must close upstream Worker WS instead of leaking it",
                wsClosedLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            )
            assertEquals(1, wsHitCount.get())

            // 2. IPv6 Cloudflare DoH 2606:4700::6810:f8f9:443 with preferIpv6=false must NOT dial Worker WS at all
            java.net.Socket("127.0.0.1", localPort).use { dohSock ->
                dohSock.soTimeout = 3000
                val out = dohSock.getOutputStream()
                val inp = java.io.DataInputStream(dohSock.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                val v6 = java.net.InetAddress.getByName("2606:4700::6810:f8f9").address
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x04) + v6 + byteArrayOf(0x01, 0xBB.toByte()))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(5, rep[1].toInt())
            }
            assertEquals(
                "Cloudflare DoH target must not consume Worker WebSocket dials",
                1,
                wsHitCount.get()
            )
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }

    @Test
    fun `MainViewModel formats country flag emoji for GeoIP connection info responses`() {
        assertEquals("🇩🇪", com.example.ui.MainViewModel.countryCodeToFlagEmoji("de"))
        assertEquals("🇺🇸", com.example.ui.MainViewModel.countryCodeToFlagEmoji("US"))
        assertEquals("🇮🇷", com.example.ui.MainViewModel.countryCodeToFlagEmoji("IR"))
        assertEquals("", com.example.ui.MainViewModel.countryCodeToFlagEmoji("INVALID"))

        val ipSbParsed = com.example.ui.MainViewModel.parseConnectionInfo(
            """{"ip":"1.2.3.4","country":"Germany","country_code":"DE","city":"Frankfurt","isp":"Cloudflare"}"""
        )
        assertEquals("🇩🇪", ipSbParsed.countryFlag)
        assertEquals("Germany", ipSbParsed.countryLabel)
        assertTrue(ipSbParsed.summaryLines.contains("Country" to "🇩🇪 Germany"))
        assertTrue(ipSbParsed.summaryLines.contains("Code" to "DE"))

        val ipApiParsed = com.example.ui.MainViewModel.parseConnectionInfo(
            """{"query":"5.6.7.8","country":"Netherlands","countryCode":"NL"}"""
        )
        assertEquals("🇳🇱", ipApiParsed.countryFlag)
        assertEquals("Netherlands", ipApiParsed.countryLabel)
        assertTrue(ipApiParsed.summaryLines.contains("IP" to "5.6.7.8"))
        assertTrue(ipApiParsed.summaryLines.contains("Country" to "🇳🇱 Netherlands"))

        val ipInfoParsed = com.example.ui.MainViewModel.parseConnectionInfo(
            """{"ip":"9.9.9.9","country":"FR","city":"Paris"}"""
        )
        assertEquals("🇫🇷", ipInfoParsed.countryFlag)
        assertTrue(ipInfoParsed.summaryLines.contains("Country" to "🇫🇷 FR"))
    }

    @Test
    fun `Local DNS and FakeDNS toggles produce distinct configs per V2Ray DNS precedence rules`() {
        val vless = ConfigParser.parseSingleUri(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example.com:443?encryption=none&security=tls&type=tcp#VlessNode"
        )!!
        val amir = ConfigParser.parseSingleUri(
            "amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@twilight-dream.workers.dev:443?cleanIp=172.67.70.244#AmirNode"
        )!!

        fun hasOutbound(root: JSONObject, tag: String): Boolean {
            val arr = root.getJSONArray("outbounds")
            return (0 until arr.length()).any { arr.getJSONObject(it).optString("tag") == tag }
        }

        fun hasPort53DnsRule(root: JSONObject): Boolean {
            val rules = root.getJSONObject("routing").getJSONArray("rules")
            return (0 until rules.length()).any {
                val r = rules.getJSONObject(it)
                r.optString("port") == "53" && r.optString("outboundTag") == "dns-out"
            }
        }

        // 1. VLESS: (localDns=false, fakeDns=false) -> Direct VPN DNS mode
        val vlessDirect = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                vless,
                AppSettings(localDnsEnabled = false, fakeDnsEnabled = false, routingMode = RoutingMode.BYPASS_LAN_AND_IR),
                emptyList()
            )
        )
        assertFalse(vlessDirect.has("fakedns"))
        assertFalse(hasOutbound(vlessDirect, "dns-out"))
        assertFalse(hasPort53DnsRule(vlessDirect))
        assertEquals("AsIs", vlessDirect.getJSONObject("routing").getString("domainStrategy"))

        // 2. VLESS: (localDns=true, fakeDns=false) -> Real Local DNS only
        val vlessLocalOnly = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                vless,
                AppSettings(localDnsEnabled = true, fakeDnsEnabled = false, remoteDns = "https://1.1.1.1/dns-query"),
                emptyList()
            )
        )
        assertFalse(vlessLocalOnly.has("fakedns"))
        assertTrue(hasOutbound(vlessLocalOnly, "dns-out"))
        assertTrue(hasPort53DnsRule(vlessLocalOnly))
        val localOnlyServers = vlessLocalOnly.getJSONObject("dns").getJSONArray("servers")
        assertEquals("https://1.1.1.1/dns-query", localOnlyServers.getString(0))
        assertFalse((0 until localOnlyServers.length()).any { localOnlyServers.optString(it) == "fakedns" })

        // 3. VLESS: (localDns=false, fakeDns=true) -> Pure FakeDNS without remote DoH queries
        val vlessPureFake = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                vless,
                AppSettings(localDnsEnabled = false, fakeDnsEnabled = true, routeOnly = true),
                emptyList()
            )
        )
        assertTrue(vlessPureFake.has("fakedns"))
        assertTrue(hasOutbound(vlessPureFake, "dns-out"))
        assertTrue(hasPort53DnsRule(vlessPureFake))
        val pureFakeServers = vlessPureFake.getJSONObject("dns").getJSONArray("servers")
        assertEquals(2, pureFakeServers.length())
        assertEquals("fakedns", pureFakeServers.getString(0))
        assertEquals("localhost", pureFakeServers.getString(1))
        val pureFakeSniff = vlessPureFake.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing")
        assertFalse(pureFakeSniff.getBoolean("routeOnly"))
        assertEquals("fakedns", pureFakeSniff.getJSONArray("destOverride").getString(0))

        // 4. VLESS: (localDns=true, fakeDns=true) -> Hybrid FakeDNS: no geosite:google override steals from fakedns
        val vlessHybrid = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                vless,
                AppSettings(localDnsEnabled = true, fakeDnsEnabled = true, routingMode = RoutingMode.BYPASS_LAN_AND_IR),
                emptyList()
            )
        )
        val hybridServers = vlessHybrid.getJSONObject("dns").getJSONArray("servers")
        assertEquals("fakedns", hybridServers.getString(0))
        for (i in 0 until hybridServers.length()) {
            val obj = hybridServers.optJSONObject(i) ?: continue
            val domains = obj.optJSONArray("domains") ?: continue
            for (j in 0 until domains.length()) {
                assertFalse(
                    "geosite:google must not appear in a domain-scoped DoH server that overrides fakedns",
                    domains.getString(j) == "geosite:google"
                )
            }
        }

        // 5. AMIR: (localDns=false, fakeDns=false) -> Direct VPN DNS mode (blocks non-DNS UDP, forwards port 53)
        val amirDirect = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                amir,
                AppSettings(vpnMode = true, localDnsEnabled = false, fakeDnsEnabled = false),
                emptyList(),
                amirLocalPort = 40446
            )
        )
        assertFalse(amirDirect.has("fakedns"))
        assertFalse(hasOutbound(amirDirect, "dns-out"))
        assertFalse(hasPort53DnsRule(amirDirect))
        val amirDirectDest = amirDirect.getJSONArray("inbounds").getJSONObject(0)
            .getJSONObject("sniffing").getJSONArray("destOverride")
        assertFalse((0 until amirDirectDest.length()).any { amirDirectDest.getString(it) == "fakedns" })

        // 6. AMIR: (localDns=true, fakeDns=false) -> Local DNS routed via AmirLocalProxy UDP listener
        val amirLocal = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                amir,
                AppSettings(vpnMode = true, localDnsEnabled = true, fakeDnsEnabled = false),
                emptyList(),
                amirLocalPort = 40446,
                amirDnsPort = 55353
            )
        )
        assertFalse(amirLocal.has("fakedns"))
        assertTrue(hasOutbound(amirLocal, "dns-out"))
        assertTrue(hasPort53DnsRule(amirLocal))
        val amirLocalDnsObj = amirLocal.getJSONObject("dns").getJSONArray("servers").getJSONObject(0)
        assertEquals("127.0.0.1", amirLocalDnsObj.getString("address"))
        assertEquals(55353, amirLocalDnsObj.getInt("port"))
        val amirLocalDest = amirLocal.getJSONArray("inbounds").getJSONObject(0)
            .getJSONObject("sniffing").getJSONArray("destOverride")
        assertFalse((0 until amirLocalDest.length()).any { amirLocalDest.getString(it) == "fakedns" })

        // 7. AMIR: (localDns=false, fakeDns=true) -> Pure FakeDNS for all domains
        val amirPureFake = JSONObject(
            XrayConfigBuilder.buildFullConfig(
                amir,
                AppSettings(vpnMode = true, localDnsEnabled = false, fakeDnsEnabled = true),
                emptyList(),
                amirLocalPort = 40446
            )
        )
        assertTrue(amirPureFake.has("fakedns"))
        val amirPureFakeServers = amirPureFake.getJSONObject("dns").getJSONArray("servers")
        assertEquals(2, amirPureFakeServers.length())
        assertEquals("fakedns", amirPureFakeServers.getString(0))
        assertEquals("localhost", amirPureFakeServers.getString(1))
    }

    @Test
    fun `AmirLocalProxy direct APN HTTP CONNECT and plain HTTP forwarding work standalone on dual ports and track traffic`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val wsServer = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = wsServer.localPort
        val observedTargets = java.util.concurrent.CopyOnWriteArrayList<String>()

        val wsThread = Thread {
            while (!wsServer.isClosed) {
                val s = try {
                    wsServer.accept()
                } catch (_: Exception) {
                    break
                }
                Thread {
                    s.use { sock ->
                        val inp = java.io.BufferedInputStream(sock.getInputStream())
                        val out = sock.getOutputStream()
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

                        fun readWsFrame(): Pair<Int, ByteArray> {
                            val b0 = inp.read()
                            if (b0 < 0) return -1 to ByteArray(0)
                            val b1 = inp.read()
                            if (b1 < 0) return -1 to ByteArray(0)
                            val opcode = b0 and 0x0F
                            val masked = (b1 and 0x80) != 0
                            val lenByte = b1 and 0x7F
                            val len = when (lenByte) {
                                126 -> {
                                    val hi = inp.read()
                                    val lo = inp.read()
                                    (hi shl 8) or lo
                                }
                                else -> lenByte
                            }
                            val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                            val data = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                            if (mask != null) {
                                for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                            }
                            return opcode to data
                        }

                        val (_, init) = readWsFrame()
                        val nonce = init.copyOfRange(0, 16)
                        val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, nonce)
                        val body = com.example.core.AmirLocalProxy.xorBytes(init.copyOfRange(16, init.size - 8), key)
                        val port = ((body[1].toInt() and 0xFF) shl 8) or (body[2].toInt() and 0xFF)
                        val atyp = body[3].toInt() and 0xFF
                        val host = if (atyp == 2) {
                            val dLen = body[4].toInt() and 0xFF
                            String(body, 5, dLen, Charsets.UTF_8)
                        } else {
                            "ip"
                        }
                        observedTargets.add("$host:$port")

                        val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                            .copyOfRange(0, 16)
                        val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                        out.write(0x82)
                        out.write(encAck.size)
                        out.write(encAck)
                        out.flush()

                        val (_, reqFrame) = readWsFrame()
                        val reqText = String(com.example.core.AmirLocalProxy.xorBytes(reqFrame, key), Charsets.US_ASCII)
                        val responseBody = if (port == 443) {
                            "TLS_TUNNEL_ECHO:$reqText"
                        } else {
                            "HTTP/1.1 200 OK\r\nContent-Length: ${reqText.length}\r\nConnection: close\r\n\r\n$reqText"
                        }
                        val respBytes = responseBody.toByteArray(Charsets.US_ASCII)
                        val encResp = com.example.core.AmirLocalProxy.xorBytes(respBytes, key)
                        out.write(0x82)
                        if (encResp.size <= 125) {
                            out.write(encResp.size)
                        } else {
                            out.write(126)
                            out.write((encResp.size ushr 8) and 0xFF)
                            out.write(encResp.size and 0xFF)
                        }
                        out.write(encResp)
                        out.write(byteArrayOf(0x88.toByte(), 0x00))
                        out.flush()
                    }
                }.start()
            }
        }
        wsThread.isDaemon = true
        wsThread.start()

        // Allocate two distinct ephemeral preferred ports (HTTP + SOCKS5)
        val probeHttp = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val prefHttp = probeHttp.localPort
        probeHttp.close()
        val probeSocks = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val prefSocks = probeSocks.localPort
        probeSocks.close()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "DirectApnAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = wsPort,
            passwordOrId = uuid,
            extraParam1 = "ws://127.0.0.1:$wsPort/",
            extraParam2 = ""
        )
        val proxy = com.example.core.AmirLocalProxy(cfg)
        val (boundHttpPort, boundSocksPort) = proxy.startDual(
            httpPreferredPort = prefHttp,
            socksPreferredPort = prefSocks,
            listenHost = "127.0.0.1"
        )
        try {
            assertEquals(prefHttp, boundHttpPort)
            assertEquals(prefSocks, boundSocksPort)
            assertTrue(proxy.isRunning())

            // 1. HTTPS CONNECT on HTTP port (with pipelined payload right after headers!)
            java.net.Socket("127.0.0.1", boundHttpPort).use { sock ->
                sock.soTimeout = 5000
                val out = sock.getOutputStream()
                out.write(
                    (
                        "CONNECT youtube.com:443 HTTP/1.1\r\n" +
                            "Host: youtube.com:443\r\n" +
                            "Proxy-Connection: keep-alive\r\n\r\n" +
                            "CLIENT_HELLO_PING"
                        ).toByteArray(Charsets.US_ASCII)
                )
                out.flush()
                val rawResp = String(sock.getInputStream().readBytes(), Charsets.US_ASCII)
                assertTrue(rawResp.startsWith("HTTP/1.1 200 Connection Established"))
                assertTrue(rawResp.contains("TLS_TUNNEL_ECHO:CLIENT_HELLO_PING"))
            }

            // 2. Plain HTTP proxy GET with Proxy-Connection stripping on HTTP port
            java.net.Socket("127.0.0.1", boundHttpPort).use { sock ->
                sock.soTimeout = 5000
                val out = sock.getOutputStream()
                out.write(
                    (
                        "GET http://api.ip.sb/geoip?lang=en HTTP/1.1\r\n" +
                            "Host: api.ip.sb\r\n" +
                            "Proxy-Connection: keep-alive\r\n" +
                            "User-Agent: TestClient\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                )
                out.flush()
                val rawResp = String(sock.getInputStream().readBytes(), Charsets.US_ASCII)
                assertTrue(rawResp.startsWith("HTTP/1.1 200 OK"))
                assertTrue(rawResp.contains("GET /geoip?lang=en HTTP/1.1"))
                assertTrue(rawResp.contains("Connection: close"))
                assertFalse(rawResp.contains("Proxy-Connection"))
            }

            // 3. Auto-detection: HTTP CONNECT also works if client sends HTTP to the SOCKS5 port!
            java.net.Socket("127.0.0.1", boundSocksPort).use { sock ->
                sock.soTimeout = 5000
                val out = sock.getOutputStream()
                out.write("CONNECT google.com:443 HTTP/1.1\r\nHost: google.com:443\r\n\r\nHI".toByteArray())
                out.flush()
                val rawResp = String(sock.getInputStream().readBytes(), Charsets.US_ASCII)
                assertTrue(rawResp.startsWith("HTTP/1.1 200 Connection Established"))
                assertTrue(rawResp.contains("TLS_TUNNEL_ECHO:HI"))
            }

            assertTrue(observedTargets.contains("youtube.com:443"))
            assertTrue(observedTargets.contains("api.ip.sb:80"))
            assertTrue(observedTargets.contains("google.com:443"))

            val (upBytes, downBytes) = proxy.consumeTrafficDelta()
            assertTrue("Expected positive uplink bytes, got $upBytes", upBytes > 0L)
            assertTrue("Expected positive downlink bytes, got $downBytes", downBytes > 0L)
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }

    @Test
    fun `AmirLocalProxy Happy Eyeballs parallel dial fails over from refused cleanIp to live loopback workerHost`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)

        val wsServer = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val livePort = wsServer.localPort

        val serverThread = Thread {
            while (!wsServer.isClosed) {
                val s = try {
                    wsServer.accept()
                } catch (_: Exception) {
                    break
                }
                Thread {
                    s.use { sock ->
                        val inp = java.io.BufferedInputStream(sock.getInputStream())
                        val out = sock.getOutputStream()
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

                        fun readWsFrame(): ByteArray {
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

                        val init = readWsFrame()
                        val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                        val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                            .copyOfRange(0, 16)
                        val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                        out.write(0x82)
                        out.write(encAck.size)
                        out.write(encAck)
                        out.flush()

                        val req = com.example.core.AmirLocalProxy.xorBytes(readWsFrame(), key)
                        val resp = com.example.core.AmirLocalProxy.xorBytes(("EYEBALLS:" + String(req)).toByteArray(), key)
                        out.write(0x82)
                        out.write(resp.size)
                        out.write(resp)
                        out.write(byteArrayOf(0x88.toByte(), 0x00))
                        out.flush()
                    }
                }.start()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        // cleanIp points to 127.0.0.2 (different loopback IP with no listener on livePort -> refused)
        // workerHost points to 127.0.0.1 (live listener on livePort)
        val cfg = com.example.data.model.ServerConfig(
            remarks = "HappyEyeballsAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = livePort,
            passwordOrId = uuid,
            extraParam1 = "ws://127.0.0.1:$livePort/",
            extraParam2 = "127.0.0.2"
        )
        val proxy = com.example.core.AmirLocalProxy(cfg)
        val localPort = proxy.start()
        try {
            val startMs = System.currentTimeMillis()
            java.net.Socket("127.0.0.1", localPort).use { sock ->
                sock.soTimeout = 5000
                val out = sock.getOutputStream()
                val inp = java.io.DataInputStream(sock.getInputStream())
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 8, 8, 8, 8, 0x00, 0x50))
                out.flush()
                val rep = ByteArray(10)
                inp.readFully(rep)
                assertEquals(0, rep[1].toInt())
                out.write("OK".toByteArray())
                out.flush()
                val resp = String(sock.getInputStream().readBytes())
                assertEquals("EYEBALLS:OK", resp)
            }
            val elapsedMs = System.currentTimeMillis() - startMs
            assertTrue("Expected fast Happy Eyeballs failover (<3000ms), took ${elapsedMs}ms", elapsedMs < 3000L)
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }

    @Test
    fun `AmirLocalProxy local UDP DNS listener coalesces concurrent identical queries via singleflight`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val wsServer = java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = wsServer.localPort
        val upstreamDnsCalls = java.util.concurrent.atomic.AtomicInteger(0)

        val serverThread = Thread {
            while (!wsServer.isClosed) {
                val s = try {
                    wsServer.accept()
                } catch (_: Exception) {
                    break
                }
                upstreamDnsCalls.incrementAndGet()
                Thread {
                    s.use { sock ->
                        val inp = java.io.BufferedInputStream(sock.getInputStream())
                        val out = sock.getOutputStream()
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

                        fun readWsFrame(): ByteArray {
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

                        val init = readWsFrame()
                        val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                        val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                            .copyOfRange(0, 16)
                        val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                        out.write(0x82)
                        out.write(encAck.size)
                        out.write(encAck)
                        out.flush()

                        val tcpDnsReq = com.example.core.AmirLocalProxy.xorBytes(readWsFrame(), key)
                        val dnsLen = ((tcpDnsReq[0].toInt() and 0xFF) shl 8) or (tcpDnsReq[1].toInt() and 0xFF)
                        val dnsReq = tcpDnsReq.copyOfRange(2, 2 + dnsLen)
                        // Small artificial delay so concurrent callers coalesce onto singleflight in-flight future
                        Thread.sleep(120)
                        val dnsResp = dnsReq.copyOf()
                        dnsResp[2] = 0x81.toByte()
                        dnsResp[3] = 0x80.toByte()
                        val tcpDnsResp = ByteArray(2 + dnsResp.size)
                        tcpDnsResp[0] = ((dnsResp.size ushr 8) and 0xFF).toByte()
                        tcpDnsResp[1] = (dnsResp.size and 0xFF).toByte()
                        System.arraycopy(dnsResp, 0, tcpDnsResp, 2, dnsResp.size)
                        val encResp = com.example.core.AmirLocalProxy.xorBytes(tcpDnsResp, key)
                        out.write(0x82)
                        out.write(encResp.size)
                        out.write(encResp)
                        out.write(byteArrayOf(0x88.toByte(), 0x00))
                        out.flush()
                    }
                }.start()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "LocalDnsSingleflightAmir",
            protocol = ProtocolType.AMIR,
            address = "127.0.0.1",
            port = wsPort,
            passwordOrId = uuid,
            extraParam1 = "ws://127.0.0.1:$wsPort/",
            extraParam2 = ""
        )
        val proxy = com.example.core.AmirLocalProxy(cfg)
        proxy.startDual(httpPreferredPort = 0, socksPreferredPort = 0, listenHost = "127.0.0.1", dnsPreferredPort = 0)
        val dnsPort = proxy.localDnsPort
        assertTrue("Expected bound localDnsPort > 0", dnsPort > 0)

        try {
            fun buildDnsQuery(txId: Int): ByteArray {
                val l1 = "singleflight".toByteArray(Charsets.US_ASCII)
                val l2 = "test".toByteArray(Charsets.US_ASCII)
                val bos = java.io.ByteArrayOutputStream()
                bos.write(
                    byteArrayOf(
                        ((txId ushr 8) and 0xFF).toByte(),
                        (txId and 0xFF).toByte(),
                        0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                    )
                )
                bos.write(l1.size); bos.write(l1)
                bos.write(l2.size); bos.write(l2)
                bos.write(0x00)
                bos.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
                return bos.toByteArray()
            }

            val callerCount = 8
            val startGate = java.util.concurrent.CountDownLatch(1)
            val doneLatch = java.util.concurrent.CountDownLatch(callerCount)
            val failures = java.util.concurrent.CopyOnWriteArrayList<String>()

            repeat(callerCount) { idx ->
                Thread {
                    try {
                        val txId = 0x2000 + idx
                        val query = buildDnsQuery(txId)
                        java.net.DatagramSocket().use { udp ->
                            udp.soTimeout = 4000
                            startGate.await()
                            udp.send(
                                java.net.DatagramPacket(
                                    query,
                                    query.size,
                                    java.net.InetAddress.getByName("127.0.0.1"),
                                    dnsPort
                                )
                            )
                            val buf = ByteArray(512)
                            val pkt = java.net.DatagramPacket(buf, buf.size)
                            udp.receive(pkt)
                            val resp = pkt.data.copyOfRange(0, pkt.length)
                            val respTx = ((resp[0].toInt() and 0xFF) shl 8) or (resp[1].toInt() and 0xFF)
                            if (respTx != txId) {
                                failures.add("TxID mismatch: expected=$txId got=$respTx")
                            }
                            if ((resp[2].toInt() and 0x80) == 0) {
                                failures.add("Expected DNS QR=1 response bit")
                            }
                        }
                    } catch (e: Exception) {
                        failures.add(e.message ?: e.javaClass.simpleName)
                    } finally {
                        doneLatch.countDown()
                    }
                }.start()
            }

            startGate.countDown()
            assertTrue("Concurrent DNS queries timed out", doneLatch.await(6, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("Unexpected DNS query failures: $failures", failures.isEmpty())
            assertEquals("Expected singleflight to coalesce 8 concurrent identical queries to 1 upstream WS dial", 1, upstreamDnsCalls.get())
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }

    @Test
    fun `AmirLocalProxy handles concurrent TCP streams with abrupt resets and half-closes without deadlock`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
        val wsServer = java.net.ServerSocket(0, 32, java.net.InetAddress.getByName("127.0.0.1"))
        val wsPort = wsServer.localPort

        val serverThread = Thread {
            while (!wsServer.isClosed) {
                val s = try {
                    wsServer.accept()
                } catch (_: Exception) {
                    break
                }
                Thread {
                    s.use { sock ->
                        val inp = java.io.BufferedInputStream(sock.getInputStream())
                        val out = sock.getOutputStream()
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

                        fun readWsFrame(): Pair<Int, ByteArray> {
                            val b0 = inp.read()
                            if (b0 < 0) return -1 to ByteArray(0)
                            val b1 = inp.read()
                            if (b1 < 0) return -1 to ByteArray(0)
                            val opcode = b0 and 0x0F
                            val masked = (b1 and 0x80) != 0
                            val len = b1 and 0x7F
                            val mask = if (masked) ByteArray(4).also { java.io.DataInputStream(inp).readFully(it) } else null
                            val data = ByteArray(len).also { java.io.DataInputStream(inp).readFully(it) }
                            if (mask != null) {
                                for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                            }
                            return opcode to data
                        }

                        val (_, init) = readWsFrame()
                        val key = com.example.core.AmirLocalProxy.deriveKey(uuidBytes, init.copyOfRange(0, 16))
                        val expectedAck = com.example.core.AmirLocalProxy.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII))
                            .copyOfRange(0, 16)
                        val encAck = com.example.core.AmirLocalProxy.xorBytes(expectedAck, key)
                        out.write(0x82)
                        out.write(encAck.size)
                        out.write(encAck)
                        out.flush()

                        val (_, reqFrame) = readWsFrame()
                        if (reqFrame.isEmpty()) return@use
                        val reqText = String(com.example.core.AmirLocalProxy.xorBytes(reqFrame, key), Charsets.US_ASCII)
                        val respBytes = com.example.core.AmirLocalProxy.xorBytes(("STREAM_OK:$reqText").toByteArray(Charsets.US_ASCII), key)
                        out.write(0x82)
                        out.write(respBytes.size)
                        out.write(respBytes)
                        out.write(byteArrayOf(0x88.toByte(), 0x00))
                        out.flush()
                    }
                }.start()
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val cfg = com.example.data.model.ServerConfig(
            remarks = "StressAmir",
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
            val totalClients = 16
            val done = java.util.concurrent.CountDownLatch(totalClients)
            val errors = java.util.concurrent.CopyOnWriteArrayList<String>()

            repeat(totalClients) { idx ->
                Thread {
                    try {
                        java.net.Socket("127.0.0.1", localPort).use { sock ->
                            sock.soTimeout = 5000
                            val out = sock.getOutputStream()
                            val inp = java.io.DataInputStream(sock.getInputStream())
                            out.write(byteArrayOf(0x05, 0x01, 0x00))
                            out.flush()
                            inp.readFully(ByteArray(2))
                            out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 8, 8, 8, 8, 0x00, 0x50))
                            out.flush()
                            val rep = ByteArray(10)
                            inp.readFully(rep)
                            if (rep[1].toInt() != 0) {
                                errors.add("Client $idx SOCKS connect failed: ${rep[1].toInt()}")
                                return@use
                            }
                            if (idx % 4 == 0) {
                                // Abrupt RST on a quarter of concurrent sockets must never stall siblings
                                sock.setSoLinger(true, 0)
                                out.write("ABORT-$idx".toByteArray())
                                out.flush()
                            } else {
                                val msg = "REQ-$idx"
                                out.write(msg.toByteArray())
                                out.flush()
                                sock.shutdownOutput()
                                val body = String(sock.getInputStream().readBytes())
                                if (body != "STREAM_OK:$msg") {
                                    errors.add("Client $idx mismatch: got '$body'")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Client $idx error: ${e.message}")
                    } finally {
                        done.countDown()
                    }
                }.start()
            }

            assertTrue("Concurrent TCP stress test deadlocked/timed out", done.await(8, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("Unexpected stress errors: $errors", errors.isEmpty())
        } finally {
            proxy.stop()
            wsServer.close()
        }
    }
}

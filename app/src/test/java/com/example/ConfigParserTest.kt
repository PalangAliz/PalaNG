package com.example

import com.example.core.ConfigParser
import com.example.core.XrayConfigBuilder
import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.RoutingMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertEquals("UseIPv4", dns.getString("queryStrategy"))
        val dnsServers = dns.getJSONArray("servers")
        assertEquals("fakedns", dnsServers.getString(0))
        assertEquals("https://1.1.1.1/dns-query", dnsServers.getString(1))

        val tunSniff = root.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing")
        assertTrue(tunSniff.getBoolean("enabled"))
        assertEquals(false, tunSniff.getBoolean("routeOnly"))
        assertEquals("fakedns", tunSniff.getJSONArray("destOverride").getString(0))

        val routing = root.getJSONObject("routing")
        assertEquals("AsIs", routing.getString("domainStrategy"))
        val rules = routing.getJSONArray("rules")
        var hasUdpBlock = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (r.optString("network") == "udp" && r.optString("outboundTag") == "block") {
                hasUdpBlock = true
            }
        }
        assertTrue(hasUdpBlock)

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
}

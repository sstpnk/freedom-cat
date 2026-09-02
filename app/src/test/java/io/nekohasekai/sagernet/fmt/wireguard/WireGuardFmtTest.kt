package io.nekohasekai.sagernet.fmt.wireguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.Deflater

class WireGuardFmtTest {

    @Test
    fun plainWireGuardConfigStaysPlainAndBuildsWireGuardOutbound() {
        val bean = parseWireGuardConfig(
            """
                [Interface]
                PrivateKey = client-private
                Address = 10.0.0.2/32, fd00::2/128
                MTU = 1280

                [Peer]
                PublicKey = peer-public
                PresharedKey = peer-psk
                AllowedIPs = 10.0.0.0/24
                Endpoint = wg.example.com:51820
            """.trimIndent()
        ).single()

        assertEquals("10.0.0.2/32\nfd00::2/128", bean.localAddress)
        assertEquals("client-private", bean.privateKey)
        assertEquals("wg.example.com", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertEquals("peer-public", bean.peerPublicKey)
        assertEquals("peer-psk", bean.peerPreSharedKey)
        assertEquals(1280, bean.mtu)
        assertFalse(bean.enableAmnezia == true)

        val outbound = buildSingBoxOutboundWireguardBean(bean)
        assertEquals("wireguard", outbound.type)
        assertEquals("wg.example.com", outbound.server)
        assertEquals(51820, outbound.server_port)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), outbound.local_address)
        assertEquals("client-private", outbound.private_key)
        assertEquals("peer-public", outbound.peer_public_key)
        assertEquals("peer-psk", outbound.pre_shared_key)
        assertEquals(1280, outbound.mtu)
    }

    @Test
    fun amneziawgSchemeEnablesAwgAndParsesAliases() {
        val bean = parseWireGuardLinks(
            "amneziawg://awg.example.com:51820" +
                    "?private_key=client" +
                    "&peer_public_key=peer" +
                    "&pre_shared_key=psk" +
                    "&local_address=10.8.0.2/32" +
                    "&jmin=16" +
                    "&s2=32" +
                    "&h3=123456"
        ).single()

        assertEquals("awg.example.com", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertTrue(bean.enableAmnezia == true)
        assertEquals(16, bean.jmin)
        assertEquals(32, bean.s2)
        assertEquals("123456", bean.h3)
    }

    @Test
    fun legacyAwgConfigDetectsAmneziaAndBuildsEndpoint() {
        val bean = parseWireGuardConfig(
            """
                [Interface]
                PrivateKey = client-private
                Address = 10.8.0.2/32
                MTU = 1360
                Jc = 5
                Jmin = 10
                Jmax = 50
                S1 = 139
                S2 = 60
                S3 = 43
                S4 = 12
                H1 = 100-200
                H2 = 300-400
                H3 = 500-600
                H4 = 700-800
                I1 = <r 16>

                [Peer]
                PublicKey = peer-public
                PresharedKey = peer-psk
                AllowedIPs = 0.0.0.0/0, ::/0
                Endpoint = 203.0.113.10:443
            """.trimIndent()
        ).single()

        assertTrue(bean.enableAmnezia == true)
        assertEquals(5, bean.jc)
        assertEquals(10, bean.jmin)
        assertEquals(50, bean.jmax)
        assertEquals(139, bean.s1)
        assertEquals(60, bean.s2)
        assertEquals(43, bean.s3)
        assertEquals(12, bean.s4)
        assertEquals("100-200", bean.h1)
        assertEquals("300-400", bean.h2)
        assertEquals("500-600", bean.h3)
        assertEquals("700-800", bean.h4)
        assertEquals("<r 16>", bean.i1)

        val endpoint = buildSingBoxEndpointAwgBean(bean)
        assertEquals("awg", endpoint.type)
        assertEquals("client-private", endpoint.private_key)
        assertEquals(listOf("10.8.0.2/32"), endpoint.address)
        assertEquals(1360, endpoint.mtu)
        assertEquals(5, endpoint.jc)
        assertEquals(10, endpoint.jmin)
        assertEquals(50, endpoint.jmax)
        assertEquals("100-200", endpoint.h1)
        assertEquals("<r 16>", endpoint.i1)
        assertEquals("203.0.113.10", endpoint.peers.single().address)
        assertEquals(443, endpoint.peers.single().port)
        assertEquals("peer-public", endpoint.peers.single().public_key)
        assertEquals("peer-psk", endpoint.peers.single().pre_shared_key)
        assertEquals(listOf("0.0.0.0/0", "::/0"), endpoint.peers.single().allowed_ips)
    }

    @Test
    fun wireGuardConfigParsesIpv6PeerEndpoint() {
        val bean = parseWireGuardConfig(
            """
                [Interface]
                PrivateKey = client-private
                Address = 10.0.0.2/32

                [Peer]
                PublicKey = peer-two
                Endpoint = [2001:db8::2]:10002
            """.trimIndent()
        ).single()

        assertEquals("peer-two", bean.peerPublicKey)
        assertEquals("2001:db8::2", bean.serverAddress)
        assertEquals(10002, bean.serverPort)
    }

    @Test
    fun legacyAwgUriParsesOptions() {
        val bean = parseWireGuardLinks(
            "awg://awg.example.com:51820" +
                    "?private_key=client" +
                    "&peer_public_key=peer" +
                    "&pre_shared_key=psk" +
                    "&local_address=10.8.0.2/32-fd00::2/128" +
                    "&mtu=1360" +
                    "&jc=5" +
                    "&jmin=10" +
                    "&jmax=50" +
                    "&s1=139" +
                    "&s2=60" +
                    "&s3=43" +
                    "&s4=12" +
                    "&h1=100-200" +
                    "&h2=300-400" +
                    "&h3=500-600" +
                    "&h4=700-800" +
                    "&i1=%3Cr%2016%3E" +
                    "#Legacy%20AWG"
        ).single()

        assertEquals("Legacy AWG", bean.name)
        assertEquals("awg.example.com", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertTrue(bean.enableAmnezia == true)
        assertEquals("10.8.0.2/32\nfd00::2/128", bean.localAddress)
        assertEquals(1360, bean.mtu)
        assertEquals(5, bean.jc)
        assertEquals(50, bean.jmax)
        assertEquals(139, bean.s1)
        assertEquals("100-200", bean.h1)
        assertEquals("<r 16>", bean.i1)
    }

    @Test
    fun vpnSchemeDecodesNativeAmneziaAwgConfig() {
        val awgConfig = """
            [Interface]
            PrivateKey = client
            Address = 10.8.0.2/32
            Jc = 5
            Jmin = 10
            Jmax = 40
            S1 = 16
            S2 = 32
            H1 = 123
            H2 = 456
            H3 = 789
            H4 = 101112

            [Peer]
            PublicKey = peer
            PresharedKey = psk
            Endpoint = [2001:db8::1]:443
        """.trimIndent()
        val json = JSONObject()
            .put("displayName", "Premium AWG")
            .put("dns1", "1.1.1.1")
            .put("dns2", "1.0.0.1")
            .put(
                "containers",
                JSONArray().put(
                    JSONObject().put(
                        "awg",
                        JSONObject().put(
                            "last_config",
                            JSONObject()
                                .put("config", awgConfig)
                                .put("mtu", 1360)
                                .toString()
                        )
                    )
                )
            )
            .toString()

        val link = "vpn://" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(qCompress(json.toByteArray(StandardCharsets.UTF_8)))
        val bean = parseWireGuardLinks(link).single()

        assertEquals("Premium AWG", bean.name)
        assertEquals("2001:db8::1", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals(1360, bean.mtu)
        assertTrue(bean.enableAmnezia == true)
        assertEquals(5, bean.jc)
        assertEquals(32, bean.s2)
        assertEquals("789", bean.h3)
    }

    @Test
    fun awg31NativeConfigPreservesAllProtocolFields() {
        val bean = parseWireGuardConfig(awg31Config()).single()

        assertTrue(bean.enableAmnezia == true)
        assertEquals("31.56.196.18", bean.serverAddress)
        assertEquals(33622, bean.serverPort)
        assertEquals(1376, bean.mtu)
        assertEquals(5, bean.jc)
        assertEquals(10, bean.jmin)
        assertEquals(50, bean.jmax)
        assertEquals(139, bean.s1)
        assertEquals(60, bean.s2)
        assertEquals(43, bean.s3)
        assertEquals(12, bean.s4)
        assertEquals("1", bean.h1)
        assertEquals("2", bean.h2)
        assertEquals("3", bean.h3)
        assertEquals("4", bean.h4)
        assertBeanField(bean, "headerProtectionKey", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertBeanField(bean, "contentPaddingAddition", "10-100")
        assertBeanField(bean, "rekeyAfterTime", "100-120")
        assertBeanField(bean, "rekeyTimeout", "3-7")
        assertBeanField(bean, "rejectAfterTime", "150-180")
        assertBeanField(bean, "keepaliveTimeout", "5-15")
        assertBeanField(bean, "maxHandshakeAttempts", "15-20")
        assertBeanField(bean, "randomTrailers", true)
        assertBeanField(bean, "disableCookies", true)
        assertBeanField(bean, "persistentKeepalive", "25-35")
    }

    @Test
    fun awg31ConfigBuildsSingBoxEndpointWithNewOptions() {
        val endpoint = buildSingBoxEndpointAwgBean(parseWireGuardConfig(awg31Config()).single())

        assertEndpointField(endpoint, "header_protection_key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertEndpointField(endpoint, "content_padding_addition", "10-100")
        assertEndpointField(endpoint, "rekey_after_time", "100-120")
        assertEndpointField(endpoint, "rekey_timeout", "3-7")
        assertEndpointField(endpoint, "reject_after_time", "150-180")
        assertEndpointField(endpoint, "keepalive_timeout", "5-15")
        assertEndpointField(endpoint, "max_handshake_attempts", "15-20")
        assertEndpointField(endpoint, "random_trailers", true)
        assertEndpointField(endpoint, "disable_cookies", true)
        assertEndpointField(endpoint.peers.single(), "persistent_keepalive_interval", "25-35")
    }

    @Test
    fun awg31FieldsSurviveBeanSerialization() {
        val clone = parseWireGuardConfig(awg31Config()).single().clone()

        assertBeanField(clone, "headerProtectionKey", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertBeanField(clone, "contentPaddingAddition", "10-100")
        assertBeanField(clone, "rekeyAfterTime", "100-120")
        assertBeanField(clone, "rekeyTimeout", "3-7")
        assertBeanField(clone, "rejectAfterTime", "150-180")
        assertBeanField(clone, "keepaliveTimeout", "5-15")
        assertBeanField(clone, "maxHandshakeAttempts", "15-20")
        assertBeanField(clone, "randomTrailers", true)
        assertBeanField(clone, "disableCookies", true)
        assertBeanField(clone, "persistentKeepalive", "25-35")
    }

    @Test
    fun vpnSchemeDecodesNativeAmneziaAwg31Config() {
        val lastConfig = JSONObject()
            .put("config", awg31Config())
            .put("mtu", 1376)
            .put("persistent_keep_alive", "25-35")
        val json = JSONObject()
            .put("description", "HostVDS")
            .put("containers", JSONArray().put(JSONObject().put("awg", JSONObject().put("last_config", lastConfig.toString()))))
            .toString()
        val link = "vpn://" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(qCompress(json.toByteArray(StandardCharsets.UTF_8)))

        val bean = parseWireGuardLinks(link).single()

        assertEquals("HostVDS", bean.name)
        assertBeanField(bean, "headerProtectionKey", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertBeanField(bean, "contentPaddingAddition", "10-100")
        assertBeanField(bean, "persistentKeepalive", "25-35")
    }

    private fun awg31Config() = """
        [Interface]
        Address = 10.8.1.5/32
        MTU = 1376
        PrivateKey = client
        Jc = 5
        Jmin = 10
        Jmax = 50
        S1 = 139
        S2 = 60
        S3 = 43
        S4 = 12
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4
        HeaderProtectionKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        ContentPaddingAddition = 10-100
        RekeyAfterTime = 100-120
        RekeyTimeout = 3-7
        RejectAfterTime = 150-180
        KeepaliveTimeout = 5-15
        MaxHandshakeAttempts = 15-20
        RandomTrailers = on
        DisableCookies = on

        [Peer]
        PublicKey = peer
        PresharedKey = psk
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = 31.56.196.18:33622
        PersistentKeepalive = 25-35
    """.trimIndent()

    private fun assertBeanField(bean: WireGuardBean, name: String, expected: Any?) {
        val field = WireGuardBean::class.java.getField(name)
        assertEquals(expected, field.get(bean))
    }

    private fun assertEndpointField(target: Any, name: String, expected: Any?) {
        val field = target.javaClass.getField(name)
        assertEquals(expected, field.get(target))
    }

    private fun qCompress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write((data.size ushr 24) and 0xff)
        output.write((data.size ushr 16) and 0xff)
        output.write((data.size ushr 8) and 0xff)
        output.write(data.size and 0xff)

        val deflater = Deflater(8)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            output.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return output.toByteArray()
    }
}

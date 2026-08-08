package io.nekohasekai.sagernet.fmt.wireguard

import org.junit.Assert.assertEquals
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

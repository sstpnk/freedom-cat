package tech.stpnk.freedomcat.fmt.v2ray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RealityShortIdTest {

    private fun realityBean(shortId: String) = VMessBean().apply {
        serverAddress = "147.45.213.169"
        serverPort = 25567
        uuid = "df096924-87be-4895-a1d0-4341760eed05"
        encryption = "xtls-rprx-vision"
        security = "tls"
        sni = "kookas.fi"
        realityPubKey = "qB_eMu3ZO_wGITnyIqJ7Kt39-h9oHFnHlQQEqA-r3VA"
        realityShortId = shortId
        utlsFingerprint = "firefox"
        // Equality goes through the binary form, so the bean has to be complete.
        initializeDefaultValues()
    }

    // A subscription refresh has to notice a rotated REALITY short id, otherwise the
    // profile keeps the old value and the handshake fails.
    @Test
    fun changedShortIdMarksProfileAsDifferent() {
        assertNotEquals(realityBean("43dcff52289b81e6"), realityBean("43dcff53849b81e6"))
    }

    @Test
    fun sameValueKeepsProfileUnchanged() {
        assertEquals(realityBean("43dcff53849b81e6"), realityBean("43dcff53849b81e6"))
    }

}

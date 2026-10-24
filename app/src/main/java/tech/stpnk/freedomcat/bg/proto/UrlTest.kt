package tech.stpnk.freedomcat.bg.proto

import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.ProxyEntity

class UrlTest {

    val link = DataStore.connectionTestURL
    private val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): Int {
        return TestInstance(profile, link, timeout).doTest()
    }

}
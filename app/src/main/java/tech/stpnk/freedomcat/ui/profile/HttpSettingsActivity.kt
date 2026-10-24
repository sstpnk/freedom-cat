package tech.stpnk.freedomcat.ui.profile

import tech.stpnk.freedomcat.fmt.http.HttpBean

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = HttpBean()

}

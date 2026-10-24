package tech.stpnk.freedomcat.ui.profile

import tech.stpnk.freedomcat.fmt.trojan.TrojanBean

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = TrojanBean()

}

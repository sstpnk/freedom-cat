package tech.stpnk.freedomcat.ui

import android.os.Bundle
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.SagerNet
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.ProfileManager
import tech.stpnk.freedomcat.ktx.runOnMainDispatcher

class SwitchActivity : ThemedActivity(R.layout.layout_empty),
    ConfigurationFragment.SelectCallback {

    override val isDialog = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_holder,
                ConfigurationFragment(true, null, R.string.action_switch)
            )
            .commitAllowingStateLoss()
    }

    override fun returnProfile(profileId: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = profileId
        runOnMainDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(profileId, true)
        }
        SagerNet.reloadService()
        finish()
    }
}
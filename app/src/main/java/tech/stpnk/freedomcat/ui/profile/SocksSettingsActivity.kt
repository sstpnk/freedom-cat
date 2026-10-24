package tech.stpnk.freedomcat.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import tech.stpnk.freedomcat.Key
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.preference.EditTextPreferenceModifiers
import tech.stpnk.freedomcat.fmt.socks.SOCKSBean
import tech.stpnk.freedomcat.core.ui.SimpleMenuPreference

class SocksSettingsActivity : ProfileSettingsActivity<SOCKSBean>() {
    override fun createEntity() = SOCKSBean()

    override fun SOCKSBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort

        DataStore.serverProtocolInt = protocol
        DataStore.serverUsername = username
        DataStore.serverPassword = password

        DataStore.profileCacheStore.putBoolean("sUoT", sUoT)
    }

    override fun SOCKSBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort

        protocol = DataStore.serverProtocolInt
        username = DataStore.serverUsername
        password = DataStore.serverPassword

        sUoT = DataStore.profileCacheStore.getBoolean("sUoT")
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.socks_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        val password = findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!

        fun updateProtocol(version: Int) {
            password.isVisible = version == SOCKSBean.PROTOCOL_SOCKS5
        }

        updateProtocol(DataStore.protocolVersion)
        protocol.setOnPreferenceChangeListener { _, newValue ->
            updateProtocol((newValue as String).toInt())
            true
        }
    }
}

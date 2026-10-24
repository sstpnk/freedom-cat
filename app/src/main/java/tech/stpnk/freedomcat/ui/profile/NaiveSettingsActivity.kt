package tech.stpnk.freedomcat.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import tech.stpnk.freedomcat.Key
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.preference.EditTextPreferenceModifiers
import tech.stpnk.freedomcat.fmt.naive.NaiveBean

class NaiveSettingsActivity : ProfileSettingsActivity<NaiveBean>() {

    override fun createEntity() = NaiveBean()

    override fun NaiveBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverProtocol = proto
        DataStore.serverSNI = sni
        DataStore.serverCertificates = certificates
        DataStore.serverHeaders = extraHeaders
        DataStore.serverInsecureConcurrency = insecureConcurrency
        DataStore.profileCacheStore.putBoolean("sUoT", sUoT)
    }

    override fun NaiveBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        proto = DataStore.serverProtocol
        sni = DataStore.serverSNI
        certificates = DataStore.serverCertificates
        extraHeaders = DataStore.serverHeaders.replace("\r\n", "\n")
        insecureConcurrency = DataStore.serverInsecureConcurrency
        sUoT = DataStore.profileCacheStore.getBoolean("sUoT")
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.naive_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_INSECURE_CONCURRENCY)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }

    override fun finish() {
        if (DataStore.profileName == "喵要打开隐藏功能") {
            DataStore.isExpert = true
        } else if (DataStore.profileName == "喵要关闭隐藏功能") {
            DataStore.isExpert = false
        }
        super.finish()
    }

}
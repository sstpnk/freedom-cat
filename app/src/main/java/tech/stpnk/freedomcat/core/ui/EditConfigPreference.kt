package tech.stpnk.freedomcat.core.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.preference.Preference
import tech.stpnk.freedomcat.Key
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.ktx.Logs
import tech.stpnk.freedomcat.ktx.app
import tech.stpnk.freedomcat.ui.profile.ConfigEditActivity

class EditConfigPreference : Preference {

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    init {
        intent = Intent(context, ConfigEditActivity::class.java)
    }

    var configKey = Key.SERVER_CONFIG
    var useConfigStore = false

    fun useConfigStore(key: String) {
        try {
            this.configKey = key
            useConfigStore = true
            intent = intent!!.apply {
                putExtra("useConfigStore", "1")
                putExtra("key", key)
            }
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    override fun getSummary(): CharSequence {
        val config =
            (if (useConfigStore) DataStore.configurationStore.getString(configKey) else DataStore.serverConfig)
                ?: ""
        return if (config.isBlank()) {
            return app.resources.getString(androidx.preference.R.string.not_set)
        } else {
            app.resources.getString(R.string.lines, config.split('\n').size)
        }
    }

    public override fun notifyChanged() {
        super.notifyChanged()
    }

}

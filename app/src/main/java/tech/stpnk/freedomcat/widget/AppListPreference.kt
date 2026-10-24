package tech.stpnk.freedomcat.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.ktx.app
import tech.stpnk.freedomcat.utils.PackageCache

class AppListPreference : Preference {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    )

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun getSummary(): CharSequence {
        val packages = DataStore.routePackages.split("\n").filter { it.isNotBlank() }.map {
            PackageCache.installedPackages[it]?.applicationInfo?.loadLabel(app.packageManager)
                ?: PackageCache.installedPluginPackages[it]?.applicationInfo?.loadLabel(app.packageManager)
                ?: it
        }
        if (packages.isEmpty()) {
            return context.getString(androidx.preference.R.string.not_set)
        }
        val count = packages.size
        if (count <= 5) return packages.joinToString("\n")
        return context.getString(R.string.apps_message, count)
    }

    fun postUpdate() {
        notifyChanged()
    }

}
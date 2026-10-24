package tech.stpnk.freedomcat.widget

import android.content.Context
import android.util.AttributeSet
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.database.AppDatabase
import tech.stpnk.freedomcat.core.ui.SimpleMenuPreference

class GroupPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.dropdownPreferenceStyle
) : SimpleMenuPreference(context, attrs, defStyle, 0) {

    init {
        val groups = AppDatabase.groupDao.allGroups()

        entries = groups.map { it.displayName() }.toTypedArray()
        entryValues = groups.map { "${it.id}" }.toTypedArray()
    }

    override fun getSummary(): CharSequence? {
        if (!value.isNullOrBlank() && value != "0") {
            return AppDatabase.groupDao.getById(value.toLong())?.displayName()
                ?: super.getSummary()
        }
        return super.getSummary()
    }

}
/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2017 by Mygod Studio <[Email1]>  *
 *                                                                             *
 *  This program is free software: you can redistribute it and/or modify       *
 *  it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                        *
 *                                                                             *
 *  This program is distributed in the hope that it will be useful,            *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 *  GNU General Public License for more details.                               *
 *                                                                             *
 *  You should have received a copy of the GNU General Public License          *
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                             *
 *******************************************************************************/

package tech.stpnk.freedomcat.ui

import android.app.Activity
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import tech.stpnk.freedomcat.FreedomCatApp
import tech.stpnk.freedomcat.aidl.IServiceBridge
import tech.stpnk.freedomcat.bg.BaseService
import tech.stpnk.freedomcat.bg.ServiceBridge

class QuickDisableShortcut : Activity(), ServiceBridge.Callback {
    private val connection = ServiceBridge(ServiceBridge.CONNECTION_ID_SHORTCUT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection.connect(this, this)
        if (Build.VERSION.SDK_INT >= 25) {
            getSystemService<ShortcutManager>()!!.reportShortcutUsed("disable")
        }
    }

    override fun onServiceConnected(service: IServiceBridge) {
        val state = BaseService.State.values()[service.state]
        if (state.canStop) {
            FreedomCatApp.stopService()
        }
        finish()
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {}

    override fun onDestroy() {
        connection.disconnect(this)
        super.onDestroy()
    }
}

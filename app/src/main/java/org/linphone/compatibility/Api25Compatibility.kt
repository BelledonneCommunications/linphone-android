/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.compatibility

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.linphone.utils.ShortcutsHelper

@TargetApi(25)
class Api25Compatibility {
    companion object {
        fun getDeviceName(context: Context): String {
            var name = Settings.Global.getString(
                context.contentResolver, Settings.Global.DEVICE_NAME
            )
            if (name == null) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                name = adapter?.name
            }
            if (name == null) {
                name = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }
            if (name == null) {
                name = Build.MANUFACTURER + " " + Build.MODEL
            }
            return name
        }

        fun createShortcutsToContacts(context: Context) {
            ShortcutsHelper.createShortcutsToContacts(context)
        }

        fun createShortcutsToChatRooms(context: Context) {
            ShortcutsHelper.createShortcutsToChatRooms(context)
        }

        fun removeShortcuts(context: Context) {
            ShortcutsHelper.removeShortcuts(context)
        }
    }
}

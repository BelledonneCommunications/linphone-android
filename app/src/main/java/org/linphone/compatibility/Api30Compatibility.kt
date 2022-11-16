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

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.ShortcutManager
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

@TargetApi(30)
class Api30Compatibility {
    companion object {
        fun hasReadPhoneNumbersPermission(context: Context): Boolean {
            val granted = Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
            if (granted) {
                Log.d("[Permission Helper] Permission READ_PHONE_NUMBERS is granted")
            } else {
                Log.w("[Permission Helper] Permission READ_PHONE_NUMBERS is denied")
            }
            return granted
        }

        fun requestReadPhoneNumbersPermission(fragment: Fragment, code: Int) {
            fragment.requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), code)
        }

        fun requestTelecomManagerPermission(activity: Activity, code: Int) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_PHONE_NUMBERS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.MANAGE_OWN_CALLS
                ),
                code
            )
        }

        fun hasTelecomManagerPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_NUMBERS) &&
                Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_STATE) &&
                Compatibility.hasPermission(context, Manifest.permission.MANAGE_OWN_CALLS)
        }

        fun removeChatRoomShortcut(context: Context, chatRoom: ChatRoom) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
            val shortcutsToRemoveList = arrayListOf(id)
            shortcutManager.removeLongLivedShortcuts(shortcutsToRemoveList)
        }

        fun hideAndroidSystemUI(hide: Boolean, window: Window) {
            val windowInsetsCompat = WindowInsetsControllerCompat(window, window.decorView)
            if (hide) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                windowInsetsCompat.let {
                    it.hide(WindowInsetsCompat.Type.systemBars())
                    it.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                windowInsetsCompat.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }
}

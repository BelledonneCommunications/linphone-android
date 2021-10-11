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

        fun requestReadPhoneNumbersPermission(activity: Activity, code: Int) {
            activity.requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), code)
        }

        fun removeChatRoomShortcut(context: Context, chatRoom: ChatRoom) {
            val peerAddress = chatRoom.peerAddress.asStringUriOnly()
            val localAddress = chatRoom.localAddress.asStringUriOnly()

            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            val id = LinphoneUtils.getChatRoomId(localAddress, peerAddress)
            val shortcutsToRemoveList = arrayListOf(id)
            shortcutManager.removeLongLivedShortcuts(shortcutsToRemoveList)
        }
    }
}

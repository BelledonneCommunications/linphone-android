/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.core

import com.google.firebase.messaging.RemoteMessage
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log

class CoreFirebaseMessaging : org.linphone.core.tools.firebase.FirebaseMessaging() {
    companion object {
        private const val TAG = "[Core Firebase Messaging]"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val callId = remoteMessage.data["call-id"].orEmpty()
        val isCallPush = callId.isNotEmpty()

        if (corePreferences.isDoNotDisturbActive && !isCallPush) {
            Log.i("$TAG Do Not Disturb is active, ignoring FCM message push (no call-id)")
            return
        }

        if (corePreferences.isDoNotDisturbActive) {
            Log.i("$TAG Do Not Disturb is active but this is a call push (call-id=[$callId]), processing to allow SIP decline")
        }

        super.onMessageReceived(remoteMessage)
    }
}

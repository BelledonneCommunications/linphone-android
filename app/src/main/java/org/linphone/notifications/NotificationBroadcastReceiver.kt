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
package org.linphone.notifications

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.ensureCoreExists
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.tools.Log

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("[Notification Broadcast Receiver] Ensuring Core exists")
        ensureCoreExists(context, false)

        val notificationId = intent.getIntExtra(NotificationsManager.INTENT_NOTIF_ID, 0)
        Log.i("[Notification Broadcast Receiver] Got notification broadcast for ID [$notificationId]")

        if (intent.action == NotificationsManager.INTENT_REPLY_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_MARK_AS_READ_ACTION) {
            handleChatIntent(context, intent, notificationId)
        } else if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION) {
            handleCallIntent(intent)
        }
    }

    private fun handleChatIntent(context: Context, intent: Intent, notificationId: Int) {
        val remoteSipAddress = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_ADDRESS)
        if (remoteSipAddress == null) {
            Log.e("[Notification Broadcast Receiver] Remote SIP address is null for notification id $notificationId")
            return
        }
        val core: Core = coreContext.core

        val remoteAddress = core.interpretUrl(remoteSipAddress, false)
        if (remoteAddress == null) {
            Log.e("[Notification Broadcast Receiver] Couldn't interpret remote address $remoteSipAddress")
            return
        }

        val localIdentity = intent.getStringExtra(NotificationsManager.INTENT_LOCAL_IDENTITY)
        if (localIdentity == null) {
            Log.e("[Notification Broadcast Receiver] Local identity is null for notification id $notificationId")
            return
        }
        val localAddress = core.interpretUrl(localIdentity, false)
        if (localAddress == null) {
            Log.e("[Notification Broadcast Receiver] Couldn't interpret local address $localIdentity")
            return
        }

        val room = core.searchChatRoom(null, localAddress, remoteAddress, arrayOfNulls(0))
        if (room == null) {
            Log.e("[Notification Broadcast Receiver] Couldn't find chat room for remote address $remoteSipAddress and local address $localIdentity")
            return
        }

        room.markAsRead()
        if (intent.action == NotificationsManager.INTENT_REPLY_NOTIF_ACTION) {
            val reply = getMessageText(intent)?.toString()
            if (reply == null) {
                Log.e("[Notification Broadcast Receiver] Couldn't get reply text")
                return
            }

            val msg = room.createMessageFromUtf8(reply)
            msg.userData = notificationId
            msg.addListener(coreContext.notificationsManager.chatListener)
            msg.send()
            Log.i("[Notification Broadcast Receiver] Reply sent for notif id $notificationId")
        } else {
            if (!coreContext.notificationsManager.dismissChatNotification(room)) {
                Log.w("[Notification Broadcast Receiver] Notifications Manager failed to cancel notification")
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.cancel(NotificationsManager.CHAT_TAG, notificationId)
            }
        }
    }

    private fun handleCallIntent(intent: Intent) {
        val remoteSipAddress = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_ADDRESS)
        if (remoteSipAddress == null) {
            Log.e("[Notification Broadcast Receiver] Remote SIP address is null for notification")
            return
        }

        val core: Core = coreContext.core

        val remoteAddress = core.interpretUrl(remoteSipAddress, false)
        val call = if (remoteAddress != null) core.getCallByRemoteAddress2(remoteAddress) else null
        if (call == null) {
            Log.e("[Notification Broadcast Receiver] Couldn't find call from remote address $remoteSipAddress")
            return
        }

        if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION) {
            coreContext.answerCall(call)
        } else {
            if (call.state == Call.State.IncomingReceived ||
                call.state == Call.State.IncomingEarlyMedia
            ) {
                coreContext.declineCall(call)
            } else {
                coreContext.terminateCall(call)
            }
        }
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(NotificationsManager.KEY_TEXT_REPLY)
    }
}

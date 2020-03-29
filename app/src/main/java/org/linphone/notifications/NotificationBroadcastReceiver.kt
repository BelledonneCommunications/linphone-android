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

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.tools.Log

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationsManager.INTENT_NOTIF_ID, 0)
        val localIdentity = intent.getStringExtra(NotificationsManager.INTENT_LOCAL_IDENTITY)

        if (intent.action == NotificationsManager.INTENT_REPLY_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_MARK_AS_READ_ACTION) {
            val remoteSipAddress: String? = coreContext.notificationsManager.getSipUriForChatNotificationId(notificationId)
            val core: Core = coreContext.core

            val remoteAddress = core.interpretUrl(remoteSipAddress)
            if (remoteAddress == null) {
                Log.e("[Notification Broadcast Receiver] Couldn't interpret remote address $remoteSipAddress")
                return
            }

            val localAddress = core.interpretUrl(localIdentity)
            if (localAddress == null) {
                Log.e("[Notification Broadcast Receiver] Couldn't interpret local address $localIdentity")
                return
            }

            val room = core.getChatRoom(remoteAddress, localAddress)
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

                val msg = room.createMessage(reply)
                msg.userData = notificationId
                msg.addListener(coreContext.notificationsManager.chatListener)
                msg.send()
                Log.i("[Notification Broadcast Receiver] Reply sent for notif id $notificationId")
            } else {
                coreContext.notificationsManager.cancel(notificationId)
            }
        } else if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION) {
            val remoteAddress: String? = coreContext.notificationsManager.getSipUriForCallNotificationId(notificationId)
            val core: Core = coreContext.core

            val call = core.findCallFromUri(remoteAddress)
            if (call == null) {
                Log.e("[Notification Broadcast Receiver] Couldn't find call from remote address $remoteAddress")
                return
            }

            if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION) {
                coreContext.answerCall(call)
            } else {
                if (call.state == Call.State.IncomingReceived || call.state == Call.State.IncomingEarlyMedia) coreContext.declineCall(call) else coreContext.terminateCall(call)
            }
        }
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(NotificationsManager.KEY_TEXT_REPLY)
    }
}

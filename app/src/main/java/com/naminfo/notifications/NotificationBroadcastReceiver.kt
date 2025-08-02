/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package com.naminfo.notifications

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naminfo.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.ConferenceParams
import org.linphone.core.tools.Log
import com.naminfo.utils.AudioUtils

class NotificationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "[Notification Broadcast Receiver]"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationsManager.INTENT_NOTIF_ID, 0)
        val action = intent.action
        Log.i("$TAG Got notification broadcast for ID [$notificationId] with action [$action]")

        // Wait for coreContext to be ready to handle intent
        while (!coreContext.isReady()) {
            Thread.sleep(50)
        }

        if (
            action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION ||
            action == NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION ||
            action == NotificationsManager.INTENT_TOGGLE_SPEAKER_CALL_NOTIF_ACTION
        ) {
            handleCallIntent(intent, notificationId, action)
        } else if (
            action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION ||
            action == NotificationsManager.INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION
        ) {
            handleChatIntent(context, intent, notificationId, action)
        }
    }

    private fun handleCallIntent(intent: Intent, notificationId: Int, action: String) {
        val remoteSipUri = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_SIP_URI)
        if (remoteSipUri == null) {
            Log.e("$TAG Remote SIP address is null for call notification ID [$notificationId]")
            return
        }

        coreContext.postOnCoreThread { core ->
            val call = core.calls.find {
                it.remoteAddress.asStringUriOnly() == remoteSipUri
            }
            if (call == null) {
                Log.e("$TAG Couldn't find call from remote address [$remoteSipUri]")
            } else {
                when (action) {
                    NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION -> {
                        Log.i("$TAG Answering call with remote address [$remoteSipUri]")
                        coreContext.answerCall(call)
                    }
                    NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION -> {
                        Log.i("$TAG Declining/terminating call with remote address [$remoteSipUri]")
                        coreContext.terminateCall(call)
                    }
                    NotificationsManager.INTENT_TOGGLE_SPEAKER_CALL_NOTIF_ACTION -> {
                        val audioDevice = call.outputAudioDevice
                        val isUsingSpeaker = audioDevice?.type == AudioDevice.Type.Speaker
                        if (isUsingSpeaker) {
                            Log.i("$TAG Routing audio to earpiece for call [$remoteSipUri]")
                            AudioUtils.routeAudioToEarpiece(call)
                        } else {
                            Log.i("$TAG Routing audio to speaker for call [$remoteSipUri]")
                            AudioUtils.routeAudioToSpeaker(call)
                        }
                    }
                }
            }
        }
    }

    private fun handleChatIntent(context: Context, intent: Intent, notificationId: Int, action: String) {
        val remoteSipAddress = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_SIP_URI)
        if (remoteSipAddress == null) {
            Log.e("$TAG Remote SIP address is null for notification ID [$notificationId]")
            return
        }
        val localIdentity = intent.getStringExtra(NotificationsManager.INTENT_LOCAL_IDENTITY)
        if (localIdentity == null) {
            Log.e("$TAG Local identity is null for notification ID [$notificationId]")
            return
        }

        val reply = getMessageText(intent)?.toString()
        if (action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION) {
            if (reply == null) {
                Log.e("$TAG Couldn't get reply text")
                return
            }
        }

        coreContext.postOnCoreThread { core ->
            val remoteAddress = core.interpretUrl(remoteSipAddress, false)
            if (remoteAddress == null) {
                Log.e(
                    "$TAG Couldn't interpret remote address [$remoteSipAddress]"
                )
                return@postOnCoreThread
            }

            val localAddress = core.interpretUrl(localIdentity, false)
            if (localAddress == null) {
                Log.e(
                    "$TAG Couldn't interpret local address [$localIdentity]"
                )
                return@postOnCoreThread
            }

            val params: ConferenceParams? = null
            val room = core.searchChatRoom(
                params,
                localAddress,
                remoteAddress,
                arrayOfNulls<Address>(
                    0
                )
            )
            if (room == null) {
                Log.e(
                    "$TAG Couldn't find conversation for remote address [$remoteSipAddress] and local address [$localIdentity]"
                )
                return@postOnCoreThread
            }

            if (action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION) {
                val msg = room.createMessageFromUtf8(reply)
                msg.userData = notificationId
                msg.addListener(coreContext.notificationsManager.chatMessageListener)
                msg.send()
                Log.i("$TAG Reply sent for notif id [$notificationId]")
            } else if (action == NotificationsManager.INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION) {
                Log.i("$TAG Marking chat room from notification id [$notificationId] as read")
                room.markAsRead()
                if (!coreContext.notificationsManager.dismissChatNotification(room)) {
                    Log.w(
                        "$TAG Notifications Manager failed to cancel notification"
                    )
                    val notificationManager = context.getSystemService(
                        NotificationManager::class.java
                    )
                    notificationManager.cancel(NotificationsManager.CHAT_TAG, notificationId)
                }
            }
        }
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(NotificationsManager.KEY_TEXT_REPLY)
    }
}

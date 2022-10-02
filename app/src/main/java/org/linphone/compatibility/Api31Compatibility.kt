/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.getThumbnailUri
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.notifications.Notifiable
import org.linphone.notifications.NotificationsManager
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

@TargetApi(31)
class Api31Compatibility {
    companion object {
        fun getUpdateCurrentPendingIntentFlag(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        }

        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val remoteContact = call.remoteContact
            val conferenceAddress = if (remoteContact != null) coreContext.core.interpretUrl(remoteContact, false) else null
            val conferenceInfo = if (conferenceAddress != null) coreContext.core.findConferenceInformationFromUri(conferenceAddress) else null
            if (conferenceInfo != null) {
                Log.i("[Notifications Manager] Displaying incoming group call notification with subject ${conferenceInfo.subject} and remote contact address $remoteContact")
            } else {
                Log.i("[Notifications Manager] No conference info found for remote contact address $remoteContact")
            }

            val caller = if (conferenceInfo == null) {
                val contact =
                    coreContext.contactsManager.findContactByAddress(call.remoteAddress)
                val roundPicture =
                    ImageUtils.getRoundBitmapFromUri(context, contact?.getThumbnailUri())
                val displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)

                val person = notificationsManager.getPerson(contact, displayName, roundPicture)
                Person.Builder()
                    .setName(person.name)
                    .setIcon(person.icon?.toIcon(context))
                    .setUri(person.uri)
                    .setKey(person.key)
                    .setImportant(person.isImportant)
                    .build()
            } else {
                Person.Builder()
                    .setName(if (conferenceInfo.subject.isNullOrEmpty()) context.getString(R.string.conference_incoming_title) else conferenceInfo.subject)
                    .setIcon(coreContext.contactsManager.groupAvatar.toIcon(context))
                    .setImportant(false)
                    .build()
            }

            val declineIntent = notificationsManager.getCallDeclinePendingIntent(notifiable)
            val answerIntent = notificationsManager.getCallAnswerPendingIntent(notifiable)

            val isVideoEnabledInRemoteParams = call.remoteParams?.isVideoEnabled ?: false
            val isVideoAutomaticallyAccepted = call.core.videoActivationPolicy.automaticallyAccept
            val isVideo = isVideoEnabledInRemoteParams && isVideoAutomaticallyAccepted

            val builder = Notification.Builder(context, context.getString(R.string.notification_channel_incoming_call_id)).apply {
                try {
                    style = Notification.CallStyle.forIncomingCall(
                        caller,
                        declineIntent,
                        answerIntent
                    ).setIsVideo(isVideo)
                } catch (iae: IllegalArgumentException) {
                    Log.e("[Api31 Compatibility] Can't use notification call style: $iae, using API 26 notification instead")
                    return Api26Compatibility.createIncomingCallNotification(context, call, notifiable, pendingIntent, notificationsManager)
                }
                setSmallIcon(R.drawable.topbar_call_notification)
                setCategory(Notification.CATEGORY_CALL)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setWhen(System.currentTimeMillis())
                setAutoCancel(false)
                setShowWhen(true)
                setOngoing(true)
                setColor(ContextCompat.getColor(context, R.color.primary_color))
                setFullScreenIntent(pendingIntent, true)
            }

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }

        fun createCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            channel: String,
            notificationsManager: NotificationsManager
        ): Notification {
            val conferenceAddress = LinphoneUtils.getConferenceAddress(call)
            val conferenceInfo = if (conferenceAddress != null) coreContext.core.findConferenceInformationFromUri(conferenceAddress) else null
            if (conferenceInfo != null) {
                Log.i("[Notifications Manager] Displaying group call notification with subject ${conferenceInfo.subject}")
            } else {
                Log.i("[Notifications Manager] No conference info found for remote contact address ${call.remoteAddress} (${call.remoteContact})")
            }

            val caller = if (conferenceInfo == null) {
                val contact =
                    coreContext.contactsManager.findContactByAddress(call.remoteAddress)
                val roundPicture =
                    ImageUtils.getRoundBitmapFromUri(context, contact?.getThumbnailUri())
                val displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)

                val person = notificationsManager.getPerson(contact, displayName, roundPicture)
                Person.Builder()
                    .setName(person.name)
                    .setIcon(person.icon?.toIcon(context))
                    .setUri(person.uri)
                    .setKey(person.key)
                    .setImportant(person.isImportant)
                    .build()
            } else {
                Person.Builder()
                    .setName(conferenceInfo.subject)
                    .setIcon(coreContext.contactsManager.groupAvatar.toIcon(context))
                    .setImportant(false)
                    .build()
            }
            val isVideo = call.currentParams.isVideoEnabled
            val iconResourceId: Int = when (call.state) {
                Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote -> {
                    R.drawable.topbar_call_paused_notification
                }
                else -> {
                    if (isVideo) {
                        R.drawable.topbar_videocall_notification
                    } else {
                        R.drawable.topbar_call_notification
                    }
                }
            }
            val declineIntent = notificationsManager.getCallDeclinePendingIntent(notifiable)

            val builder = Notification.Builder(
                context, channel
            ).apply {
                try {
                    style = Notification.CallStyle.forOngoingCall(caller, declineIntent)
                        .setIsVideo(isVideo)
                } catch (iae: IllegalArgumentException) {
                    Log.e("[Api31 Compatibility] Can't use notification call style: $iae, using API 26 notification instead")
                    return Api26Compatibility.createCallNotification(context, call, notifiable, pendingIntent, channel, notificationsManager)
                }
                setSmallIcon(iconResourceId)
                setAutoCancel(false)
                setCategory(Notification.CATEGORY_CALL)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setWhen(System.currentTimeMillis())
                setShowWhen(true)
                setOngoing(true)
                setColor(ContextCompat.getColor(context, R.color.notification_led_color))
                // This is required for CallStyle notification
                setFullScreenIntent(pendingIntent, true)
            }

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }

        fun startForegroundService(context: Context, intent: Intent) {
            try {
                context.startForegroundService(intent)
            } catch (fssnae: ForegroundServiceStartNotAllowedException) {
                Log.e("[Api31 Compatibility] Can't start service as foreground! $fssnae")
            }
        }

        fun startForegroundService(service: Service, notifId: Int, notif: Notification?) {
            try {
                service.startForeground(notifId, notif)
            } catch (fssnae: ForegroundServiceStartNotAllowedException) {
                Log.e("[Api31 Compatibility] Can't start service as foreground! $fssnae")
            }
        }

        fun enableAutoEnterPiP(activity: Activity, enable: Boolean, conference: Boolean) {
            val supportsPip = activity.packageManager
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            Log.i("[Call] Is PiP supported: $supportsPip")
            if (supportsPip) {
                // Force portrait layout if in conference, otherwise for landscape
                // Our layouts behave better in these orientation
                val params = PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(enable)
                    .setAspectRatio(Compatibility.getPipRatio(activity, conference, !conference))
                    .build()
                try {
                    activity.setPictureInPictureParams(params)
                    Log.i("[Call] PiP auto enter enabled params set to $enable with ${if (conference) "portrait" else "landscape"} aspect ratio")
                } catch (e: Exception) {
                    Log.e("[Call] Can't build PiP params: $e")
                }
            }
        }

        fun hasBluetoothConnectPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}

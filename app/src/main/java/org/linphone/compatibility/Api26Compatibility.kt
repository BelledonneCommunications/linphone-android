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
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.Contact
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.notifications.Notifiable
import org.linphone.notifications.NotificationsManager
import org.linphone.telecom.NativeCallWrapper
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

@TargetApi(26)
class Api26Compatibility {
    companion object {
        fun enterPipMode(activity: Activity) {
            val supportsPip = activity.packageManager
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            Log.i("[Call] Is picture in picture supported: $supportsPip")
            if (supportsPip) {
                val params = PictureInPictureParams.Builder().build()
                if (!activity.enterPictureInPictureMode(params)) {
                    Log.e("[Call] Failed to enter picture in picture mode")
                }
            }
        }

        fun createServiceChannel(context: Context, notificationManager: NotificationManagerCompat) {
            // Create service notification channel
            val id = context.getString(R.string.notification_channel_service_id)
            val name = context.getString(R.string.notification_channel_service_name)
            val description = context.getString(R.string.notification_channel_service_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.enableVibration(false)
            channel.enableLights(false)
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        fun createMissedCallChannel(
            context: Context,
            notificationManager: NotificationManagerCompat
        ) {
            val id = context.getString(R.string.notification_channel_missed_call_id)
            val name = context.getString(R.string.notification_channel_missed_call_name)
            val description = context.getString(R.string.notification_channel_missed_call_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.lightColor = context.getColor(R.color.notification_led_color)
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.setShowBadge(true)
            notificationManager.createNotificationChannel(channel)
        }

        fun createIncomingCallChannel(
            context: Context,
            notificationManager: NotificationManagerCompat
        ) {
            // Create incoming calls notification channel
            val id = context.getString(R.string.notification_channel_incoming_call_id)
            val name = context.getString(R.string.notification_channel_incoming_call_name)
            val description = context.getString(R.string.notification_channel_incoming_call_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            channel.lightColor = context.getColor(R.color.notification_led_color)
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.setShowBadge(true)
            notificationManager.createNotificationChannel(channel)
        }

        fun createMessageChannel(
            context: Context,
            notificationManager: NotificationManagerCompat
        ) {
            // Create messages notification channel
            val id = context.getString(R.string.notification_channel_chat_id)
            val name = context.getString(R.string.notification_channel_chat_name)
            val description = context.getString(R.string.notification_channel_chat_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            channel.lightColor = context.getColor(R.color.notification_led_color)
            channel.enableLights(true)
            channel.enableVibration(true)
            channel.setShowBadge(true)
            notificationManager.createNotificationChannel(channel)
        }

        fun getChannelImportance(
            notificationManager: NotificationManagerCompat,
            channelId: String
        ): Int {
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        }

        fun getOverlayType(): Int {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val contact: Contact? = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            val pictureUri = contact?.getContactThumbnailPictureUri()
            val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
            val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress)
            val address = LinphoneUtils.getDisplayableAddress(call.remoteAddress)

            val notificationLayoutHeadsUp = RemoteViews(context.packageName, R.layout.call_incoming_notification_heads_up)
            notificationLayoutHeadsUp.setTextViewText(R.id.caller, displayName)
            notificationLayoutHeadsUp.setTextViewText(R.id.sip_uri, address)
            notificationLayoutHeadsUp.setTextViewText(R.id.incoming_call_info, context.getString(R.string.incoming_call_notification_title))

            if (roundPicture != null) {
                notificationLayoutHeadsUp.setImageViewBitmap(R.id.caller_picture, roundPicture)
            }

            val builder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setSmallIcon(R.drawable.topbar_call_notification)
                .setContentTitle(displayName)
                .setContentText(context.getString(R.string.incoming_call_notification_title))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.primary_color))
                .setFullScreenIntent(pendingIntent, true)
                .addAction(notificationsManager.getCallDeclineAction(notifiable))
                .addAction(notificationsManager.getCallAnswerAction(notifiable))
                .setCustomHeadsUpContentView(notificationLayoutHeadsUp)

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
            val contact: Contact? = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            val pictureUri = contact?.getContactThumbnailPictureUri()
            val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
            val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress)

            val stringResourceId: Int
            val iconResourceId: Int
            when (call.state) {
                Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote -> {
                    stringResourceId = R.string.call_notification_paused
                    iconResourceId = R.drawable.topbar_call_paused_notification
                }
                Call.State.OutgoingRinging, Call.State.OutgoingProgress, Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia -> {
                    stringResourceId = R.string.call_notification_outgoing
                    iconResourceId = if (call.params.isVideoEnabled) {
                        R.drawable.topbar_videocall_notification
                    } else {
                        R.drawable.topbar_call_notification
                    }
                }
                else -> {
                    stringResourceId = R.string.call_notification_active
                    iconResourceId = if (call.currentParams.isVideoEnabled) {
                        R.drawable.topbar_videocall_notification
                    } else {
                        R.drawable.topbar_call_notification
                    }
                }
            }

            val builder = NotificationCompat.Builder(
                context, channel
            )
                .setContentTitle(contact?.fullName ?: displayName)
                .setContentText(context.getString(stringResourceId))
                .setSmallIcon(iconResourceId)
                .setLargeIcon(roundPicture)
                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.notification_led_color))
                .addAction(notificationsManager.getCallDeclineAction(notifiable))

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }

        @SuppressLint("MissingPermission")
        fun eventVibration(vibrator: Vibrator) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0L, 100L, 100L), intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0), -1)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            vibrator.vibrate(effect, audioAttrs)
        }

        fun changeAudioRouteForTelecomManager(connection: NativeCallWrapper, route: Int) {
            connection.setAudioRoute(route)
        }

        fun requestTelecomManagerPermission(activity: Activity, code: Int) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.MANAGE_OWN_CALLS
                ),
                code
            )
        }

        fun hasTelecomManagerPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_STATE) &&
                Compatibility.hasPermission(context, Manifest.permission.MANAGE_OWN_CALLS)
        }

        fun getImeFlagsForSecureChatRoom(): Int {
            return EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
    }
}

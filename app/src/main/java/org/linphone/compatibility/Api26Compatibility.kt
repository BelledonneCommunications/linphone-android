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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.telecom.NativeCallWrapper

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
    }
}

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

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Rational
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import java.util.*
import org.linphone.core.Call
import org.linphone.core.ChatRoom
import org.linphone.core.Content
import org.linphone.mediastream.Version
import org.linphone.notifications.Notifiable
import org.linphone.notifications.NotificationsManager
import org.linphone.telecom.NativeCallWrapper

@Suppress("DEPRECATION")
class Compatibility {
    companion object {
        const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

        fun hasPermission(context: Context, permission: String): Boolean {
            return Api23Compatibility.hasPermission(context, permission)
        }

        // See https://developer.android.com/about/versions/11/privacy/permissions#phone-numbers
        fun hasReadPhoneStateOrNumbersPermission(context: Context): Boolean {
            return if (Version.sdkAboveOrEqual(Version.API30_ANDROID_11)) {
                Api30Compatibility.hasReadPhoneNumbersPermission(context)
            } else {
                Api29Compatibility.hasReadPhoneStatePermission(context)
            }
        }

        // See https://developer.android.com/about/versions/11/privacy/permissions#phone-numbers
        fun requestReadPhoneStateOrNumbersPermission(fragment: Fragment, code: Int) {
            if (Version.sdkAboveOrEqual(Version.API30_ANDROID_11)) {
                Api30Compatibility.requestReadPhoneNumbersPermission(fragment, code)
            } else {
                Api23Compatibility.requestReadPhoneStatePermission(fragment, code)
            }
        }

        fun hasBluetoothConnectPermission(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                return Api31Compatibility.hasBluetoothConnectPermission(context)
            }
            return true
        }

        // See https://developer.android.com/about/versions/11/privacy/permissions#phone-numbers
        fun hasTelecomManagerPermissions(context: Context): Boolean {
            return if (Version.sdkAboveOrEqual(Version.API30_ANDROID_11)) {
                Api30Compatibility.hasTelecomManagerPermission(context)
            } else if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                Api29Compatibility.hasTelecomManagerPermission(context)
            } else {
                false
            }
        }

        fun requestTelecomManagerPermissions(activity: Activity, code: Int) {
            if (Version.sdkAboveOrEqual(Version.API30_ANDROID_11)) {
                Api30Compatibility.requestTelecomManagerPermission(activity, code)
            } else {
                Api26Compatibility.requestTelecomManagerPermission(activity, code)
            }
        }

        fun requestPostNotificationsPermission(fragment: Fragment, code: Int) {
            if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                Api33Compatibility.requestPostNotificationsPermission(fragment, code)
            }
        }

        fun hasPostNotificationsPermission(context: Context): Boolean {
            return if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                Api33Compatibility.hasPostNotificationsPermission(context)
            } else true
        }

        fun requestReadExternalStorageAndCameraPermissions(fragment: Fragment, code: Int) {
            if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                Api33Compatibility.requestReadMediaAndCameraPermissions(fragment, code)
            } else {
                Api23Compatibility.requestReadExternalStorageAndCameraPermissions(fragment, code)
            }
        }

        fun hasReadExternalStoragePermission(context: Context): Boolean {
            return if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                Api33Compatibility.hasReadExternalStoragePermission(context)
            } else {
                Api23Compatibility.hasReadExternalStoragePermission(context)
            }
        }

        fun getDeviceName(context: Context): String {
            return when (Version.sdkAboveOrEqual(Version.API25_NOUGAT_71)) {
                true -> Api25Compatibility.getDeviceName(context)
                else -> Api23Compatibility.getDeviceName(context)
            }
        }

        fun createPhoneListener(telephonyManager: TelephonyManager): PhoneStateInterface {
            return if (Version.sdkStrictlyBelow(Version.API31_ANDROID_12)) {
                PhoneStateListener(telephonyManager)
            } else {
                TelephonyListener(telephonyManager)
            }
        }

        /* UI */

        fun setShowWhenLocked(activity: Activity, enable: Boolean) {
            if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
                Api23Compatibility.setShowWhenLocked(activity, enable)
            } else {
                Api27Compatibility.setShowWhenLocked(activity, enable)
            }
        }

        fun setTurnScreenOn(activity: Activity, enable: Boolean) {
            if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
                Api23Compatibility.setTurnScreenOn(activity, enable)
            } else {
                Api27Compatibility.setTurnScreenOn(activity, enable)
            }
        }

        fun requestDismissKeyguard(activity: Activity) {
            if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
                Api23Compatibility.requestDismissKeyguard(activity)
            } else {
                Api27Compatibility.requestDismissKeyguard(activity)
            }
        }

        fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
            return if (Version.sdkStrictlyBelow(Version.API29_ANDROID_10)) {
                Api23Compatibility.getBitmapFromUri(context, uri)
            } else {
                Api29Compatibility.getBitmapFromUri(context, uri)
            }
        }

        /* Notifications */

        fun createNotificationChannels(
            context: Context,
            notificationManager: NotificationManagerCompat
        ) {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                Api26Compatibility.createServiceChannel(context, notificationManager)
                Api26Compatibility.createMissedCallChannel(context, notificationManager)
                Api26Compatibility.createIncomingCallChannel(context, notificationManager)
                if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                    Api29Compatibility.createMessageChannel(context, notificationManager)
                } else {
                    Api26Compatibility.createMessageChannel(context, notificationManager)
                }
            }
        }

        fun getChannelImportance(
            notificationManager: NotificationManagerCompat,
            channelId: String
        ): Int {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                return Api26Compatibility.getChannelImportance(notificationManager, channelId)
            }
            return NotificationManagerCompat.IMPORTANCE_DEFAULT
        }

        fun getOverlayType(): Int {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                return Api26Compatibility.getOverlayType()
            }
            return WindowManager.LayoutParams.TYPE_PHONE
        }

        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
            // Samsung One UI 4.0 (API 31) doesn't (currently) display CallStyle notifications well
            // Tested on Samsung S10 and Z Fold 2
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12) && manufacturer != "samsung") {
                return Api31Compatibility.createIncomingCallNotification(context, call, notifiable, pendingIntent, notificationsManager)
            } else if (manufacturer == "xiaomi") { // Xiaomi devices don't handle CustomHeadsUpContentView correctly
                return XiaomiCompatibility.createIncomingCallNotification(context, call, notifiable, pendingIntent, notificationsManager)
            }
            return Api26Compatibility.createIncomingCallNotification(context, call, notifiable, pendingIntent, notificationsManager)
        }

        fun createCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            channel: String,
            notificationsManager: NotificationsManager
        ): Notification {
            val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
            // Samsung One UI 4.0 (API 31) doesn't (currently) display CallStyle notifications well
            // Tested on Samsung S10 and Z Fold 2
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12) && manufacturer != "samsung") {
                return Api31Compatibility.createCallNotification(context, call, notifiable, pendingIntent, channel, notificationsManager)
            }
            return Api26Compatibility.createCallNotification(context, call, notifiable, pendingIntent, channel, notificationsManager)
        }

        fun startForegroundService(context: Context, intent: Intent) {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.startForegroundService(context, intent)
            } else if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                Api26Compatibility.startForegroundService(context, intent)
            } else {
                Api23Compatibility.startForegroundService(context, intent)
            }
        }

        fun startForegroundService(service: Service, notifId: Int, notif: Notification?) {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.startForegroundService(service, notifId, notif)
            } else {
                Api23Compatibility.startForegroundService(service, notifId, notif)
            }
        }

        /* Call */

        fun canDrawOverlay(context: Context): Boolean {
            return Api23Compatibility.canDrawOverlay(context)
        }

        fun isInPictureInPictureMode(activity: Activity): Boolean {
            if (Version.sdkAboveOrEqual(Version.API25_NOUGAT_71)) {
                return Api25Compatibility.isInPictureInPictureMode(activity)
            }
            return false
        }

        fun enterPipMode(activity: Activity, conference: Boolean) {
            if (Version.sdkStrictlyBelow(Version.API31_ANDROID_12) && Version.sdkAboveOrEqual(Version.API26_O_80)) {
                Api26Compatibility.enterPipMode(activity, conference)
            }
        }

        fun enableAutoEnterPiP(activity: Activity, enable: Boolean, conference: Boolean) {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.enableAutoEnterPiP(activity, enable, conference)
            }
        }

        fun getPipRatio(
            activity: Activity,
            forcePortrait: Boolean = false,
            forceLandscape: Boolean = false
        ): Rational {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            var height = displayMetrics.heightPixels
            var width = displayMetrics.widthPixels

            val aspectRatio = width / height
            if (aspectRatio < 1 / 2.39) {
                height = 2.39.toInt()
                width = 1
            } else if (aspectRatio > 2.39) {
                width = 2.39.toInt()
                height = 1
            }

            val ratio = if (width > height) {
                if (forcePortrait) {
                    Rational(height, width)
                } else {
                    Rational(width, height)
                }
            } else {
                if (forceLandscape) {
                    Rational(height, width)
                } else {
                    Rational(width, height)
                }
            }
            return ratio
        }

        fun eventVibration(vibrator: Vibrator) {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                Api26Compatibility.eventVibration(vibrator)
            } else {
                Api23Compatibility.eventVibration(vibrator)
            }
        }

        fun changeAudioRouteForTelecomManager(connection: NativeCallWrapper, route: Int): Boolean {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                return Api26Compatibility.changeAudioRouteForTelecomManager(connection, route)
            }
            return false
        }

        fun hideAndroidSystemUI(hide: Boolean, window: Window) {
            if (Version.sdkAboveOrEqual(Version.API30_ANDROID_11)) {
                Api30Compatibility.hideAndroidSystemUI(hide, window)
            } else {
                Api23Compatibility.hideAndroidSystemUI(hide, window)
            }
        }

        /* Chat */

        fun removeChatRoomShortcut(context: Context, chatRoom: ChatRoom) {
            if (Version.sdkAboveOrEqual(Version.API30_ANDROID_11)) {
                Api30Compatibility.removeChatRoomShortcut(context, chatRoom)
            }
        }

        fun extractLocusIdFromIntent(intent: Intent): String? {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.extractLocusIdFromIntent(intent)
            }
            return null
        }

        fun setLocusIdInContentCaptureSession(root: View, chatRoom: ChatRoom) {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.setLocusIdInContentCaptureSession(root, chatRoom)
            }
        }

        fun canChatMessageChannelBubble(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.canChatMessageChannelBubble(context)
            }
            return false
        }

        suspend fun addImageToMediaStore(context: Context, content: Content): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.addImageToMediaStore(context, content)
            }
            return Api23Compatibility.addImageToMediaStore(context, content)
        }

        suspend fun addVideoToMediaStore(context: Context, content: Content): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.addVideoToMediaStore(context, content)
            }
            return Api23Compatibility.addVideoToMediaStore(context, content)
        }

        suspend fun addAudioToMediaStore(context: Context, content: Content): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.addAudioToMediaStore(context, content)
            }
            return Api23Compatibility.addAudioToMediaStore(context, content)
        }

        fun getUpdateCurrentPendingIntentFlag(): Int {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                return Api31Compatibility.getUpdateCurrentPendingIntentFlag()
            }
            return Api23Compatibility.getUpdateCurrentPendingIntentFlag()
        }

        fun getImeFlagsForSecureChatRoom(): Int {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                return Api26Compatibility.getImeFlagsForSecureChatRoom()
            }
            return Api23Compatibility.getImeFlagsForSecureChatRoom()
        }

        fun hasTelecomManagerFeature(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                return Api33Compatibility.hasTelecomManagerFeature(context)
            } else if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                return Api26Compatibility.hasTelecomManagerFeature(context)
            }
            return false
        }
    }
}

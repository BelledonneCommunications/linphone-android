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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import org.linphone.core.ChatRoom
import org.linphone.core.Content
import org.linphone.mediastream.Version

@Suppress("DEPRECATION")
class Compatibility {
    companion object {
        fun hasPermission(context: Context, permission: String): Boolean {
            return when (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
                true -> Api23Compatibility.hasPermission(context, permission)
                else -> context.packageManager.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun getDeviceName(context: Context): String {
            return when (Version.sdkAboveOrEqual(Version.API25_NOUGAT_71)) {
                true -> Api25Compatibility.getDeviceName(context)
                else -> Api21Compatibility.getDeviceName(context)
            }
        }

        /* UI */

        fun setShowWhenLocked(activity: Activity, enable: Boolean) {
            if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
                Api21Compatibility.setShowWhenLocked(activity, enable)
            } else {
                Api27Compatibility.setShowWhenLocked(activity, enable)
            }
        }

        fun setTurnScreenOn(activity: Activity, enable: Boolean) {
            if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
                Api21Compatibility.setTurnScreenOn(activity, enable)
            } else {
                Api27Compatibility.setTurnScreenOn(activity, enable)
            }
        }

        fun requestDismissKeyguard(activity: Activity) {
            if (Version.sdkStrictlyBelow(Version.API27_OREO_81)) {
                Api21Compatibility.requestDismissKeyguard(activity)
            } else {
                Api27Compatibility.requestDismissKeyguard(activity)
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

        fun getOverlayType(): Int {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                return Api26Compatibility.getOverlayType()
            }
            return WindowManager.LayoutParams.TYPE_PHONE
        }

        /* Call */

        fun canDrawOverlay(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
                return Api23Compatibility.canDrawOverlay(context)
            }
            return false
        }

        fun enterPipMode(activity: Activity) {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                Api26Compatibility.enterPipMode(activity)
            }
        }

        fun eventVibration(vibrator: Vibrator) {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                Api26Compatibility.eventVibration(vibrator)
            } else {
                Api21Compatibility.eventVibration(vibrator)
            }
        }

        /* Contacts */

        fun createShortcutsToContacts(context: Context) {
            if (Version.sdkAboveOrEqual(Version.API25_NOUGAT_71)) {
                Api25Compatibility.createShortcutsToContacts(context)
            }
        }

        fun removeShortcuts(context: Context) {
            if (Version.sdkAboveOrEqual(Version.API25_NOUGAT_71)) {
                Api25Compatibility.removeShortcuts(context)
            }
        }

        /* Chat */

        fun createShortcutsToChatRooms(context: Context) {
            if (Version.sdkAboveOrEqual(Version.API25_NOUGAT_71)) {
                Api25Compatibility.createShortcutsToChatRooms(context)
            }
        }

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
            return Api21Compatibility.addImageToMediaStore(context, content)
        }

        suspend fun addVideoToMediaStore(context: Context, content: Content): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.addVideoToMediaStore(context, content)
            }
            return Api21Compatibility.addVideoToMediaStore(context, content)
        }

        suspend fun addAudioToMediaStore(context: Context, content: Content): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.addAudioToMediaStore(context, content)
            }
            return Api21Compatibility.addAudioToMediaStore(context, content)
        }
    }
}

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
package org.linphone.compatibility

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version

@SuppressLint("NewApi")
class Compatibility {
    companion object {
        private const val TAG = "[Compatibility]"

        const val FOREGROUND_SERVICE_TYPE_PHONE_CALL = 4 // ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        const val FOREGROUND_SERVICE_TYPE_CAMERA = 64 // ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        const val FOREGROUND_SERVICE_TYPE_MICROPHONE = 128 // ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1073741824 // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

        fun startServiceForeground(
            service: Service,
            id: Int,
            notification: Notification,
            foregroundServiceType: Int
        ) {
            if (Version.sdkAboveOrEqual(Version.API34_ANDROID_14_UPSIDE_DOWN_CAKE)) {
                Api34Compatibility.startServiceForeground(
                    service,
                    id,
                    notification,
                    foregroundServiceType
                )
            } else {
                Api28Compatibility.startServiceForeground(service, id, notification)
            }
        }

        fun setBlurRenderEffect(view: View) {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.setBlurRenderEffect(view)
            }
        }

        fun removeBlurRenderEffect(view: View) {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.removeBlurRenderEffect(view)
            }
        }

        fun getMediaCollectionUri(
            isImage: Boolean = false,
            isVideo: Boolean = false,
            isAudio: Boolean = false
        ): Uri {
            return if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                Api29Compatibility.getMediaCollectionUri(isImage, isVideo, isAudio)
            } else {
                Api28Compatibility.getMediaCollectionUri(isImage, isVideo, isAudio)
            }
        }

        fun getAllRequiredPermissionsArray(): Array<String> {
            if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                return Api33Compatibility.getAllRequiredPermissionsArray()
            }
            return arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        }

        fun hasFullScreenIntentPermission(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API34_ANDROID_14_UPSIDE_DOWN_CAKE)) {
                return Api34Compatibility.hasFullScreenIntentPermission(context)
            }
            return true
        }

        fun requestFullScreenIntentPermission(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API34_ANDROID_14_UPSIDE_DOWN_CAKE)) {
                Api34Compatibility.requestFullScreenIntentPermission(context)
                return true
            }
            return false
        }

        fun isPostNotificationsPermissionGranted(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API33_ANDROID_13_TIRAMISU)) {
                return Api33Compatibility.isPostNotificationsPermissionGranted(context)
            }
            return true
        }

        fun enterPipMode(activity: Activity): Boolean {
            if (Version.sdkStrictlyBelow(Version.API31_ANDROID_12)) {
                return Api28Compatibility.enterPipMode(activity)
            }
            return activity.isInPictureInPictureMode
        }

        fun enableAutoEnterPiP(activity: Activity, enable: Boolean) {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.enableAutoEnterPiP(activity, enable)
            }
        }

        fun forceDarkMode(context: Context) {
            Log.i("$TAG Forcing dark/night theme")
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.forceDarkMode(context)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        fun forceLightMode(context: Context) {
            Log.i("$TAG Forcing light/day theme")
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.forceLightMode(context)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        fun setAutoLightDarkMode(context: Context) {
            Log.i("$TAG Following Android's choice for light/dark theme")
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                Api31Compatibility.setAutoLightDarkMode(context)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        fun extractLocusIdFromIntent(intent: Intent): String? {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.extractLocusIdFromIntent(intent)
            }
            return null
        }

        fun setLocusIdInContentCaptureSession(root: View, conversationId: String) {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.setLocusIdInContentCaptureSession(
                    root,
                    conversationId
                )
            }
        }

        fun getRecordingsDirectory(): String {
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                return Api31Compatibility.getRecordingsDirectory()
            }
            return Environment.DIRECTORY_PODCASTS
        }

        fun setupAppStartupListener(context: Context) {
            if (Version.sdkAboveOrEqual(Version.API35_ANDROID_15_VANILLA_ICE_CREAM)) {
                Api35Compatibility.setupAppStartupListener(context)
            }
        }

        fun isIpAddress(string: String): Boolean {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                return Api29Compatibility.isIpAddress(string)
            }
            return Patterns.IP_ADDRESS.matcher(string).matches()
        }
    }
}

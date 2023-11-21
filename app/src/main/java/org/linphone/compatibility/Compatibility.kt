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

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.net.Uri
import android.view.View
import org.linphone.mediastream.Version

@SuppressLint("NewApi")
class Compatibility {
    companion object {
        private const val TAG = "[Compatibility]"

        const val FOREGROUND_SERVICE_TYPE_PHONE_CALL = 4 // Matches ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        const val FOREGROUND_SERVICE_TYPE_CAMERA = 64 // Matches ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        const val FOREGROUND_SERVICE_TYPE_MICROPHONE = 128 // ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

        fun startServiceForeground(
            service: Service,
            id: Int,
            notification: Notification,
            foregroundServiceType: Int
        ) {
            if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                Api29Compatibility.startServiceForeground(
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
    }
}

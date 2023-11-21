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

import android.app.Notification
import android.app.Service
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import org.linphone.core.tools.Log

@RequiresApi(Build.VERSION_CODES.Q)
class Api29Compatibility {
    companion object {
        private const val TAG = "[API 29 Compatibility]"

        fun startServiceForeground(
            service: Service,
            id: Int,
            notification: Notification,
            foregroundServiceType: Int
        ) {
            try {
                service.startForeground(
                    id,
                    notification,
                    foregroundServiceType
                )
            } catch (e: Exception) {
                Log.e("$TAG Can't start service as foreground! $e")
            }
        }

        fun getMediaCollectionUri(isImage: Boolean, isVideo: Boolean, isAudio: Boolean): Uri {
            return when {
                isImage -> {
                    MediaStore.Images.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                }
                isVideo -> {
                    MediaStore.Video.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                }
                isAudio -> {
                    MediaStore.Audio.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                }
                else -> Uri.EMPTY
            }
        }
    }
}

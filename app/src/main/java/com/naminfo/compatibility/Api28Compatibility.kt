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
package com.naminfo.compatibility

import android.app.Activity
import android.app.Notification
import android.app.PictureInPictureParams
import android.app.Service
import android.net.Uri
import android.provider.MediaStore
import org.linphone.core.tools.Log
import com.naminfo.utils.AppUtils

class Api28Compatibility {
    companion object {
        private const val TAG = "[API 28 Compatibility]"

        fun startServiceForeground(service: Service, id: Int, notification: Notification) {
            try {
                service.startForeground(
                    id,
                    notification
                )
            } catch (e: Exception) {
                Log.e("$TAG Can't start service as foreground! $e")
            }
        }

        fun enterPipMode(activity: Activity): Boolean {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(AppUtils.getPipRatio(activity))
                .build()
            try {
                if (!activity.enterPictureInPictureMode(params)) {
                    Log.e("$TAG Failed to enter PiP mode")
                } else {
                    Log.i("$TAG Entered PiP mode")
                    return true
                }
            } catch (e: Exception) {
                Log.e("$TAG Can't build PiP params: $e")
            }
            return false
        }

        fun getMediaCollectionUri(isImage: Boolean, isVideo: Boolean, isAudio: Boolean): Uri {
            return when {
                isImage -> {
                    MediaStore.Images.Media.getContentUri("external")
                }
                isVideo -> {
                    MediaStore.Video.Media.getContentUri("external")
                }
                isAudio -> {
                    MediaStore.Audio.Media.getContentUri("external")
                }
                else -> Uri.EMPTY
            }
        }
    }
}

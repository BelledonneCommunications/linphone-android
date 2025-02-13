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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.view.contentcapture.ContentCaptureContext
import android.view.contentcapture.ContentCaptureSession
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
class Api29Compatibility {
    companion object {
        private const val TAG = "[API 29 Compatibility]"

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

        fun extractLocusIdFromIntent(intent: Intent): String? {
            return intent.getStringExtra(Intent.EXTRA_LOCUS_ID)
        }

        fun setLocusIdInContentCaptureSession(root: View, conversationId: String) {
            val session: ContentCaptureSession? = root.contentCaptureSession
            if (session != null) {
                session.contentCaptureContext = ContentCaptureContext.forLocusId(conversationId)
            }
        }
    }
}

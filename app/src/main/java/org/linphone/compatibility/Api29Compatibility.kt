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
import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.contentcapture.ContentCaptureContext
import android.view.contentcapture.ContentCaptureSession
import androidx.core.app.NotificationManagerCompat
import org.linphone.R
import org.linphone.core.ChatRoom
import org.linphone.core.Content
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.Log

@TargetApi(29)
class Api29Compatibility {
    companion object {
        fun hasReadPhoneStatePermission(context: Context): Boolean {
            val granted = Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_STATE)
            if (granted) {
                Log.d("[Permission Helper] Permission READ_PHONE_STATE is granted")
            } else {
                Log.w("[Permission Helper] Permission READ_PHONE_STATE is denied")
            }
            return granted
        }

        fun hasTelecomManagerPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_STATE) &&
                Compatibility.hasPermission(context, Manifest.permission.MANAGE_OWN_CALLS)
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
            channel.setAllowBubbles(true)
            notificationManager.createNotificationChannel(channel)
        }

        fun extractLocusIdFromIntent(intent: Intent): String? {
            return intent.getStringExtra(Intent.EXTRA_LOCUS_ID)
        }

        fun setLocusIdInContentCaptureSession(root: View, chatRoom: ChatRoom) {
            val session: ContentCaptureSession? = root.contentCaptureSession
            if (session != null) {
                val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
                session.contentCaptureContext = ContentCaptureContext.forLocusId(id)
            }
        }

        fun canChatMessageChannelBubble(context: Context): Boolean {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val bubblesAllowed = notificationManager.areBubblesAllowed()
            Log.i(
                "[Notifications Manager] Bubbles notifications are ${if (bubblesAllowed) "allowed" else "forbidden"}"
            )
            return bubblesAllowed
        }

        fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
            return ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            )
        }

        suspend fun addImageToMediaStore(context: Context, content: Content): Boolean {
            return addContentToMediaStore(context, content, isImage = true)
        }

        suspend fun addVideoToMediaStore(context: Context, content: Content): Boolean {
            return addContentToMediaStore(context, content, isVideo = true)
        }

        suspend fun addAudioToMediaStore(context: Context, content: Content): Boolean {
            return addContentToMediaStore(context, content, isAudio = true)
        }

        private suspend fun addContentToMediaStore(
            context: Context,
            content: Content,
            isImage: Boolean = false,
            isVideo: Boolean = false,
            isAudio: Boolean = false
        ): Boolean {
            val isContentEncrypted = content.isFileEncrypted
            val filePath = if (content.isFileEncrypted) {
                val plainFilePath = content.exportPlainFile().orEmpty()
                Log.i(
                    "[Media Store] [VFS] Content is encrypted, plain file path is: $plainFilePath"
                )
                plainFilePath
            } else {
                content.filePath
            }

            if (filePath.isNullOrEmpty()) {
                Log.e("[Media Store] Content doesn't have a file path!")
                return false
            }

            val appName = AppUtils.getString(R.string.app_name)
            val directory = when {
                isImage -> Environment.DIRECTORY_PICTURES
                isVideo -> Environment.DIRECTORY_MOVIES
                isAudio -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }
            val relativePath = "$directory/$appName"
            val fileName = content.name
            val mime = "${content.type}/${content.subtype}"
            val type = when {
                isImage -> "image"
                isVideo -> "video"
                isAudio -> "audio"
                else -> "unexpected"
            }
            Log.i(
                "[Media Store] Adding $type [$filePath] to Media Store with name [$fileName] and MIME [$mime], asking to be stored in: $relativePath"
            )

            val mediaStoreFilePath = when {
                isImage -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Images.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                    addContentValuesToCollection(
                        context,
                        filePath,
                        collection,
                        values,
                        MediaStore.Images.Media.IS_PENDING
                    )
                }
                isVideo -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.TITLE, fileName)
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, mime)
                        put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Video.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                    addContentValuesToCollection(
                        context,
                        filePath,
                        collection,
                        values,
                        MediaStore.Video.Media.IS_PENDING
                    )
                }
                isAudio -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, fileName)
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.MIME_TYPE, mime)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Audio.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                    addContentValuesToCollection(
                        context,
                        filePath,
                        collection,
                        values,
                        MediaStore.Audio.Media.IS_PENDING
                    )
                }
                else -> ""
            }

            if (isContentEncrypted) {
                Log.w("[Media Store] Content was encrypted, delete plain version: $filePath")
                FileUtils.deleteFile(filePath)
            }

            if (mediaStoreFilePath.isNotEmpty()) {
                Log.i("[Media Store] Exported file path is: $mediaStoreFilePath")
                content.userData = mediaStoreFilePath
                return true
            }

            return false
        }

        private suspend fun addContentValuesToCollection(
            context: Context,
            filePath: String,
            collection: Uri,
            values: ContentValues,
            pendingKey: String
        ): String {
            try {
                val fileUri = context.contentResolver.insert(collection, values)
                if (fileUri == null) {
                    Log.e("[Media Store] Failed to get a URI to where store the file, aborting")
                    return ""
                }

                context.contentResolver.openOutputStream(fileUri).use { out ->
                    if (FileUtils.copyFileTo(filePath, out)) {
                        values.clear()
                        values.put(pendingKey, 0)
                        context.contentResolver.update(fileUri, values, null, null)

                        return fileUri.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("[Media Store] Exception: $e")
            }
            return ""
        }
    }
}

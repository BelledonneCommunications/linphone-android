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

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import org.linphone.R
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils

@TargetApi(21)
class Api21Compatibility {
    companion object {
        fun getDeviceName(context: Context): String {
            var name = BluetoothAdapter.getDefaultAdapter().name
            if (name == null) {
                name = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }
            if (name == null) {
                name = Build.MANUFACTURER + " " + Build.MODEL
            }
            return name
        }

        fun vibrate(vibrator: Vibrator) {
            val pattern = longArrayOf(0, 1000, 1000)
            vibrator.vibrate(pattern, 1)
        }

        fun addImageToMediaStore(context: Context, content: Content): Boolean {
            val filePath = content.filePath
            val appName = AppUtils.getString(R.string.app_name)
            val relativePath = "${Environment.DIRECTORY_PICTURES}/$appName"
            val fileName = content.name
            val mime = "${content.type}/${content.subtype}"
            Log.i("[Chat Message] Adding image $filePath to Media Store with name $fileName and MIME $mime, asking to be stored in $relativePath")

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
            }
            val collection = MediaStore.Images.Media.getContentUri("external")

            val fileUri = context.contentResolver.insert(collection, values)
            if (fileUri == null) {
                Log.e("[Chat Message] Failed to get a URI to where store the file, aborting")
                return false
            }

            var copyOk = false
            context.contentResolver.openOutputStream(fileUri).use { out ->
                copyOk = FileUtils.copyFileTo(filePath, out)
            }

            if (copyOk) {
                content.userData = fileUri.toString()
            }
            return copyOk
        }

        fun addVideoToMediaStore(context: Context, content: Content): Boolean {
            val filePath = content.filePath
            val appName = AppUtils.getString(R.string.app_name)
            val relativePath = "${Environment.DIRECTORY_MOVIES}/$appName"
            val fileName = content.name
            val mime = "${content.type}/${content.subtype}"
            Log.i("[Chat Message] Adding video $filePath to Media Store with name $fileName and MIME $mime, asking to be stored in $relativePath")

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.TITLE, fileName)
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
            }
            val collection = MediaStore.Video.Media.getContentUri("external")

            val fileUri = context.contentResolver.insert(collection, values)
            if (fileUri == null) {
                Log.e("[Chat Message] Failed to get a URI to where store the file, aborting")
                return false
            }

            var copyOk = false
            context.contentResolver.openOutputStream(fileUri).use { out ->
                copyOk = FileUtils.copyFileTo(filePath, out)
            }

            if (copyOk) {
                content.userData = fileUri.toString()
            }
            return copyOk
        }

        fun addAudioToMediaStore(context: Context, content: Content): Boolean {
            val filePath = content.filePath
            val appName = AppUtils.getString(R.string.app_name)
            val relativePath = "${Environment.DIRECTORY_MUSIC}/$appName"
            val fileName = content.name
            val mime = "${content.type}/${content.subtype}"
            Log.i("[Chat Message] Adding audio $filePath to Media Store with name $fileName and MIME $mime, asking to be stored in $relativePath")

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, fileName)
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
            }
            val collection = MediaStore.Audio.Media.getContentUri("external")

            val fileUri = context.contentResolver.insert(collection, values)
            if (fileUri == null) {
                Log.e("[Chat Message] Failed to get a URI to where store the file, aborting")
                return false
            }

            var copyOk = false
            context.contentResolver.openOutputStream(fileUri).use { out ->
                copyOk = FileUtils.copyFileTo(filePath, out)
            }

            if (copyOk) {
                content.userData = fileUri.toString()
            }
            return copyOk
        }
    }
}

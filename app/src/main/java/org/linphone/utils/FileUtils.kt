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
package org.linphone.utils

import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class FileUtils {
    companion object {
        const val TAG = "[File Utils]"

        fun getFileStoragePath(fileName: String, isImage: Boolean = false): File {
            val path = getFileStorageDir(isImage)
            var file = File(path, fileName)

            var prefix = 1
            while (file.exists()) {
                file = File(path, prefix.toString() + "_" + fileName)
                Log.w("$TAG File with that name already exists, renamed to ${file.name}")
                prefix += 1
            }
            return file
        }

        suspend fun copyFile(from: Uri, to: File): Boolean {
            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(to).use { outputStream ->
                        val inputStream = FileInputStream(
                            coreContext.context.contentResolver.openFileDescriptor(from, "r")?.fileDescriptor
                        )
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
                return true
            } catch (e: IOException) {
                Log.e("$TAG copyFile [$from] to [$to] exception: $e")
            }
            return false
        }

        private fun getFileStorageDir(isPicture: Boolean = false): File {
            var path: File? = null
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                Log.w("$TAG External storage is mounted")
                var directory = Environment.DIRECTORY_DOWNLOADS
                if (isPicture) {
                    Log.w("$TAG Using pictures directory instead of downloads")
                    directory = Environment.DIRECTORY_PICTURES
                }
                path = coreContext.context.getExternalFilesDir(directory)
            }

            val returnPath: File = path ?: coreContext.context.filesDir
            if (path == null) {
                Log.w(
                    "$TAG Couldn't get external storage path, using internal"
                )
            }

            return returnPath
        }
    }
}

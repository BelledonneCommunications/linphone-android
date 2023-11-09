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

import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import android.system.Os
import android.webkit.MimeTypeMap
import androidx.annotation.AnyThread
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class FileUtils {
    enum class MimeType {
        PlainText,
        Pdf,
        Image,
        Video,
        Audio,
        Unknown
    }

    companion object {
        private const val TAG = "[File Utils]"

        @AnyThread
        fun getProperFilePath(path: String): String {
            if (path.startsWith("file:") || path.startsWith("content:")) {
                return path
            } else if (path.startsWith("/")) {
                return "file:$path"
            }
            return "file:/$path"
        }

        @AnyThread
        fun getFileStoragePath(
            fileName: String,
            isImage: Boolean = false,
            overrideExisting: Boolean = false
        ): File {
            val path = getFileStorageDir(isImage)
            var file = File(path, fileName)

            if (!overrideExisting) {
                var prefix = 1
                while (file.exists()) {
                    file = File(path, prefix.toString() + "_" + fileName)
                    Log.w("$TAG File with that name already exists, renamed to ${file.name}")
                    prefix += 1
                }
            }
            return file
        }

        @AnyThread
        fun getFileStorageCacheDir(fileName: String, overrideExisting: Boolean = false): File {
            val path = coreContext.context.cacheDir
            Log.i("$TAG Cache directory is: $path")

            var file = File(path, fileName)
            if (!overrideExisting) {
                var prefix = 1
                while (file.exists()) {
                    file = File(path, prefix.toString() + "_" + fileName)
                    Log.w("$TAG File with that name already exists, renamed to ${file.name}")
                    prefix += 1
                }
            }
            return file
        }

        suspend fun getFilePath(context: Context, uri: Uri, overrideExisting: Boolean): String? {
            val name: String = getNameFromUri(uri, context)
            try {
                if (Os.fstat(
                        ParcelFileDescriptor.open(
                                File(uri.path),
                                ParcelFileDescriptor.MODE_READ_ONLY
                            ).fileDescriptor
                    ).st_uid != Process.myUid()
                ) {
                    Log.e("$TAG File descriptor UID different from our, denying copy!")
                    return null
                }
            } catch (e: Exception) {
                Log.e("$TAG Can't check file ownership: ", e)
            }

            val extension = getExtensionFromFileName(name)
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            val isImage = getMimeType(type) == MimeType.Image

            try {
                val localFile: File = getFileStoragePath(name, isImage, overrideExisting)
                copyFile(uri, localFile)
                return localFile.absolutePath
            } catch (e: Exception) {
                Log.e("$TAG Can't copy file in local storage: ", e)
            }

            return null
        }

        @AnyThread
        suspend fun copyFile(from: Uri, to: File): Boolean {
            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(to).use { outputStream ->
                        val fileDescriptor = coreContext.context.contentResolver.openFileDescriptor(
                            from,
                            "r"
                        )
                        val inputStream = FileInputStream(fileDescriptor?.fileDescriptor)
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        fileDescriptor?.close()
                    }
                }
                return true
            } catch (e: IOException) {
                Log.e("$TAG copyFile [$from] to [$to] exception: $e")
            }
            return false
        }

        suspend fun deleteFile(filePath: String) {
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (file.exists()) {
                    try {
                        if (file.delete()) {
                            Log.i("$TAG Deleted $filePath")
                        } else {
                            Log.e("$TAG Can't delete $filePath")
                        }
                    } catch (e: Exception) {
                        Log.e("$TAG Can't delete $filePath, exception: $e")
                    }
                } else {
                    Log.e("$TAG File $filePath doesn't exists")
                }
            }
        }

        @AnyThread
        suspend fun dumpStringToFile(data: String, to: File): Boolean {
            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(to).use { outputStream ->
                        val inputStream = data.byteInputStream()
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
                return true
            } catch (e: IOException) {
                Log.e("$TAG dumpStringToFile [$data] to [$to] exception: $e")
            }
            return false
        }

        suspend fun readFile(file: File): String {
            Log.i("$TAG Trying to read file [${file.absoluteFile}]")
            val stringBuilder = StringBuilder()
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { inputStream ->
                        val buffer = ByteArray(4096)
                        while (inputStream.read(buffer) >= 0) {
                            stringBuilder.append(String(buffer))
                        }
                    }
                }
                return stringBuilder.toString()
            } catch (e: IOException) {
                Log.e("$TAG Failed to read file [$file] as plain text: $e")
            }
            return stringBuilder.toString()
        }

        @AnyThread
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

        @AnyThread
        private fun getNameFromUri(uri: Uri, context: Context): String {
            var name = ""
            if (uri.scheme == "content") {
                val returnCursor =
                    context.contentResolver.query(uri, null, null, null, null)
                if (returnCursor != null) {
                    returnCursor.moveToFirst()
                    val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        try {
                            val displayName = returnCursor.getString(nameIndex)
                            if (displayName != null) {
                                name = displayName
                            } else {
                                Log.e(
                                    "$TAG Failed to get the display name for URI $uri, returned value is null"
                                )
                            }
                        } catch (e: CursorIndexOutOfBoundsException) {
                            Log.e(
                                "$TAG Failed to get the display name for URI $uri, exception is $e"
                            )
                        }
                    } else {
                        Log.e("$TAG Couldn't get DISPLAY_NAME column index for URI: $uri")
                    }
                    returnCursor.close()
                }
            } else if (uri.scheme == "file") {
                name = uri.lastPathSegment ?: ""
            }
            return name
        }

        @AnyThread
        fun isExtensionVideo(path: String): Boolean {
            val extension = getExtensionFromFileName(path)
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return getMimeType(type) == MimeType.Video
        }

        @AnyThread
        private fun getExtensionFromFileName(fileName: String): String {
            var extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
            if (extension.isNullOrEmpty()) {
                val i = fileName.lastIndexOf('.')
                if (i > 0) {
                    extension = fileName.substring(i + 1)
                }
            }

            return extension.lowercase(Locale.getDefault())
        }

        @AnyThread
        private fun getMimeType(type: String?): MimeType {
            if (type.isNullOrEmpty()) return MimeType.Unknown
            return when {
                type.startsWith("image/") -> MimeType.Image
                type.startsWith("text/plain") -> MimeType.PlainText
                type.startsWith("video/") -> MimeType.Video
                type.startsWith("audio/") -> MimeType.Audio
                type.startsWith("application/pdf") -> MimeType.Pdf
                else -> MimeType.Unknown
            }
        }
    }
}

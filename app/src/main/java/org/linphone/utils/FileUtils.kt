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

import android.content.ContentValues
import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.Os
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
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
        fun bytesToDisplayableSize(bytes: Long): String {
            return Formatter.formatShortFileSize(coreContext.context, bytes)
        }

        @AnyThread
        fun isExtensionImage(path: String): Boolean {
            val extension = getExtensionFromFileName(path)
            val type = getMimeTypeFromExtension(extension)
            return getMimeType(type) == MimeType.Image
        }

        @AnyThread
        fun isExtensionVideo(path: String): Boolean {
            val extension = getExtensionFromFileName(path)
            val type = getMimeTypeFromExtension(extension)
            return getMimeType(type) == MimeType.Video
        }

        @AnyThread
        fun isExtensionAudio(path: String): Boolean {
            val extension = getExtensionFromFileName(path)
            val type = getMimeTypeFromExtension(extension)
            return getMimeType(type) == MimeType.Audio
        }

        @AnyThread
        fun getExtensionFromFileName(fileName: String): String {
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
        fun getMimeTypeFromExtension(extension: String): String {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "file/$extension"
        }

        @AnyThread
        fun getMimeType(type: String?): MimeType {
            if (type.isNullOrEmpty()) return MimeType.Unknown
            val mime = when {
                type.startsWith("image/") -> MimeType.Image
                type.startsWith("text/") -> MimeType.PlainText
                type.endsWith("/log") -> MimeType.PlainText
                type.startsWith("video/") -> MimeType.Video
                type.startsWith("audio/") -> MimeType.Audio
                type.startsWith("application/pdf") -> MimeType.Pdf
                type.startsWith("application/json") -> MimeType.PlainText
                else -> MimeType.Unknown
            }
            Log.d("$TAG MIME type for [$type] is [$mime]")
            return mime
        }

        @AnyThread
        fun getNameFromFilePath(filePath: String): String {
            var name = filePath
            val i = filePath.lastIndexOf('/')
            if (i > 0) {
                name = filePath.substring(i + 1)
            }
            return name
        }

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
            isRecording: Boolean = false,
            overrideExisting: Boolean = false
        ): File {
            val path = getFileStorageDir(isPicture = isImage, isRecording = isRecording)
            var file = File(path, fileName)

            if (!overrideExisting) {
                var prefix = 1
                while (file.exists()) {
                    file = File(path, prefix.toString() + "_" + fileName)
                    Log.w("$TAG File with that name already exists, renamed to [${file.name}]")
                    prefix += 1
                }
            }
            return file
        }

        @AnyThread
        fun getFileStorageCacheDir(fileName: String, overrideExisting: Boolean = false): File {
            val path = coreContext.context.cacheDir

            var file = File(path, fileName)
            if (!overrideExisting) {
                var prefix = 1
                while (file.exists()) {
                    file = File(path, prefix.toString() + "_" + fileName)
                    Log.w("$TAG File with that name already exists, renamed to [${file.name}]")
                    prefix += 1
                }
            }
            return file
        }

        fun getPublicFilePath(context: Context, path: String): Uri {
            val contentUri: Uri
            when {
                path.startsWith("file://") -> {
                    val file = File(path.substring("file://".length))
                    contentUri = FileProvider.getUriForFile(
                        context,
                        context.getString(R.string.file_provider),
                        file
                    )
                }
                path.startsWith("content://") -> {
                    contentUri = Uri.parse(path)
                }
                else -> {
                    val file = File(path)
                    contentUri = try {
                        FileProvider.getUriForFile(
                            context,
                            context.getString(R.string.file_provider),
                            file
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "$TAG Couldn't get URI for file [$file] using file provider ${context.getString(
                                R.string.file_provider
                            )}: $e"
                        )
                        Uri.parse(path)
                    }
                }
            }
            return contentUri
        }

        suspend fun getFilePath(
            context: Context,
            uri: Uri,
            overrideExisting: Boolean,
            copyToCache: Boolean = false
        ): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val path = uri.path
                    if (path.isNullOrEmpty()) return@withContext null
                    if (Os.fstat(
                            ParcelFileDescriptor.open(
                                    File(path),
                                    ParcelFileDescriptor.MODE_READ_ONLY
                                ).fileDescriptor
                        ).st_uid != Process.myUid()
                    ) {
                        Log.e("$TAG File descriptor UID different from our, denying copy!")
                        return@withContext null
                    }
                } catch (e: Exception) {
                    Log.e("$TAG Can't check file ownership: ", e)
                }

                val name: String = getNameFromUri(uri, context)
                val extension = getExtensionFromFileName(name)
                val type = getMimeTypeFromExtension(extension)
                val isImage = getMimeType(type) == MimeType.Image

                try {
                    val localFile: File = if (copyToCache) {
                        getFileStorageCacheDir(name, overrideExisting)
                    } else {
                        getFileStoragePath(
                            name,
                            isImage = isImage,
                            overrideExisting = overrideExisting
                        )
                    }
                    copyFile(uri, localFile)
                    return@withContext localFile.absolutePath
                } catch (e: Exception) {
                    Log.e("$TAG Can't copy file in local storage: ", e)
                }

                return@withContext null
            }
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

        @AnyThread
        fun copyFile(from: Uri, to: Uri): Boolean {
            try {
                coreContext.context.contentResolver.openFileDescriptor(to, "w")?.use { fd ->
                    FileOutputStream(fd.fileDescriptor).use { outputStream ->
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
            } catch (e: FileNotFoundException) {
                Log.e("$TAG Failed to find dest file: $e")
            } catch (e: IOException) {
                Log.e("$TAG Error copying file: $e")
            }
            return false
        }

        private suspend fun copyFileTo(filePath: String, outputStream: OutputStream?): Boolean {
            if (outputStream == null) {
                Log.e("$TAG Can't copy file [$filePath] to given null output stream")
                return false
            }

            val file = File(filePath)
            if (!file.exists()) {
                Log.e("$TAG Can't copy file [$filePath], it doesn't exists")
                return false
            }

            try {
                withContext(Dispatchers.IO) {
                    val inputStream = FileInputStream(file)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                return true
            } catch (e: IOException) {
                Log.e("$TAG copyFileTo exception: $e")
            }
            return false
        }

        fun renameFile(from: String, to: String): String {
            val source = File(from)
            val isImage = isExtensionImage(to)
            val dest = getFileStoragePath(to, isImage = isImage, overrideExisting = true)

            if (!source.exists()) {
                if (dest.exists()) {
                    Log.w(
                        "$TAG Source file doesn't exists but destination does, considering renaming done"
                    )
                    return dest.absolutePath
                }
                Log.e("$TAG Can't rename file [${source.absoluteFile}], it doesn't exists!")
                return ""
            }

            val destination = if (dest.exists()) {
                getFileStoragePath(to, isImage = isImage, overrideExisting = false)
            } else {
                dest
            }
            if (source.renameTo(destination)) {
                return destination.absolutePath
            }

            Log.e(
                "$TAG Failed to rename file [${source.absoluteFile}] to [${destination.absoluteFile}]"
            )
            return ""
        }

        suspend fun deleteFile(filePath: String) {
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (file.exists()) {
                    try {
                        if (file.delete()) {
                            Log.i("$TAG Deleted [$filePath]")
                        } else {
                            Log.e("$TAG Can't delete [$filePath]")
                        }
                    } catch (e: Exception) {
                        Log.e("$TAG Can't delete [$filePath], exception: $e")
                    }
                } else {
                    Log.e("$TAG File [$filePath] doesn't exists")
                }
            }
        }

        fun doesFileExist(path: String): Boolean {
            val file = File(path)
            return file.exists()
        }

        @AnyThread
        fun storeBitmap(bitmap: Bitmap, fileName: String): String {
            val path = getFileStorageCacheDir("$fileName.jpg", true)
            FileOutputStream(path).use { outputStream ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    100,
                    outputStream
                )
            }
            return path.absolutePath
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
        suspend fun clearExistingPlainFiles(path: String) {
            val dir = File(path)
            if (dir.exists()) {
                for (file in dir.listFiles().orEmpty()) {
                    Log.w(
                        "$TAG [VFS] Found forgotten plain file [${file.path}], deleting it now"
                    )
                    deleteFile(file.path)
                }
            }
        }

        @AnyThread
        fun countFilesInDirectory(path: String): Int {
            val dir = File(path)
            if (dir.exists()) {
                return dir.listFiles().orEmpty().size
            }
            return -1
        }

        @AnyThread
        fun getFileStorageDir(isPicture: Boolean = false, isRecording: Boolean = false): File {
            var path: File? = null
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                Log.w("$TAG External storage is mounted")
                var directory = Environment.DIRECTORY_DOWNLOADS
                if (isPicture) {
                    Log.w("$TAG Using pictures directory instead of downloads")
                    directory = Environment.DIRECTORY_PICTURES
                } else if (isRecording) {
                    directory = Compatibility.getRecordingsDirectory()
                    Log.w("$TAG Using [$directory] directory instead of downloads")
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

        suspend fun addContentToMediaStore(path: String): String {
            if (path.isEmpty()) {
                Log.e("$TAG No file path to export to MediaStore!")
                return ""
            }

            val isImage = isExtensionImage(path)
            val isVideo = isExtensionVideo(path)
            val isAudio = isExtensionAudio(path)

            val directory = when {
                isImage -> Environment.DIRECTORY_PICTURES
                isVideo -> Environment.DIRECTORY_MOVIES
                isAudio -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val appName = AppUtils.getString(R.string.app_name)
            val relativePath = "$directory/$appName"
            val fileName = getNameFromFilePath(path)
            val extension = getExtensionFromFileName(fileName)
            val mime = getMimeTypeFromExtension(extension)

            val context = coreContext.context
            val mediaStoreFilePath = when {
                isImage -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val collection = Compatibility.getMediaCollectionUri(isImage = true)
                    addContentValuesToCollection(
                        context,
                        path,
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
                    val collection = Compatibility.getMediaCollectionUri(isVideo = true)
                    addContentValuesToCollection(
                        context,
                        path,
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
                    val collection = Compatibility.getMediaCollectionUri(isAudio = true)
                    addContentValuesToCollection(
                        context,
                        path,
                        collection,
                        values,
                        MediaStore.Audio.Media.IS_PENDING
                    )
                }
                else -> ""
            }

            if (mediaStoreFilePath.isNotEmpty()) {
                Log.i("$TAG Exported file path to MediaStore is: $mediaStoreFilePath")
                return mediaStoreFilePath
            }

            return ""
        }

        @UiThread
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
                    Log.e("$TAG Failed to get a URI to where store the file, aborting")
                    return ""
                }

                context.contentResolver.openOutputStream(fileUri).use { out ->
                    if (copyFileTo(filePath, out)) {
                        values.clear()
                        values.put(pendingKey, 0)
                        context.contentResolver.update(fileUri, values, null, null)

                        return fileUri.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("$TAG Exception: $e")
            }
            return ""
        }
    }
}

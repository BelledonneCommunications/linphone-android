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
package org.linphone.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.CursorIndexOutOfBoundsException
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log

class FileUtils {
    companion object {
        const val VFS_PLAIN_FILE_EXTENSION = ".bctbx_evfs_plain"

        fun getNameFromFilePath(filePath: String): String {
            var name = filePath
            val i = filePath.lastIndexOf('/')
            if (i > 0) {
                name = filePath.substring(i + 1)
            }
            return name
        }

        fun getExtensionFromFileName(fileName: String): String {
            val realFileName = if (fileName.endsWith(VFS_PLAIN_FILE_EXTENSION)) {
                fileName.substring(0, fileName.length - VFS_PLAIN_FILE_EXTENSION.length)
            } else fileName

            var extension = MimeTypeMap.getFileExtensionFromUrl(realFileName)
            if (extension.isNullOrEmpty()) {
                val i = realFileName.lastIndexOf('.')
                if (i > 0) {
                    extension = realFileName.substring(i + 1)
                }
            }

            return extension
        }

        fun isPlainTextFile(path: String): Boolean {
            val extension = getExtensionFromFileName(path).lowercase(Locale.getDefault())
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return type?.startsWith("text/plain") ?: false
        }

        fun isExtensionPdf(path: String): Boolean {
            val extension = getExtensionFromFileName(path).lowercase(Locale.getDefault())
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return type?.startsWith("application/pdf") ?: false
        }

        fun isExtensionImage(path: String): Boolean {
            val extension = getExtensionFromFileName(path).lowercase(Locale.getDefault())
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return type?.startsWith("image/") ?: false
        }

        fun isExtensionVideo(path: String): Boolean {
            val extension = getExtensionFromFileName(path).lowercase(Locale.getDefault())
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return type?.startsWith("video/") ?: false
        }

        fun isExtensionAudio(path: String): Boolean {
            val extension = getExtensionFromFileName(path).lowercase(Locale.getDefault())
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return type?.startsWith("audio/") ?: false
        }

        fun clearExistingPlainFiles() {
            for (file in coreContext.context.filesDir.listFiles().orEmpty()) {
                if (file.path.endsWith(VFS_PLAIN_FILE_EXTENSION)) {
                    Log.w("[File Utils] Found forgotten plain file: ${file.path}, deleting it")
                    deleteFile(file.path)
                }
            }
            for (file in coreContext.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.listFiles().orEmpty()) {
                if (file.path.endsWith(VFS_PLAIN_FILE_EXTENSION)) {
                    Log.w("[File Utils] Found forgotten plain file: ${file.path}, deleting it")
                    deleteFile(file.path)
                }
            }
            for (file in coreContext.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles().orEmpty()) {
                if (file.path.endsWith(VFS_PLAIN_FILE_EXTENSION)) {
                    Log.w("[File Utils] Found forgotten plain file: ${file.path}, deleting it")
                    deleteFile(file.path)
                }
            }
        }

        fun getFileStorageDir(isPicture: Boolean = false): File {
            var path: File? = null
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                Log.w("[File Utils] External storage is mounted")
                var directory = Environment.DIRECTORY_DOWNLOADS
                if (isPicture) {
                    Log.w("[File Utils] Using pictures directory instead of downloads")
                    directory = Environment.DIRECTORY_PICTURES
                }
                path = coreContext.context.getExternalFilesDir(directory)
            }

            val returnPath: File = path ?: coreContext.context.filesDir
            if (path == null) Log.w("[File Utils] Couldn't get external storage path, using internal")

            return returnPath
        }

        private fun getFileStorageCacheDir(fileName: String): File {
            val path = coreContext.context.cacheDir
            Log.i("[File Utils] Cache directory is: $path")

            val realFileName = if (fileName.endsWith(VFS_PLAIN_FILE_EXTENSION)) {
                fileName.substring(0, fileName.length - VFS_PLAIN_FILE_EXTENSION.length)
            } else fileName
            var file = File(path, realFileName)

            var prefix = 1
            while (file.exists()) {
                file = File(path, prefix.toString() + "_" + realFileName)
                Log.w("[File Utils] File with that name already exists, renamed to ${file.name}")
                prefix += 1
            }
            return file
        }

        fun getFileStoragePath(fileName: String): File {
            val path = getFileStorageDir(isExtensionImage(fileName))
            var file = File(path, fileName)

            var prefix = 1
            while (file.exists()) {
                file = File(path, prefix.toString() + "_" + fileName)
                Log.w("[File Utils] File with that name already exists, renamed to ${file.name}")
                prefix += 1
            }
            return file
        }

        suspend fun getFilesPathFromPickerIntent(data: Intent?, temporaryImageFilePath: File?): List<String> {
            var filePath: String? = null
            if (data != null) {
                val clipData = data.clipData
                if (clipData != null && clipData.itemCount > 1) { // Multiple selection
                    Log.i("[File Utils] Found ${clipData.itemCount} elements")
                    val list = arrayListOf<String>()
                    for (i in 0 until clipData.itemCount) {
                        val dataUri = clipData.getItemAt(i).uri
                        if (dataUri != null) {
                            filePath = dataUri.toString()
                            Log.i("[File Utils] Using data URI $filePath")
                        }
                        filePath = cleanFilePath(filePath)
                        if (filePath != null) list.add(filePath)
                    }
                    return list
                } else { // Single selection
                    val dataUri = if (clipData != null && clipData.itemCount == 1) {
                        clipData.getItemAt(0).uri
                    } else {
                        data.data
                    }
                    if (dataUri != null) {
                        filePath = dataUri.toString()
                        Log.i("[File Utils] Using data URI $filePath")
                    } else if (temporaryImageFilePath?.exists() == true) {
                        filePath = temporaryImageFilePath.absolutePath
                        Log.i("[File Utils] Data URI is null, using $filePath")
                    }
                    filePath = cleanFilePath(filePath)
                    if (filePath != null) return arrayListOf(filePath)
                }
            } else if (temporaryImageFilePath?.exists() == true) {
                filePath = temporaryImageFilePath.absolutePath
                Log.i("[File Utils] Data is null, using $filePath")
                filePath = cleanFilePath(filePath)
                if (filePath != null) return arrayListOf(filePath)
            }
            return arrayListOf()
        }

        suspend fun getFilePathFromPickerIntent(data: Intent?, temporaryImageFilePath: File?): String? {
            var filePath: String? = null
            if (data != null) {
                val clipData = data.clipData
                if (clipData != null && clipData.itemCount > 1) { // Multiple selection
                    Log.e("[File Utils] Expecting only one file, got ${clipData.itemCount}")
                } else { // Single selection
                    val dataUri = if (clipData != null && clipData.itemCount == 1) {
                        clipData.getItemAt(0).uri
                    } else {
                        data.data
                    }
                    if (dataUri != null) {
                        filePath = dataUri.toString()
                        Log.i("[File Utils] Using data URI $filePath")
                    } else if (temporaryImageFilePath?.exists() == true) {
                        filePath = temporaryImageFilePath.absolutePath
                        Log.i("[File Utils] Data URI is null, using $filePath")
                    }
                }
            } else if (temporaryImageFilePath?.exists() == true) {
                filePath = temporaryImageFilePath.absolutePath
                Log.i("[File Utils] Data is null, using $filePath")
            }
            return cleanFilePath(filePath)
        }

        private suspend fun cleanFilePath(filePath: String?): String? {
            if (filePath != null) {
                val uriToParse = Uri.parse(filePath)
                if (filePath.startsWith("content://com.android.contacts/contacts/lookup/")) {
                    Log.i("[File Utils] Contact sharing URI detected")
                    return ContactUtils.getContactVcardFilePath(uriToParse)
                } else if (filePath.startsWith("content://") ||
                    filePath.startsWith("file://")
                ) {
                    val result = getFilePath(coreContext.context, uriToParse)
                    Log.i("[File Utils] Path was using a content or file scheme, real path is: $result")
                    if (result == null) {
                        Log.e("[File Utils] Failed to get access to file $uriToParse")
                    }
                    return result
                }
            }
            return filePath
        }

        fun deleteFile(filePath: String) {
            val file = File(filePath)
            if (file.exists()) {
                try {
                    if (file.delete()) {
                        Log.i("[File Utils] Deleted $filePath")
                    } else {
                        Log.e("[File Utils] Can't delete $filePath")
                    }
                } catch (e: Exception) {
                    Log.e("[File Utils] Can't delete $filePath, exception: $e")
                }
            } else {
                Log.e("[File Utils] File $filePath doesn't exists")
            }
        }

        suspend fun getFilePath(context: Context, uri: Uri): String? {
            var result: String? = null
            val name: String = getNameFromUri(uri, context)

            try {
                val localFile: File = createFile(name)
                val remoteFile =
                    context.contentResolver.openInputStream(uri)
                Log.i(
                    "[File Utils] Trying to copy file from " +
                        uri.toString() +
                        " to local file " +
                        localFile.absolutePath
                )
                coroutineScope {
                    val deferred = async { copyToFile(remoteFile, localFile) }
                    if (deferred.await()) {
                        Log.i("[File Utils] Copy successful")
                        result = localFile.absolutePath
                    } else {
                        Log.e("[File Utils] Copy failed")
                    }
                    remoteFile?.close()
                }
            } catch (e: IOException) {
                Log.e("[File Utils] getFilePath exception: ", e)
            }

            return result
        }

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
                                Log.e("[File Utils] Failed to get the display name for URI $uri, returned value is null")
                            }
                        } catch (e: CursorIndexOutOfBoundsException) {
                            Log.e("[File Utils] Failed to get the display name for URI $uri, exception is $e")
                        }
                    } else {
                        Log.e("[File Utils] Couldn't get DISPLAY_NAME column index for URI: $uri")
                    }
                    returnCursor.close()
                }
            } else if (uri.scheme == "file") {
                name = uri.lastPathSegment ?: ""
            }
            return name
        }

        suspend fun copyFileTo(filePath: String, outputStream: OutputStream?): Boolean {
            if (outputStream == null) {
                Log.e("[File Utils] Can't copy file $filePath to given null output stream")
                return false
            }

            val file = File(filePath)
            if (!file.exists()) {
                Log.e("[File Utils] Can't copy file $filePath, it doesn't exists")
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
                Log.e("[File Utils] copyFileTo exception: $e")
            }
            return false
        }

        private suspend fun copyToFile(inputStream: InputStream?, destFile: File?): Boolean {
            if (inputStream == null || destFile == null) return false
            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(destFile).use { out ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                            out.write(buffer, 0, bytesRead)
                        }
                    }
                }
                return true
            } catch (e: IOException) {
                Log.e("[File Utils] copyToFile exception: $e")
            }
            return false
        }

        suspend fun copyFileToCache(plainFilePath: String): String? {
            val cacheFile = getFileStorageCacheDir(getNameFromFilePath(plainFilePath))
            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(cacheFile).use { out ->
                        copyFileTo(plainFilePath, out)
                    }
                }
                return cacheFile.absolutePath
            } catch (e: IOException) {
                Log.e("[File Utils] copyFileToCache exception: $e")
            }
            return null
        }

        private fun createFile(file: String): File {
            var fileName = file

            if (fileName.isEmpty()) fileName = getStartDate()
            if (!fileName.contains(".")) {
                fileName = "$fileName.unknown"
            }

            return getFileStoragePath(fileName)
        }

        private fun getStartDate(): String {
            return try {
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            } catch (e: RuntimeException) {
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            }
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
                            "[Chat Message] Couldn't get URI for file $file using file provider ${context.getString(
                                R.string.file_provider
                            )}"
                        )
                        Uri.parse(path)
                    }
                }
            }
            return contentUri
        }

        fun openFileInThirdPartyApp(
            activity: Activity,
            contentFilePath: String,
            newTask: Boolean = false
        ): Boolean {
            val intent = Intent(Intent.ACTION_VIEW)
            val contentUri: Uri = getPublicFilePath(activity, contentFilePath)
            val filePath: String = contentUri.toString()
            Log.i("[File Viewer] Trying to open file: $filePath")
            var type: String? = null
            val extension = getExtensionFromFileName(filePath)

            if (extension.isNotEmpty()) {
                Log.i("[File Viewer] Found extension $extension")
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            } else {
                Log.e("[File Viewer] Couldn't find extension")
            }

            if (type != null) {
                Log.i("[File Viewer] Found matching MIME type $type")
            } else {
                type = if (extension == "linphonerc") {
                    "text/plain"
                } else {
                    "file/$extension"
                }
                Log.w("[File Viewer] Can't get MIME type from extension: $extension, will use $type")
            }

            intent.setDataAndType(contentUri, type)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (newTask) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                activity.startActivity(intent)
                return true
            } catch (anfe: ActivityNotFoundException) {
                Log.e("[File Viewer] Can't open file in third party app: $anfe")
            }
            return false
        }

        fun openMediaStoreFile(
            activity: Activity,
            contentFilePath: String,
            newTask: Boolean = false
        ): Boolean {
            val intent = Intent(Intent.ACTION_VIEW)
            val contentUri: Uri = Uri.parse(contentFilePath)
            intent.data = contentUri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (newTask) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                activity.startActivity(intent)
                return true
            } catch (anfe: ActivityNotFoundException) {
                Log.e("[File Viewer] Can't open media store export in third party app: $anfe")
            }
            return false
        }

        fun writeIntoFile(bytes: ByteArray, file: File) {
            val inStream = ByteArrayInputStream(bytes)
            val outStream = FileOutputStream(file)

            val buffer = ByteArray(1024)
            var read: Int
            while (inStream.read(buffer).also { read = it } != -1) {
                outStream.write(buffer, 0, read)
            }

            inStream.close()
            outStream.flush()
            outStream.close()
        }
    }
}

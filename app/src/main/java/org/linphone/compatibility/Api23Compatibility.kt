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
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import org.linphone.R
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class Api23Compatibility {
    companion object {
        fun hasPermission(context: Context, permission: String): Boolean {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

        fun requestReadPhoneStatePermission(fragment: Fragment, code: Int) {
            fragment.requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), code)
        }

        fun canDrawOverlay(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
            return MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        @SuppressLint("MissingPermission")
        fun eventVibration(vibrator: Vibrator) {
            val pattern = longArrayOf(0, 100, 100)
            vibrator.vibrate(pattern, -1)
        }

        @SuppressLint("MissingPermission")
        fun getDeviceName(context: Context): String {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            var name = adapter?.name
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

        fun setShowWhenLocked(activity: Activity, enable: Boolean) {
            if (enable) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }

        fun setTurnScreenOn(activity: Activity, enable: Boolean) {
            if (enable) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }

        fun requestDismissKeyguard(activity: Activity) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        fun startForegroundService(context: Context, intent: Intent) {
            context.startService(intent)
        }

        fun startForegroundService(service: Service, notifId: Int, notif: Notification?) {
            service.startForeground(notifId, notif)
        }

        fun hideAndroidSystemUI(hide: Boolean, window: Window) {
            val decorView = window.decorView
            val uiOptions = if (hide) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
            decorView.systemUiVisibility = uiOptions

            if (hide) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            }
        }

        fun getUpdateCurrentPendingIntentFlag(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT
        }

        fun getImeFlagsForSecureChatRoom(): Int {
            return EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
            if (!PermissionHelper.get().hasWriteExternalStoragePermission()) {
                Log.e("[Media Store] Write external storage permission denied")
                return false
            }

            val isContentEncrypted = content.isFileEncrypted
            val filePath = if (content.isFileEncrypted) {
                val plainFilePath = content.exportPlainFile().orEmpty()
                Log.w("[Media Store] Content is encrypted, plain file path is: $plainFilePath")
                plainFilePath
            } else content.filePath

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
            Log.i("[Media Store] Adding $type [$filePath] to Media Store with name [$fileName] and MIME [$mime], asking to be stored in: $relativePath")

            val mediaStoreFilePath = when {
                isImage -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                    }
                    val collection = MediaStore.Images.Media.getContentUri("external")
                    addContentValuesToCollection(context, filePath, collection, values)
                }
                isVideo -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.TITLE, fileName)
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, mime)
                    }
                    val collection = MediaStore.Video.Media.getContentUri("external")
                    addContentValuesToCollection(context, filePath, collection, values)
                }
                isAudio -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, fileName)
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    }
                    val collection = MediaStore.Audio.Media.getContentUri("external")
                    addContentValuesToCollection(context, filePath, collection, values)
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
            values: ContentValues
        ): String {
            try {
                val fileUri = context.contentResolver.insert(collection, values)
                if (fileUri == null) {
                    Log.e("[Media Store] Failed to get a URI to where store the file, aborting")
                    return ""
                }

                context.contentResolver.openOutputStream(fileUri).use { out ->
                    if (FileUtils.copyFileTo(filePath, out)) {
                        return fileUri.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("[Media Store] Exception: $e")
            }
            return ""
        }

        fun requestReadExternalStorageAndCameraPermissions(fragment: Fragment, code: Int) {
            fragment.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                ),
                code
            )
        }

        fun hasReadExternalStoragePermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.core

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.FileTransferService
import org.linphone.ui.main.MainActivity

@MainThread
class CoreFileTransferService : FileTransferService() {
    companion object {
        private const val TAG = "[Core File Transfer Service]"
    }

    var builder = NotificationCompat.Builder(this, SERVICE_NOTIFICATION_CHANNEL_ID)

    var listenerAdded = false

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onRemainingNumberOfFileTransferChanged(
            core: Core,
            downloadCount: Int,
            uploadCount: Int
        ) {
            updateNotificationContent(downloadCount, uploadCount)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!listenerAdded && coreContext.isCoreAvailable()) {
            coreContext.core.addListener(coreListener)
            listenerAdded = true
        }
        Log.i("$TAG Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("$TAG onStartCommand")
        if (!listenerAdded && coreContext.isCoreAvailable()) {
            coreContext.core.addListener(coreListener)
            listenerAdded = true
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i("$TAG Task removed, doing nothing")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i("$TAG onDestroy")
        coreContext.core.removeListener(coreListener)
        listenerAdded = false
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun createServiceNotification() {
        Log.i("$TAG Creating notification")

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mServiceNotification = builder.setContentTitle(
            getString(R.string.notification_file_transfer_title)
        )
            .setContentText(getString(R.string.notification_file_transfer_startup_message))
            .setSmallIcon(R.drawable.linphone_notification)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setContentIntent(pendingIntent)
            .build()
        postNotification()

        coreContext.postOnCoreThread { core ->
            val downloadingFilesCount = core.remainingDownloadFileCount
            val uploadingFilesCount = core.remainingUploadFileCount
            updateNotificationContent(downloadingFilesCount, uploadingFilesCount)
        }
    }

    @WorkerThread
    private fun updateNotificationContent(downloadingFilesCount: Int, uploadingFilesCount: Int) {
        Log.i(
            "$TAG [$downloadingFilesCount] file(s) being downloaded, [$uploadingFilesCount] file(s) being uploaded"
        )
        if (downloadingFilesCount == 0 && uploadingFilesCount == 0) {
            Log.i("$TAG No more files being transferred, do not alter the notification")
            return
        }

        val downloadText = resources.getQuantityString(
            R.plurals.notification_file_transfer_download,
            downloadingFilesCount,
            "$downloadingFilesCount"
        )
        val uploadText = resources.getQuantityString(
            R.plurals.notification_file_transfer_upload,
            uploadingFilesCount,
            "$uploadingFilesCount"
        )

        val message = if (downloadingFilesCount > 0 && uploadingFilesCount > 0) {
            getString(
                R.string.notification_file_transfer_upload_download_message,
                downloadText,
                uploadText
            )
        } else if (downloadingFilesCount > 0) {
            downloadText
        } else {
            uploadText
        }

        mServiceNotification = builder.setContentText(message).build()
        postNotification()
    }

    @AnyThread
    private fun postNotification() {
        val notificationsManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (mServiceNotification != null) {
                Log.i("$TAG Sending notification to manager")
                notificationsManager.notify(SERVICE_NOTIF_ID, mServiceNotification)
            } else {
                Log.e("$TAG Notification content hasn't been computed yet!")
            }
        } else {
            Log.e("$TAG POST_NOTIFICATIONS permission wasn't granted!")
        }
    }
}

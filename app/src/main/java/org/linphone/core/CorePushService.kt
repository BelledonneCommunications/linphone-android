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
package org.linphone.core

import android.content.Intent
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.PushService
import org.linphone.notifications.NotificationsManager

@MainThread
class CorePushService : PushService() {
    companion object {
        private const val TAG = "[Core Push Service]"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("$TAG Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("$TAG onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i("$TAG Task removed, doing nothing")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i("$TAG onDestroy")
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun createServiceNotification() {
        mServiceNotification = NotificationCompat.Builder(
            this,
            SERVICE_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.notification_push_received_title))
            .setSmallIcon(R.drawable.linphone_notification)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup(NotificationsManager.CHAT_NOTIFICATIONS_GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(true)
            .build()
    }
}

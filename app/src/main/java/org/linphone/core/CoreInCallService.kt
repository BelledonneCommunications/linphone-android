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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.CoreService

@MainThread
class CoreInCallService : CoreService() {
    companion object {
        private const val TAG = "[Core InCall Service]"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("$TAG Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("$TAG onStartCommand")
        coreContext.notificationsManager.onInCallServiceStarted(this)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i("$TAG Task removed, doing nothing")

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i("$TAG onDestroy")
        coreContext.notificationsManager.onInCallServiceDestroyed()

        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun createServiceNotificationChannel() {
        // Do nothing, app's Notifications Manager will do the job
    }

    override fun createServiceNotification() {
        // Do nothing, app's Notifications Manager will do the job
    }

    override fun showForegroundServiceNotification(isVideoCall: Boolean) {
        // Do nothing, app's Notifications Manager will do the job
    }

    override fun hideForegroundServiceNotification() {
        // Do nothing, app's Notifications Manager will do the job
    }
}

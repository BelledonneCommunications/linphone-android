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
package org.linphone.core

import android.content.Intent
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.CoreService

class CoreService : CoreService() {
    override fun onCreate() {
        super.onCreate()

        coreContext.notificationsManager.service = this
        Log.i("[Service] Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.extras?.get("StartForeground") == true) {
            Log.i("[Service] Starting as foreground")
            coreContext.notificationsManager.startForeground(this, true)
        } else if (corePreferences.keepServiceAlive) {
            coreContext.notificationsManager.startForeground(this, false)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun createServiceNotificationChannel() {
        // Done elsewhere
    }

    override fun showForegroundServiceNotification() {
        coreContext.notificationsManager.startCallForeground(this)
    }

    override fun hideForegroundServiceNotification() {
        coreContext.notificationsManager.stopCallForeground()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!corePreferences.keepServiceAlive) {
            Log.i("[Service] Task removed, stopping Core")
            coreContext.stop()
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i("[Service] Stopping")
        coreContext.notificationsManager.service = null

        super.onDestroy()
    }
}

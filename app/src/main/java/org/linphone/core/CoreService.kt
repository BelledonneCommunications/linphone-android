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
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.LinphoneApplication.Companion.ensureCoreExists
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.CoreService

class CoreService : CoreService() {
    override fun onCreate() {
        super.onCreate()
        Log.i("[Service] Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("[Service] Ensuring Core exists")
        if (corePreferences.keepServiceAlive) {
            Log.i("[Service] Starting as foreground to keep app alive in background")
            if (!ensureCoreExists(applicationContext, pushReceived = false, service = this, useAutoStartDescription = false)) {
                coreContext.notificationsManager.startForeground(this, false)
            }
        } else if (intent?.extras?.get("StartForeground") == true) {
            Log.i("[Service] Starting as foreground due to device boot or app update")
            if (!ensureCoreExists(applicationContext, pushReceived = false, service = this, useAutoStartDescription = true)) {
                coreContext.notificationsManager.startForeground(this, true)
            }
            coreContext.checkIfForegroundServiceNotificationCanBeRemovedAfterDelay(5000)
        } else {
            ensureCoreExists(applicationContext, pushReceived = false, service = this, useAutoStartDescription = false)
        }

        coreContext.notificationsManager.serviceCreated(this)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun createServiceNotificationChannel() {
        // Done elsewhere
    }

    override fun showForegroundServiceNotification() {
        Log.i("[Service] Starting service as foreground")
        coreContext.notificationsManager.startCallForeground(this)
    }

    override fun hideForegroundServiceNotification() {
        Log.i("[Service] Stopping service as foreground")
        coreContext.notificationsManager.stopCallForeground()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (LinphoneApplication.contextExists()) {
            if (coreContext.core.callsNb > 0) {
                Log.w("[Service] Task removed but there is at least one active call, do not stop the Core!")
            } else if (!corePreferences.keepServiceAlive) {
                if (coreContext.core.isInBackground) {
                    Log.i("[Service] Task removed, stopping Core")
                    coreContext.stop()
                } else {
                    Log.w("[Service] Task removed but Core is not in background, skipping")
                }
            } else {
                Log.i("[Service] Task removed but we were asked to keep the service alive, so doing nothing")
            }
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (LinphoneApplication.contextExists()) {
            Log.i("[Service] Stopping")
            coreContext.notificationsManager.serviceDestroyed()
        }

        super.onDestroy()
    }
}

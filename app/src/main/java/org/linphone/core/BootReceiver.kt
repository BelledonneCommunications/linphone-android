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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true)) {
            val autoStart = corePreferences.autoStart
            Log.i("[Boot Receiver] Device is starting, autoStart is $autoStart")
            if (autoStart) {
                startService(context)
            }
        } else if (intent.action.equals(Intent.ACTION_MY_PACKAGE_REPLACED, ignoreCase = true)) {
            val autoStart = corePreferences.autoStart
            Log.i("[Boot Receiver] App has been updated, autoStart is $autoStart")
            if (autoStart) {
                startService(context)
            }
        }
    }

    private fun startService(context: Context) {
        val serviceChannel = context.getString(R.string.notification_channel_service_id)
        val notificationManager = NotificationManagerCompat.from(context)
        if (Compatibility.getChannelImportance(notificationManager, serviceChannel) == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.w("[Boot Receiver] Service channel is disabled!")
            return
        }

        val serviceIntent = Intent(Intent.ACTION_MAIN).setClass(context, CoreService::class.java)
        serviceIntent.putExtra("StartForeground", true)
        Compatibility.startForegroundService(context, serviceIntent)
    }
}

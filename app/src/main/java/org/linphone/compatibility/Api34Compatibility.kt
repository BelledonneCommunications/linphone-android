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
package org.linphone.compatibility

import android.annotation.TargetApi
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.linphone.core.tools.Log

@TargetApi(34)
class Api34Compatibility {
    companion object {
        fun hasFullScreenIntentPermission(context: Context): Boolean {
            val notificationManager = context.getSystemService(NotificationManager::class.java) as NotificationManager
            // See https://developer.android.com/reference/android/app/NotificationManager#canUseFullScreenIntent%28%29
            return notificationManager.canUseFullScreenIntent()
        }

        fun requestFullScreenIntentPermission(context: Context) {
            val intent = Intent()
            // See https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            intent.action = Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            ContextCompat.startActivity(context, intent, null)
        }

        fun startCallForegroundService(service: Service, notifId: Int, notif: Notification) {
            try {
                service.startForeground(
                    notifId,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } catch (fssnae: ForegroundServiceStartNotAllowedException) {
                Log.e("[Api34 Compatibility] Can't start service as foreground! $fssnae")
            } catch (se: SecurityException) {
                Log.e("[Api34 Compatibility] Can't start service as foreground! $se")
            } catch (e: Exception) {
                Log.e("[Api34 Compatibility] Can't start service as foreground! $e")
            }
        }

        fun startDataSyncForegroundService(service: Service, notifId: Int, notif: Notification) {
            try {
                service.startForeground(
                    notifId,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } catch (fssnae: ForegroundServiceStartNotAllowedException) {
                Log.e("[Api34 Compatibility] Can't start service as foreground! $fssnae")
            } catch (se: SecurityException) {
                Log.e("[Api34 Compatibility] Can't start service as foreground! $se")
            } catch (e: Exception) {
                Log.e("[Api34 Compatibility] Can't start service as foreground! $e")
            }
        }
    }
}

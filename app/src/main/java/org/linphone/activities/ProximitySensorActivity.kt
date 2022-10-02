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
package org.linphone.activities

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

abstract class ProximitySensorActivity : GenericActivity() {
    private lateinit var proximityWakeLock: PowerManager.WakeLock
    private var proximitySensorEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            Log.w("[Proximity Sensor Activity] PROXIMITY_SCREEN_OFF_WAKE_LOCK isn't supported on this device!")
        }

        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "$packageName;proximity_sensor"
        )
    }

    override fun onResume() {
        super.onResume()

        if (coreContext.core.callsNb > 0) {
            val videoEnabled = coreContext.core.currentCall?.currentParams?.isVideoEnabled ?: false
            enableProximitySensor(!videoEnabled)
        }
    }

    override fun onPause() {
        enableProximitySensor(false)

        super.onPause()
    }

    override fun onDestroy() {
        enableProximitySensor(false)

        super.onDestroy()
    }

    protected fun enableProximitySensor(enable: Boolean) {
        if (enable) {
            if (!proximitySensorEnabled) {
                Log.i("[Proximity Sensor Activity] Enabling proximity sensor turning off screen")
                if (!proximityWakeLock.isHeld) {
                    Log.i("[Proximity Sensor Activity] Acquiring PROXIMITY_SCREEN_OFF_WAKE_LOCK")
                    proximityWakeLock.acquire()
                }
                proximitySensorEnabled = true
            }
        } else {
            if (proximitySensorEnabled) {
                Log.i("[Proximity Sensor Activity] Disabling proximity sensor turning off screen")
                if (proximityWakeLock.isHeld) {
                    Log.i("[Proximity Sensor Activity] Releasing PROXIMITY_SCREEN_OFF_WAKE_LOCK")
                    proximityWakeLock.release(RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                }
                proximitySensorEnabled = false
            }
        }
    }
}

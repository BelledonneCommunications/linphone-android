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
package org.linphone.activities.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import org.linphone.LinphoneApplication
import org.linphone.activities.GenericActivity
import org.linphone.core.tools.Log

abstract class ProximitySensorActivity : GenericActivity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var proximitySensor: Sensor
    private lateinit var proximityWakeLock: PowerManager.WakeLock
    private val proximityListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.timestamp == 0L) return
            if (isProximitySensorNearby(event)) {
                if (!proximityWakeLock.isHeld) {
                    Log.i("[Call Activity] Acquiring proximity wake lock")
                    proximityWakeLock.acquire()
                }
            } else {
                if (proximityWakeLock.isHeld) {
                    Log.i("[Call Activity] Releasing proximity wake lock")
                    proximityWakeLock.release()
                }
            }
        }
    }
    private var proximitySensorEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        proximityWakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "$packageName;proximity_sensor"
            )
    }

    override fun onResume() {
        super.onResume()

        if (LinphoneApplication.coreContext.core.callsNb > 0) {
            val currentCall = LinphoneApplication.coreContext.core.currentCall ?: LinphoneApplication.coreContext.core.calls[0]
            if (currentCall != null) {
                val videoEnabled = currentCall.currentParams.videoEnabled()
                enableProximitySensor(!videoEnabled)
            }
        }
    }

    override fun onPause() {
        enableProximitySensor(false)

        super.onPause()
    }

    protected fun enableProximitySensor(enable: Boolean) {
        if (enable) {
            if (!proximitySensorEnabled) {
                Log.i("[Call Activity] Enabling proximity sensor listener")
                sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
                proximitySensorEnabled = true
            }
        } else {
            if (proximitySensorEnabled) {
                Log.i("[Call Activity] Disabling proximity sensor listener")
                sensorManager.unregisterListener(proximityListener)
                if (proximityWakeLock.isHeld) {
                    proximityWakeLock.release()
                }
                proximitySensorEnabled = false
            }
        }
    }

    private fun isProximitySensorNearby(event: SensorEvent): Boolean {
        var threshold = 4.001f // <= 4 cm is near

        val distanceInCm = event.values[0]
        val maxDistance = event.sensor.maximumRange
        Log.d("[Call Activity] Proximity sensor report [$distanceInCm] , for max range [$maxDistance]")

        if (maxDistance <= threshold) {
            // Case binary 0/1 and short sensors
            threshold = maxDistance
        }
        return distanceInCm < threshold
    }
}

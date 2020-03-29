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
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.call.viewmodels.ControlsFadingViewModel
import org.linphone.activities.call.viewmodels.SharedCallViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.CallActivityBinding

class CallActivity : GenericActivity() {
    private lateinit var binding: CallActivityBinding
    private lateinit var viewModel: ControlsFadingViewModel
    private lateinit var sharedViewModel: SharedCallViewModel

    private var previewX: Float = 0f
    private var previewY: Float = 0f
    private lateinit var videoZoomHelper: VideoZoomHelper

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
            .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "$packageName;proximity_sensor")

        binding = DataBindingUtil.setContentView(this, R.layout.call_activity)
        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(ControlsFadingViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = ViewModelProvider(this).get(SharedCallViewModel::class.java)

        sharedViewModel.toggleDrawerEvent.observe(this, Observer {
            it.consume {
                if (binding.statsMenu.isDrawerOpen(Gravity.LEFT)) {
                    binding.statsMenu.closeDrawer(binding.sideMenuContent, true)
                } else {
                    binding.statsMenu.openDrawer(binding.sideMenuContent, true)
                }
            }
        })

        coreContext.core.nativeVideoWindowId = binding.remoteVideoSurface
        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface

        binding.setPreviewTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previewX = v.x - event.rawX
                    previewY = v.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate().x(event.rawX + previewX).y(event.rawY + previewY).setDuration(0).start()
                }
                else -> false
            }
            true
        }

        videoZoomHelper = VideoZoomHelper(this, binding.remoteVideoSurface)

        viewModel.videoEnabledEvent.observe(this, Observer {
            it.consume { videoEnabled ->
                enableProximitySensor(!videoEnabled)
            }
        })
    }

    override fun onResume() {
        super.onResume()

        if (coreContext.core.callsNb == 0) {
            Log.w("[Call Activity] Resuming but no call found...")
            finish()
        } else {
            coreContext.removeCallOverlay()

            val currentCall = coreContext.core.currentCall ?: coreContext.core.calls[0]
            if (currentCall != null) {
                val videoEnabled = currentCall.currentParams.videoEnabled()
                enableProximitySensor(!videoEnabled)
            }
        }
    }

    override fun onPause() {
        enableProximitySensor(false)

        val core = coreContext.core
        if (core.callsNb > 0) {
            coreContext.createCallOverlay()
        }

        super.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (coreContext.core.currentCall?.currentParams?.videoEnabled() == true) {
            Compatibility.enterPipMode(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (isInPictureInPictureMode) {
            viewModel.areControlsHidden.value = true
        }
    }

    private fun enableProximitySensor(enable: Boolean) {
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

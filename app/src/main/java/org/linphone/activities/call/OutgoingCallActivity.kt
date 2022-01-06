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

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.google.android.flexbox.FlexboxLayout
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.call.viewmodels.CallViewModel
import org.linphone.activities.call.viewmodels.CallViewModelFactory
import org.linphone.activities.call.viewmodels.ControlsViewModel
import org.linphone.activities.main.MainActivity
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.databinding.CallOutgoingActivityBinding
import org.linphone.mediastream.Version
import org.linphone.utils.PermissionHelper

class OutgoingCallActivity : ProximitySensorActivity() {
    private lateinit var binding: CallOutgoingActivityBinding
    private lateinit var viewModel: CallViewModel
    private lateinit var controlsViewModel: ControlsViewModel

    // We have to use lateinit here because we need to compute the screen width first
    private lateinit var numpadAnimator: ValueAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.call_outgoing_activity)
        binding.lifecycleOwner = this

        val outgoingCall: Call? = findOutgoingCall()
        if (outgoingCall == null) {
            Log.e("[Outgoing Call Activity] Couldn't find call in state Outgoing")
            if (isTaskRoot) {
                // When resuming app from recent tasks make sure MainActivity will be launched if there is no call
                val intent = Intent()
                intent.setClass(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                finish()
            }
            return
        }

        viewModel = ViewModelProvider(
            this,
            CallViewModelFactory(outgoingCall)
        )[CallViewModel::class.java]
        binding.viewModel = viewModel

        controlsViewModel = ViewModelProvider(this)[ControlsViewModel::class.java]
        binding.controlsViewModel = controlsViewModel

        viewModel.callEndedEvent.observe(
            this,
            {
                it.consume {
                    Log.i("[Outgoing Call Activity] Call ended, finish activity")
                    finish()
                }
            }
        )

        viewModel.callConnectedEvent.observe(
            this,
            {
                it.consume {
                    Log.i("[Outgoing Call Activity] Call connected, finish activity")
                    finish()
                }
            }
        )

        controlsViewModel.isSpeakerSelected.observe(
            this,
            {
                enableProximitySensor(!it)
            }
        )

        controlsViewModel.askAudioRecordPermissionEvent.observe(
            this,
            {
                it.consume { permission ->
                    requestPermissions(arrayOf(permission), 0)
                }
            }
        )

        controlsViewModel.askCameraPermissionEvent.observe(
            this,
            {
                it.consume { permission ->
                    requestPermissions(arrayOf(permission), 0)
                }
            }
        )

        controlsViewModel.toggleNumpadEvent.observe(
            this,
            {
                it.consume { open ->
                    if (this::numpadAnimator.isInitialized) {
                        if (open) {
                            numpadAnimator.start()
                        } else {
                            numpadAnimator.reverse()
                        }
                    }
                }
            }
        )

        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            checkPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        initNumpadLayout()
    }

    override fun onStop() {
        numpadAnimator.end()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        val outgoingCall: Call? = findOutgoingCall()
        if (outgoingCall == null) {
            Log.e("[Outgoing Call Activity] Couldn't find call in state Outgoing")
            if (isTaskRoot) {
                // When resuming app from recent tasks make sure MainActivity will be launched if there is no call
                val intent = Intent()
                intent.setClass(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                finish()
            }
        }
    }

    @TargetApi(Version.API23_MARSHMALLOW_60)
    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Outgoing Call Activity] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(Manifest.permission.RECORD_AUDIO)
        }
        if (viewModel.call.currentParams.isVideoEnabled && !PermissionHelper.get().hasCameraPermission()) {
            Log.i("[Outgoing Call Activity] Asking for CAMERA permission")
            permissionsRequiredList.add(Manifest.permission.CAMERA)
        }
        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Outgoing Call Activity] RECORD_AUDIO permission has been granted")
                        controlsViewModel.updateMuteMicState()
                    }
                    Manifest.permission.CAMERA -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Outgoing Call Activity] CAMERA permission has been granted")
                        coreContext.core.reloadVideoDevices()
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun findOutgoingCall(): Call? {
        for (call in coreContext.core.calls) {
            if (call.state == Call.State.OutgoingInit ||
                call.state == Call.State.OutgoingProgress ||
                call.state == Call.State.OutgoingRinging ||
                call.state == Call.State.OutgoingEarlyMedia
            ) {
                return call
            }
        }
        return null
    }

    private fun initNumpadLayout() {
        val screenWidth = coreContext.screenWidth
        numpadAnimator = ValueAnimator.ofFloat(screenWidth, 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                findViewById<FlexboxLayout>(R.id.numpad)?.translationX = -value
                duration = if (LinphoneApplication.corePreferences.enableAnimations) 500 else 0
            }
        }
        // Hide the numpad here as we can't set the translationX property on include tag in layout
        if (this::controlsViewModel.isInitialized && controlsViewModel.numpadVisibility.value == false) {
            findViewById<FlexboxLayout>(R.id.numpad)?.translationX = -screenWidth
        }
    }
}

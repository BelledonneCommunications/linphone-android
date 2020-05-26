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

import android.annotation.TargetApi
import android.os.Bundle
import android.view.WindowManager
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.call.viewmodels.CallViewModel
import org.linphone.activities.call.viewmodels.CallViewModelFactory
import org.linphone.activities.call.viewmodels.ControlsViewModel
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.databinding.CallOutgoingActivityBinding
import org.linphone.mediastream.Version
import org.linphone.utils.PermissionHelper

class OutgoingCallActivity : ProximitySensorActivity() {
    private lateinit var binding: CallOutgoingActivityBinding
    private lateinit var viewModel: CallViewModel
    private lateinit var controlsViewModel: ControlsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DataBindingUtil.setContentView(this, R.layout.call_outgoing_activity)
        binding.lifecycleOwner = this

        var outgoingCall: Call? = findOutgoingCall()
        if (outgoingCall == null) {
            Log.e("[Outgoing Call Activity] Couldn't find call in state Outgoing")
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            CallViewModelFactory(outgoingCall)
        )[CallViewModel::class.java]
        binding.viewModel = viewModel

        controlsViewModel = ViewModelProvider(this).get(ControlsViewModel::class.java)
        binding.controlsViewModel = controlsViewModel

        binding.setTerminateCallClickListener {
            viewModel.terminateCall()
        }

        binding.setToggleMicrophoneClickListener {
            if (PermissionHelper.get().hasRecordAudioPermission()) {
                controlsViewModel.toggleMuteMicrophone()
            } else {
                checkPermissions()
            }
        }

        binding.setToggleSpeakerClickListener {
            controlsViewModel.toggleSpeaker()
        }

        viewModel.callEndedEvent.observe(this, Observer {
            it.consume {
                Log.i("[Outgoing Call Activity] Call ended, finish activity")
                finish()
            }
        })

        viewModel.callConnectedEvent.observe(this, Observer {
            it.consume {
                Log.i("[Outgoing Call Activity] Call connected, finish activity")
                finish()
            }
        })

        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            checkPermissions()
        }
    }

    override fun onResume() {
        super.onResume()

        var outgoingCall: Call? = findOutgoingCall()
        if (outgoingCall == null) {
            Log.e("[Outgoing Call Activity] Couldn't find call in state Outgoing")
            finish()
        }
    }

    @TargetApi(Version.API23_MARSHMALLOW_60)
    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Outgoing Call Activity] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (viewModel.call.currentParams.videoEnabled() && !PermissionHelper.get().hasCameraPermission()) {
            Log.i("[Outgoing Call Activity] Asking for CAMERA permission")
            permissionsRequiredList.add(android.Manifest.permission.CAMERA)
        }
        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }

    private fun findOutgoingCall(): Call? {
        for (call in coreContext.core.calls) {
            if (call.state == Call.State.OutgoingInit ||
                call.state == Call.State.OutgoingProgress ||
                call.state == Call.State.OutgoingRinging) {
                return call
            }
        }
        return null
    }
}

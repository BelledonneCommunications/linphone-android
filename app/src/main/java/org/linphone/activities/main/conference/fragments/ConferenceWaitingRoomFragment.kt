/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.conference.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.conference.viewmodels.ConferenceWaitingRoomViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.ConferenceWaitingRoomFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.utils.PermissionHelper

class ConferenceWaitingRoomFragment : GenericFragment<ConferenceWaitingRoomFragmentBinding>() {
    private lateinit var viewModel: ConferenceWaitingRoomViewModel

    override fun getLayoutId(): Int = R.layout.conference_waiting_room_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(
            this
        )[ConferenceWaitingRoomViewModel::class.java]
        binding.viewModel = viewModel

        val conferenceSubject = arguments?.getString("Subject")
        viewModel.subject.value = conferenceSubject

        viewModel.cancelConferenceJoiningEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (viewModel.joinInProgress.value == true) {
                    val conferenceUri = arguments?.getString("Address")
                    val callToCancel = coreContext.core.calls.find { call ->
                        call.remoteAddress.asStringUriOnly() == conferenceUri
                    }
                    if (callToCancel != null) {
                        Log.i("[Conference Waiting Room] Call to conference server with URI [$conferenceUri] was started, terminate it")
                        callToCancel.terminate()
                    } else {
                        Log.w("[Conference Waiting Room] Call to conference server with URI [$conferenceUri] wasn't found!")
                    }
                }
                goBack()
            }
        }

        viewModel.joinConferenceEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { callParams ->
                val conferenceUri = arguments?.getString("Address")
                if (conferenceUri != null) {
                    val conferenceAddress = coreContext.core.interpretUrl(conferenceUri, false)
                    if (conferenceAddress != null) {
                        Log.i("[Conference Waiting Room] Calling conference SIP URI: ${conferenceAddress.asStringUriOnly()}")
                        coreContext.startCall(conferenceAddress, callParams)
                    } else {
                        Log.e("[Conference Waiting Room] Failed to parse conference SIP URI: $conferenceUri")
                    }
                } else {
                    Log.e("[Conference Waiting Room] Failed to find conference SIP URI in arguments")
                }
            }
        }

        viewModel.askPermissionEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { permission ->
                Log.i("[Conference Waiting Room] Asking for $permission permission")
                requestPermissions(arrayOf(permission), 0)
            }
        }

        viewModel.leaveWaitingRoomEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                goBack()
            }
        }

        viewModel.onMessageToNotifyEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { message ->
                (activity as MainActivity).showSnackBar(message)
            }
        }

        viewModel.networkNotReachableEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                (activity as MainActivity).showSnackBar(R.string.call_error_network_unreachable)
            }
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()

        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface
        val enablePreview = viewModel.isVideoEnabled.value == true
        if (enablePreview) {
            Log.i("[Conference Waiting Room] Fragment is being resumed, enabling video preview")
        }
        coreContext.core.isVideoPreviewEnabled = enablePreview
    }

    override fun onPause() {
        Log.i("[Conference Waiting Room] Fragment is being paused, disabling video preview")
        coreContext.core.isVideoPreviewEnabled = false
        coreContext.core.nativePreviewWindowId = null

        super.onPause()
    }

    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()

        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Conference Waiting Room] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(Manifest.permission.RECORD_AUDIO)
        }

        if (!PermissionHelper.get().hasCameraPermission()) {
            Log.i("[Conference Waiting Room] Asking for CAMERA permission")
            permissionsRequiredList.add(Manifest.permission.CAMERA)
        }

        if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12) && !PermissionHelper.get().hasBluetoothConnectPermission()) {
            Log.i("[Conference Waiting Room] Asking for BLUETOOTH_CONNECT permission")
            permissionsRequiredList.add(Compatibility.BLUETOOTH_CONNECT)
        }

        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Conference Waiting Room] RECORD_AUDIO permission has been granted")
                        viewModel.enableMic()
                    }
                    Manifest.permission.CAMERA -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Conference Waiting Room] CAMERA permission has been granted")
                        coreContext.core.reloadVideoDevices()
                        viewModel.enableVideo()
                    }
                    Compatibility.BLUETOOTH_CONNECT -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Conference Waiting Room] BLUETOOTH_CONNECT permission has been granted")
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}

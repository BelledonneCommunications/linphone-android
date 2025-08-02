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
package com.naminfo.ui.main.meetings.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.MeetingWaitingRoomFragmentBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.call.fragment.AudioDevicesMenuDialogFragment
import com.naminfo.ui.call.model.AudioDeviceModel
import com.naminfo.ui.main.fragment.GenericMainFragment
import com.naminfo.ui.main.meetings.viewmodel.MeetingWaitingRoomViewModel

@UiThread
class MeetingWaitingRoomFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Meeting Waiting Room Fragment]"
    }

    private lateinit var binding: MeetingWaitingRoomFragmentBinding

    private lateinit var viewModel: MeetingWaitingRoomViewModel

    private val args: MeetingWaitingRoomFragmentArgs by navArgs()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG CAMERA permission has been granted")
            enableVideoPreview()
        } else {
            Log.e("$TAG CAMERA permission has been denied, leaving this fragment")
            goBack()
        }
    }

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingWaitingRoomFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[MeetingWaitingRoomViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val uri = args.conferenceUri
        Log.i(
            "$TAG Looking up for conference with SIP URI [$uri]"
        )
        viewModel.findConferenceInfo(uri)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.showAudioDevicesListEvent.observe(viewLifecycleOwner) {
            it.consume { devices ->
                showAudioRoutesMenu(devices)
            }
        }

        viewModel.conferenceInfoFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    startPostponedEnterTransition()
                } else {
                    Log.e("$TAG Failed to find meeting with URI [$uri], going back")
                    goBack()
                }
            }
        }

        viewModel.leaveWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Leaving waiting room")
                goBack()
            }
        }

        viewModel.conferenceCreationError.observe(viewLifecycleOwner) {
            it.consume {
                Log.e("$TAG Error joining the conference!")
                val message = getString(
                    R.string.meeting_waiting_room_failed_to_join_toast
                )
                val icon = R.drawable.warning_circle
                (requireActivity() as GenericActivity).showRedToast(message, icon)
            }
        }

        if (!isCameraPermissionGranted()) {
            viewModel.isVideoAvailable.value = false
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA)) {
                Log.w("$TAG CAMERA permission wasn't granted yet, asking for it now")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                Log.i("$TAG Permission request for CAMERA will be automatically denied, go to android app settings instead")
                (requireActivity() as GenericActivity).goToAndroidPermissionSettings()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (viewModel.joining.value == false) {
            if (isCameraPermissionGranted()) {
                Log.i(
                    "$TAG Record video permission is granted, starting video preview with front cam if possible"
                )
                viewModel.setFrontCamera()
                enableVideoPreview()
            }
        }
    }

    override fun onPause() {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null

        coreContext.postOnCoreThread { core ->
            core.nativePreviewWindowId = null
            core.isVideoPreviewEnabled = false
        }

        super.onPause()
    }

    private fun isCameraPermissionGranted(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.i("$TAG CAMERA permission is ${if (granted) "granted" else "denied"}")
        return granted
    }

    private fun enableVideoPreview() {
        coreContext.postOnCoreThread { core ->
            if (core.isVideoEnabled) {
                viewModel.isVideoAvailable.postValue(true)
                core.nativePreviewWindowId = binding.videoPreview
                core.isVideoPreviewEnabled = true
            }
        }
    }

    private fun showAudioRoutesMenu(devicesList: List<AudioDeviceModel>) {
        val modalBottomSheet = AudioDevicesMenuDialogFragment(devicesList)
        modalBottomSheet.show(parentFragmentManager, AudioDevicesMenuDialogFragment.TAG)
        bottomSheetDialog = modalBottomSheet
    }
}

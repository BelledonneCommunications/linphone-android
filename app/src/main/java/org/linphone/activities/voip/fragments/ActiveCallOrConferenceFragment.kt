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
package org.linphone.activities.voip.fragments

import android.app.Dialog
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.call.viewmodels.SharedCallViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToCallsList
import org.linphone.activities.navigateToConferenceParticipants
import org.linphone.activities.navigateToNumpad
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.activities.voip.views.RoundCornersTextureView
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipActiveCallOrConferenceFragmentBindingImpl
import org.linphone.mediastream.video.capture.CaptureTextureView
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

class ActiveCallOrConferenceFragment : GenericFragment<VoipActiveCallOrConferenceFragmentBindingImpl>() {
    private lateinit var sharedViewModel: SharedCallViewModel
    private lateinit var controlsViewModel: ControlsViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var conferenceViewModel: ConferenceViewModel

    private var dialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.voip_active_call_or_conference_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedCallViewModel::class.java)
        }

        controlsViewModel = requireActivity().run {
            ViewModelProvider(this).get(ControlsViewModel::class.java)
        }
        binding.controlsViewModel = controlsViewModel

        callsViewModel = requireActivity().run {
            ViewModelProvider(this).get(CallsViewModel::class.java)
        }
        binding.callsViewModel = callsViewModel

        conferenceViewModel = requireActivity().run {
            ViewModelProvider(this).get(ConferenceViewModel::class.java)
        }
        binding.conferenceViewModel = conferenceViewModel

        conferenceViewModel.isInConference.observe(
            viewLifecycleOwner,
            {
                if (it) {
                    val timer = binding.root.findViewById<Chronometer>(R.id.conference_timer)
                    timer.start()
                }
            }
        )

        callsViewModel.currentCallData.observe(
            viewLifecycleOwner,
            {
                if (it != null) {
                    val timer = binding.root.findViewById<Chronometer>(R.id.active_call_timer)
                    timer.base =
                        SystemClock.elapsedRealtime() - (1000 * it.call.duration) // Linphone timestamps are in seconds
                    timer.start()
                }
            }
        )

        controlsViewModel.goToConferenceParticipantsListEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    navigateToConferenceParticipants()
                }
            }
        )

        controlsViewModel.goToChatEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    val localSipUri = coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
                    val remoteSipUri = if (conferenceViewModel.isInConference.value == true) {
                        conferenceViewModel.conferenceAddress.value?.asStringUriOnly()
                    } else {
                        coreContext.core.currentCall?.remoteAddress?.asStringUriOnly()
                    }

                    val bundle = Bundle()
                    bundle.putString("RemoteSipUri", remoteSipUri)
                    bundle.putString("LocalSipUri", localSipUri)

                    navigateToChat(bundle)
                }
            }
        )

        controlsViewModel.goToCallsListEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    navigateToCallsList()
                }
            }
        )

        controlsViewModel.showCallStatistics.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    sharedViewModel.toggleDrawerEvent.value = Event(true)
                    controlsViewModel.hideExtraButtons()
                }
            }
        )

        controlsViewModel.goToNumpadEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    navigateToNumpad()
                }
            }
        )

        callsViewModel.callUpdateEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { call ->
                    if (call.state == Call.State.StreamsRunning) {
                        dialog?.dismiss()
                    } else if (call.state == Call.State.UpdatedByRemote) {
                        if (coreContext.core.videoCaptureEnabled() || coreContext.core.videoDisplayEnabled()) {
                            if (call.currentParams.videoEnabled() != call.remoteParams?.videoEnabled()) {
                                showCallVideoUpdateDialog(call)
                            }
                        } else {
                            Log.w("[Call] Video display & capture are disabled, don't show video dialog")
                        }
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()

        val remoteLayout = binding.root.findViewById<ConstraintLayout>(R.id.remote_layout)
        val remoteVideoView = remoteLayout.findViewById<RoundCornersTextureView>(R.id.remote_video_surface)
        coreContext.core.nativeVideoWindowId = remoteVideoView

        val localVideoView = binding.root.findViewById<CaptureTextureView>(R.id.local_preview_video_surface)
        coreContext.core.nativePreviewWindowId = localVideoView
    }

    override fun onPause() {
        super.onPause()

        controlsViewModel.hideExtraButtons()
    }

    override fun onDestroy() {
        super.onDestroy()

        coreContext.core.nativeVideoWindowId = null
        coreContext.core.nativePreviewWindowId = null
    }

    private fun showCallVideoUpdateDialog(call: Call) {
        val viewModel = DialogViewModel(AppUtils.getString(R.string.call_video_update_requested_dialog))
        dialog = DialogUtils.getDialog(requireContext(), viewModel)

        viewModel.showCancelButton(
            {
                coreContext.answerCallVideoUpdateRequest(call, false)
                dialog?.dismiss()
            },
            getString(R.string.dialog_decline)
        )

        viewModel.showOkButton(
            {
                coreContext.answerCallVideoUpdateRequest(call, true)
                dialog?.dismiss()
            },
            getString(R.string.dialog_accept)
        )

        dialog?.show()
    }
}

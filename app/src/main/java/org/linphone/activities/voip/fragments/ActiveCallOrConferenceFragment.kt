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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.navigation.navGraphViewModels
import com.google.android.material.snackbar.Snackbar
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToCallsList
import org.linphone.activities.navigateToConferenceParticipants
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.activities.voip.viewmodels.StatisticsListViewModel
import org.linphone.activities.voip.views.RoundCornersTextureView
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipActiveCallOrConferenceFragmentBindingImpl
import org.linphone.mediastream.video.capture.CaptureTextureView
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils

class ActiveCallOrConferenceFragment : GenericFragment<VoipActiveCallOrConferenceFragmentBindingImpl>() {
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val statsViewModel: StatisticsListViewModel by navGraphViewModels(R.id.call_nav_graph)

    private var dialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.voip_active_call_or_conference_fragment

    override fun onStart() {
        useMaterialSharedAxisXForwardAnimation = false

        super.onStart()
    }

    @SuppressLint("CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        controlsViewModel.hideCallStats() // In case it was toggled on during incoming/outgoing fragment was visible

        binding.lifecycleOwner = viewLifecycleOwner

        binding.controlsViewModel = controlsViewModel

        binding.callsViewModel = callsViewModel

        binding.conferenceViewModel = conferenceViewModel

        binding.statsViewModel = statsViewModel

        conferenceViewModel.conferenceMosaicDisplayMode.observe(
            viewLifecycleOwner
        ) {
            if (it) {
                startTimer(R.id.grid_conference_timer)
            }
        }

        conferenceViewModel.conferenceActiveSpeakerDisplayMode.observe(
            viewLifecycleOwner
        ) {
            if (it) {
                startTimer(R.id.active_speaker_conference_timer)

                if (conferenceViewModel.conferenceExists.value == true) {
                    Log.i("[Call] Local participant is in conference and current layout is active speaker, updating Core's native window id")
                    val layout =
                        binding.root.findViewById<RelativeLayout>(R.id.conference_active_speaker_layout)
                    val window =
                        layout?.findViewById<RoundCornersTextureView>(R.id.conference_active_speaker_remote_video)
                    coreContext.core.nativeVideoWindowId = window
                } else {
                    Log.i("[Call] Either not in conference or current layout isn't active speaker, updating Core's native window id")
                    val layout = binding.root.findViewById<LinearLayout>(R.id.remote_layout)
                    val window =
                        layout?.findViewById<RoundCornersTextureView>(R.id.remote_video_surface)
                    coreContext.core.nativeVideoWindowId = window
                }
            }
        }

        conferenceViewModel.conferenceAudioOnlyDisplayMode.observe(
            viewLifecycleOwner
        ) {
            if (it) {
                startTimer(R.id.audio_only_conference_timer)
            }
        }

        conferenceViewModel.conferenceParticipantDevices.observe(
            viewLifecycleOwner
        ) {
            if (it.size > conferenceViewModel.maxParticipantsForMosaicLayout) {
                showSnackBar(R.string.conference_too_many_participants_for_mosaic_layout)
            }
        }

        conferenceViewModel.conference.observe(
            viewLifecycleOwner
        ) { conference ->
            if (corePreferences.enableFullScreenWhenJoiningVideoConference) {
                if (conference != null && conference.currentParams.isVideoEnabled) {
                    if (conference.me.devices.find { it.getStreamAvailability(StreamType.Video) } != null) {
                        Log.i("[Call] Conference is video & our device has video enabled, enabling full screen mode")
                        controlsViewModel.fullScreenMode.value = true
                    }
                }
            }
        }

        callsViewModel.currentCallData.observe(
            viewLifecycleOwner
        ) {
            if (it != null) {
                val timer = binding.root.findViewById<Chronometer>(R.id.active_call_timer)
                timer.base =
                    SystemClock.elapsedRealtime() - (1000 * it.call.duration) // Linphone timestamps are in seconds
                timer.start()
            }
        }

        controlsViewModel.goToConferenceParticipantsListEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                navigateToConferenceParticipants()
            }
        }

        controlsViewModel.goToChatEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                goToChat()
            }
        }

        controlsViewModel.goToCallsListEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                navigateToCallsList()
            }
        }

        controlsViewModel.goToConferenceLayoutSettings.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                navigateToConferenceLayout()
            }
        }

        callsViewModel.callUpdateEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { call ->
                if (call.state == Call.State.StreamsRunning) {
                    dialog?.dismiss()
                } else if (call.state == Call.State.UpdatedByRemote) {
                    if (coreContext.core.isVideoEnabled) {
                        val remoteVideo = call.remoteParams?.isVideoEnabled ?: false
                        val localVideo = call.currentParams.isVideoEnabled
                        if (remoteVideo && !localVideo) {
                            showCallVideoUpdateDialog(call)
                        }
                    } else {
                        Log.w("[Call] Video display & capture are disabled, don't show video dialog")
                    }
                }

                val conference = call.conference
                if (conference != null && conferenceViewModel.conference.value == null) {
                    Log.i("[Call] Found conference attached to call and no conference in dedicated view model, init & configure it")
                    conferenceViewModel.initConference(conference)
                    conferenceViewModel.configureConference(conference)
                }
            }
        }

        controlsViewModel.goToDialer.observe(
            viewLifecycleOwner
        ) {
            it.consume { isCallTransfer ->
                val intent = Intent()
                intent.setClass(requireContext(), MainActivity::class.java)
                intent.putExtra("Dialer", true)
                intent.putExtra("Transfer", isCallTransfer)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        val remoteLayout = binding.root.findViewById<LinearLayout>(R.id.remote_layout)
        val remoteVideoView = remoteLayout.findViewById<RoundCornersTextureView>(R.id.remote_video_surface)
        coreContext.core.nativeVideoWindowId = remoteVideoView
        val localVideoView = remoteLayout.findViewById<CaptureTextureView>(R.id.local_preview_video_surface)
        coreContext.core.nativePreviewWindowId = localVideoView

        binding.stubbedConferenceActiveSpeakerLayout.setOnInflateListener { _, inflated ->
            Log.i("[Call] Active speaker conference layout inflated")
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
            startTimer(R.id.active_speaker_conference_timer)
        }

        binding.stubbedConferenceGridLayout.setOnInflateListener { _, inflated ->
            Log.i("[Call] Mosaic conference layout inflated")
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
            startTimer(R.id.grid_conference_timer)
        }

        binding.stubbedConferenceAudioOnlyLayout.setOnInflateListener { _, inflated ->
            Log.i("[Call] Audio only conference layout inflated")
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
            startTimer(R.id.audio_only_conference_timer)
        }

        binding.stubbedAudioRoutes.setOnInflateListener { _, inflated ->
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedNumpad.setOnInflateListener { _, inflated ->
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedCallStats.setOnInflateListener { _, inflated ->
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedPausedCall.setOnInflateListener { _, inflated ->
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedRemotelyPausedCall.setOnInflateListener { _, inflated ->
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedPausedConference.setOnInflateListener { _, inflated ->
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }
    }

    override fun onPause() {
        super.onPause()

        controlsViewModel.hideExtraButtons(true)
    }

    override fun onDestroy() {
        super.onDestroy()

        coreContext.core.nativeVideoWindowId = null
        coreContext.core.nativePreviewWindowId = null
    }

    private fun showCallVideoUpdateDialog(call: Call) {
        val viewModel = DialogViewModel(AppUtils.getString(R.string.call_video_update_requested_dialog))
        dialog = DialogUtils.getVoipDialog(requireContext(), viewModel)

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

    private fun goToChat() {
        val intent = Intent()
        intent.setClass(requireContext(), MainActivity::class.java)
        intent.putExtra("Chat", true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun showSnackBar(resourceId: Int) {
        Snackbar.make(binding.coordinator, resourceId, Snackbar.LENGTH_LONG).show()
    }

    private fun startTimer(timerId: Int) {
        val timer: Chronometer? = binding.root.findViewById(timerId)
        if (timer == null) {
            Log.w("[Call] Timer not found, maybe view wasn't inflated yet?")
            return
        }

        val conference = conferenceViewModel.conference.value
        if (conference != null) {
            val duration = 1000 * conference.duration // Linphone timestamps are in seconds
            timer.base = SystemClock.elapsedRealtime() - duration
        } else {
            Log.e("[Call] Conference not found, timer will have no base")
        }

        timer.start()
    }
}

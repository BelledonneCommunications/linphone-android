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
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.navGraphViewModels
import com.google.android.material.snackbar.Snackbar
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.chat.viewmodels.ChatRoomViewModel
import org.linphone.activities.main.chat.viewmodels.ChatRoomViewModelFactory
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToCallsList
import org.linphone.activities.navigateToConferenceParticipants
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.activities.voip.viewmodels.StatisticsListViewModel
import org.linphone.activities.voip.views.RoundCornersTextureView
import org.linphone.core.Call
import org.linphone.core.Factory
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

        binding.lifecycleOwner = viewLifecycleOwner

        binding.controlsViewModel = controlsViewModel

        binding.callsViewModel = callsViewModel

        binding.conferenceViewModel = conferenceViewModel

        binding.statsViewModel = statsViewModel

        conferenceViewModel.conferenceMosaicDisplayMode.observe(
            viewLifecycleOwner,
            {
                if (it) {
                    startTimer(R.id.grid_conference_timer)
                }
            }
        )

        conferenceViewModel.conferenceActiveSpeakerDisplayMode.observe(
            viewLifecycleOwner,
            {
                if (it) {
                    startTimer(R.id.active_speaker_conference_timer)

                    if (conferenceViewModel.isInConference.value == true) {
                        Log.i("[Call] Local participant is in conference and current layout is active speaker, updating Core's native window id")
                        val layout = binding.root.findViewById<LinearLayout>(R.id.conference_active_speaker_layout)
                        val window = layout.findViewById<RoundCornersTextureView>(R.id.conference_active_speaker_remote_video)
                        coreContext.core.nativeVideoWindowId = window
                    } else {
                        Log.i("[Call] Either not in conference or current layout isn't active speaker, updating Core's native window id")
                        val layout = binding.root.findViewById<LinearLayout>(R.id.remote_layout)
                        val window = layout.findViewById<RoundCornersTextureView>(R.id.remote_video_surface)
                        coreContext.core.nativeVideoWindowId = window
                    }
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
                    goToChat()
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

        controlsViewModel.goToConferenceLayoutSettings.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    navigateToConferenceLayout()
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

        val remoteLayout = binding.root.findViewById<LinearLayout>(R.id.remote_layout)
        val remoteVideoView = remoteLayout.findViewById<RoundCornersTextureView>(R.id.remote_video_surface)
        coreContext.core.nativeVideoWindowId = remoteVideoView
        val localVideoView = remoteLayout.findViewById<CaptureTextureView>(R.id.local_preview_video_surface)
        coreContext.core.nativePreviewWindowId = localVideoView

        binding.stubbedConferenceActiveSpeakerLayout.setOnInflateListener { _, inflated ->
            Log.i("[Call] Active speaker conference layout inflated")
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedConferenceGridLayout.setOnInflateListener { _, inflated ->
            Log.i("[Call] Mosaic conference layout inflated")
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

    private fun goToChat() {
        val localSipUri = coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
        // TODO: handle participants list for conference
        val remoteSipUri = if (conferenceViewModel.isInConference.value == true) {
            conferenceViewModel.conferenceAddress.value?.asStringUriOnly()
        } else {
            coreContext.core.currentCall?.remoteAddress?.asStringUriOnly()
        }

        if (localSipUri != null && remoteSipUri != null) {
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            var chatRoom = coreContext.core.searchChatRoom(null, localAddress, remoteSipAddress, arrayOfNulls(0))
            if (chatRoom == null) chatRoom = coreContext.core.searchChatRoom(null, localAddress, null, arrayOf(remoteSipAddress))
            if (chatRoom == null) {
                Log.w("[Call] Failed to find existing chat room for local address [$localSipUri] and remote address [$remoteSipUri]")
                val chatRoomParams = coreContext.core.createDefaultChatRoomParams()
                // TODO: configure chat room params
                if (conferenceViewModel.isInConference.value == true) {
                    // TODO: compute conference participants addresses list
                } else {
                    chatRoom = coreContext.core.createChatRoom(chatRoomParams, localAddress, arrayOf(remoteSipAddress))
                }
            }

            if (chatRoom != null) {
                // Create the view model now so it won't be done in the ChatFragment's onCreate()
                val chatViewModel = ViewModelProvider(
                    this,
                    ChatRoomViewModelFactory(chatRoom)
                )[ChatRoomViewModel::class.java]

                val bundle = Bundle()
                // Use chat room peer address to simply search process in ChatFragment
                bundle.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
                bundle.putString("LocalSipUri", localSipUri)

                navigateToChat(bundle)
            } else {
                Log.e("[Call] Failed to create a chat room for local address [$localSipUri] and remote address [$remoteSipUri]!")
                controlsViewModel.chatRoomCreationInProgress.value = false
                showSnackBar(R.string.chat_room_failed_to_create)
            }
        } else {
            Log.e("[Call] Failed to get either local [$localSipUri] or remote [$remoteSipUri] SIP address!")
            controlsViewModel.chatRoomCreationInProgress.value = false
            showSnackBar(R.string.chat_room_failed_to_create)
        }
    }

    private fun showSnackBar(resourceId: Int) {
        Snackbar.make(binding.coordinator, resourceId, Snackbar.LENGTH_LONG).show()
    }

    private fun startTimer(timerId: Int) {
        val timer: Chronometer? = binding.root.findViewById(timerId)
        val conference = conferenceViewModel.conference.value
        if (conference != null) {
            val duration = 1000 * conference.duration // Linphone timestamps are in seconds
            timer?.base = SystemClock.elapsedRealtime() - duration
        } else {
            Log.e("[Call] Conference not found, timer will have no base")
        }
        timer?.start()
    }
}

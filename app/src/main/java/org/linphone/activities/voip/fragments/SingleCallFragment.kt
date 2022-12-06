/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.constraintlayout.widget.ConstraintSet
import androidx.navigation.navGraphViewModels
import androidx.window.layout.FoldingFeature
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.activities.voip.viewmodels.StatisticsListViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipSingleCallFragmentBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils

class SingleCallFragment : GenericVideoPreviewFragment<VoipSingleCallFragmentBinding>() {
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val statsViewModel: StatisticsListViewModel by navGraphViewModels(R.id.call_nav_graph)

    private var dialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.voip_single_call_fragment

    override fun onStart() {
        useMaterialSharedAxisXForwardAnimation = false

        super.onStart()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        controlsViewModel.hideCallStats() // In case it was toggled on during incoming/outgoing fragment was visible

        binding.lifecycleOwner = viewLifecycleOwner

        setupLocalViewPreview(binding.localPreviewVideoSurface, binding.switchCamera)

        binding.controlsViewModel = controlsViewModel

        binding.callsViewModel = callsViewModel

        binding.conferenceViewModel = conferenceViewModel

        binding.statsViewModel = statsViewModel

        callsViewModel.currentCallData.observe(
            viewLifecycleOwner
        ) { callData ->
            if (callData != null) {
                val call = callData.call

                val timer = binding.root.findViewById<Chronometer>(R.id.active_call_timer)
                timer.base =
                    SystemClock.elapsedRealtime() - (1000 * call.duration) // Linphone timestamps are in seconds
                timer.start()

                if (corePreferences.enableFullScreenWhenJoiningVideoCall) {
                    if (call.currentParams.isVideoEnabled) {
                        Log.i("[Single Call] Call params have video enabled, enabling full screen mode")
                        controlsViewModel.fullScreenMode.value = true
                    }
                }
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

        controlsViewModel.goToConferenceLayoutSettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                navigateToConferenceLayout()
            }
        }

        controlsViewModel.foldingState.observe(
            viewLifecycleOwner
        ) { feature ->
            updateHingeRelatedConstraints(feature)
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
                        Log.w("[Single Call] Video display & capture are disabled, don't show video dialog")
                    }
                }
            }
        }

        controlsViewModel.goToDialerEvent.observe(
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

        coreContext.core.nativeVideoWindowId = binding.remoteVideoSurface
        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface
    }

    override fun onPause() {
        super.onPause()

        controlsViewModel.hideExtraButtons(true)
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

    private fun updateHingeRelatedConstraints(feature: FoldingFeature) {
        Log.i("[Single Call] Updating constraint layout hinges: $feature")

        val constraintLayout = binding.constraintLayout
        val set = ConstraintSet()
        set.clone(constraintLayout)

        // Only modify UI in table top mode
        if (feature.orientation == FoldingFeature.Orientation.HORIZONTAL &&
            feature.state == FoldingFeature.State.HALF_OPENED
        ) {
            set.setGuidelinePercent(R.id.hinge_top, 0.5f)
            set.setGuidelinePercent(R.id.hinge_bottom, 0.5f)
            controlsViewModel.folded.value = true
        } else {
            set.setGuidelinePercent(R.id.hinge_top, 0f)
            set.setGuidelinePercent(R.id.hinge_bottom, 1f)
            controlsViewModel.folded.value = false
        }

        set.applyTo(constraintLayout)
    }
}

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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Chronometer
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.navigation.navGraphViewModels
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.window.layout.FoldingFeature
import com.google.android.material.snackbar.Snackbar
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.MainActivity
import org.linphone.activities.voip.ConferenceDisplayMode
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.activities.voip.viewmodels.StatisticsListViewModel
import org.linphone.activities.voip.views.RoundCornersTextureView
import org.linphone.core.Conference
import org.linphone.core.StreamType
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipConferenceCallFragmentBinding

class ConferenceCallFragment : GenericFragment<VoipConferenceCallFragmentBinding>() {
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val statsViewModel: StatisticsListViewModel by navGraphViewModels(R.id.call_nav_graph)

    override fun getLayoutId(): Int = R.layout.voip_conference_call_fragment

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

        conferenceViewModel.reloadConferenceFragmentEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("[Conference Call] Reloading fragment after toggling video ON while in AUDIO_ONLY layout")
                refreshConferenceFragment()
            }
        }

        conferenceViewModel.conferenceDisplayMode.observe(
            viewLifecycleOwner
        ) { displayMode ->
            startTimer(R.id.active_conference_timer)

            if (displayMode == ConferenceDisplayMode.ACTIVE_SPEAKER) {
                if (conferenceViewModel.conferenceExists.value == true) {
                    Log.i("[Conference Call] Local participant is in conference and current layout is active speaker, updating Core's native window id")
                    val window = binding.root.findViewById<RoundCornersTextureView>(R.id.conference_active_speaker_remote_video)
                    coreContext.core.nativeVideoWindowId = window

                    val preview = binding.root.findViewById<RoundCornersTextureView>(R.id.local_preview_video_surface)
                    if (preview != null) {
                        conferenceViewModel.meParticipant.value?.setTextureView(preview)
                    }
                } else {
                    Log.i("[Conference Call] Either not in conference or current layout isn't active speaker, updating Core's native window id")
                    coreContext.core.nativeVideoWindowId = null
                }
            }

            when (displayMode) {
                ConferenceDisplayMode.AUDIO_ONLY -> {
                    controlsViewModel.fullScreenMode.value = false
                }
                else -> {
                    val conference = conferenceViewModel.conference.value
                    if (conference != null) switchToFullScreenIfPossible(conference)
                }
            }
        }

        conferenceViewModel.conferenceParticipantDevices.observe(
            viewLifecycleOwner
        ) {
            if (
                conferenceViewModel.conferenceDisplayMode.value == ConferenceDisplayMode.GRID &&
                it.size > conferenceViewModel.maxParticipantsForMosaicLayout
            ) {
                Log.w("[Conference Call] More than ${conferenceViewModel.maxParticipantsForMosaicLayout} participants (${it.size}), forcing active speaker layout")
                conferenceViewModel.changeLayout(ConferenceDisplayMode.ACTIVE_SPEAKER, false)
                refreshConferenceFragment()
                // Can't use SnackBar whilst changing fragment
                Toast.makeText(requireContext(), R.string.conference_too_many_participants_for_mosaic_layout, Toast.LENGTH_LONG).show()
            }
        }

        conferenceViewModel.secondParticipantJoinedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                switchToActiveSpeakerLayoutForTwoParticipants()
            }
        }

        conferenceViewModel.moreThanTwoParticipantsJoinedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                switchToActiveSpeakerLayoutForMoreThanTwoParticipants()
            }
        }

        conferenceViewModel.conference.observe(
            viewLifecycleOwner
        ) { conference ->
            if (conference != null) switchToFullScreenIfPossible(conference)
        }

        conferenceViewModel.conferenceCreationPending.observe(
            viewLifecycleOwner
        ) { creationPending ->
            if (!creationPending) {
                val conference = conferenceViewModel.conference.value
                if (conference != null) switchToFullScreenIfPossible(conference)
            }
        }

        conferenceViewModel.firstToJoinEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Snackbar
                    .make(binding.coordinator, R.string.conference_first_to_join, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.primaryButtons.hangup)
                    .show()
            }
        }

        conferenceViewModel.allParticipantsLeftEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Snackbar
                    .make(binding.coordinator, R.string.conference_last_user, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.primaryButtons.hangup)
                    .show()

                switchToActiveSpeakerLayoutWhenAlone()
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
                val conference = call.conference
                if (conference != null && conferenceViewModel.conference.value == null) {
                    Log.i("[Conference Call] Found conference attached to call and no conference in dedicated view model, init & configure it")
                    conferenceViewModel.initConference(conference)
                    conferenceViewModel.configureConference(conference)
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
    }

    override fun onPause() {
        super.onPause()

        controlsViewModel.hideExtraButtons(true)
    }

    override fun onResume() {
        super.onResume()

        if (conferenceViewModel.conferenceDisplayMode.value == ConferenceDisplayMode.ACTIVE_SPEAKER) {
            Log.i("[Conference Call] Conference fragment is resuming, current display mode is active speaker, adjusting layout")
            adjustActiveSpeakerLayout()
        }
    }

    private fun switchToFullScreenIfPossible(conference: Conference) {
        if (corePreferences.enableFullScreenWhenJoiningVideoConference) {
            if (conference.currentParams.isVideoEnabled) {
                when {
                    conference.me.devices.isEmpty() -> {
                        Log.i("[Conference Call] Conference has video enabled but our device hasn't joined yet")
                    }
                    conference.me.devices.find { it.isInConference && it.getStreamAvailability(StreamType.Video) } != null -> {
                        Log.i("[Conference Call] Conference has video enabled & our device has video enabled, enabling full screen mode")
                        controlsViewModel.fullScreenMode.value = true
                    }
                    else -> {
                        Log.i("[Conference Call] Conference has video enabled but our device video is disabled")
                    }
                }
            }
        }
    }

    private fun goToChat() {
        val intent = Intent()
        intent.setClass(requireContext(), MainActivity::class.java)
        intent.putExtra("Chat", true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun startTimer(timerId: Int) {
        val timer: Chronometer? = binding.root.findViewById(timerId)
        if (timer == null) {
            Log.w("[Conference Call] Timer not found, maybe view wasn't inflated yet?")
            return
        }

        val conference = conferenceViewModel.conference.value
        if (conference != null) {
            val duration = 1000 * conference.duration // Linphone timestamps are in seconds
            timer.base = SystemClock.elapsedRealtime() - duration
        } else {
            Log.e("[Conference Call] Conference not found, timer will have no base")
        }

        timer.start()
    }

    private fun updateHingeRelatedConstraints(feature: FoldingFeature) {
        Log.i("[Conference Call] Updating constraint layout hinges: $feature")
        val constraintLayout = binding.root.findViewById<ConstraintLayout>(R.id.conference_constraint_layout)
            ?: return
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

    private fun animateConstraintLayout(
        constraintLayout: ConstraintLayout,
        set: ConstraintSet
    ) {
        val trans = AutoTransition()
        trans.duration = 500
        trans.interpolator = AccelerateDecelerateInterpolator()
        TransitionManager.beginDelayedTransition(constraintLayout, trans)
        set.applyTo(constraintLayout)
    }

    private fun adjustActiveSpeakerLayout() {
        if (conferenceViewModel.conference.value?.state == Conference.State.Created) {
            val participantsCount = conferenceViewModel.conferenceParticipantDevices.value.orEmpty().size
            Log.i("[Conference Call] Updating active speaker layout for [$participantsCount] participants")
            when (participantsCount) {
                1 -> switchToActiveSpeakerLayoutWhenAlone()
                2 -> switchToActiveSpeakerLayoutForTwoParticipants()
                else -> switchToActiveSpeakerLayoutForMoreThanTwoParticipants()
            }
        } else {
            Log.w("[Conference] Active speaker layout not adjusted, conference state is: ${conferenceViewModel.conference.value?.state}")
        }
    }

    private fun getConstraintSet(constraintLayout: ConstraintLayout): ConstraintSet {
        val set = ConstraintSet()
        set.clone(constraintLayout)

        set.clear(R.id.local_participant_background, ConstraintSet.TOP)
        set.clear(R.id.local_participant_background, ConstraintSet.START)
        set.clear(R.id.local_participant_background, ConstraintSet.LEFT)
        set.clear(R.id.local_participant_background, ConstraintSet.BOTTOM)
        set.clear(R.id.local_participant_background, ConstraintSet.END)
        set.clear(R.id.local_participant_background, ConstraintSet.RIGHT)

        return set
    }

    private fun switchToActiveSpeakerLayoutForMoreThanTwoParticipants() {
        if (conferenceViewModel.conferenceDisplayMode.value != ConferenceDisplayMode.ACTIVE_SPEAKER) return

        val constraintLayout =
            binding.root.findViewById<ConstraintLayout>(R.id.conference_constraint_layout)
                ?: return
        val set = getConstraintSet(constraintLayout)

        val margin = resources.getDimension(R.dimen.voip_active_speaker_miniature_margin).toInt()
        val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        if (portraitOrientation) {
            set.connect(
                R.id.local_participant_background,
                ConstraintSet.START,
                R.id.conference_constraint_layout,
                ConstraintSet.START,
                margin
            )
            set.connect(
                R.id.local_participant_background,
                ConstraintSet.BOTTOM,
                R.id.miniatures,
                ConstraintSet.BOTTOM,
                0
            )
            set.connect(
                R.id.local_participant_background,
                ConstraintSet.TOP,
                R.id.miniatures,
                ConstraintSet.TOP,
                0
            )
        } else {
            set.connect(
                R.id.local_participant_background,
                ConstraintSet.BOTTOM,
                R.id.hinge_bottom,
                ConstraintSet.BOTTOM,
                0
            )
            set.connect(
                R.id.local_participant_background,
                ConstraintSet.START,
                R.id.active_speaker_background,
                ConstraintSet.END,
                0
            )
            set.connect(
                R.id.local_participant_background,
                ConstraintSet.END,
                R.id.scroll_indicator,
                ConstraintSet.START,
                0
            )
        }

        val size = resources.getDimension(R.dimen.voip_active_speaker_miniature_size).toInt()
        set.constrainWidth(R.id.local_participant_background, size)
        set.constrainHeight(R.id.local_participant_background, size)

        val avatarSize = resources.getDimension(R.dimen.voip_conference_active_speaker_miniature_avatar_size).toInt()
        set.constrainWidth(R.id.local_participant_avatar, avatarSize)
        set.constrainHeight(R.id.local_participant_avatar, avatarSize)

        Log.i("[Conference Call] Updating active speaker layout for 3 or more participants")
        if (corePreferences.enableAnimations) {
            animateConstraintLayout(constraintLayout, set)
        } else {
            set.applyTo(constraintLayout)
        }
    }

    private fun switchToActiveSpeakerLayoutForTwoParticipants() {
        if (conferenceViewModel.conferenceDisplayMode.value != ConferenceDisplayMode.ACTIVE_SPEAKER) return

        val constraintLayout =
            binding.root.findViewById<ConstraintLayout>(R.id.conference_constraint_layout)
                ?: return
        val set = getConstraintSet(constraintLayout)

        val margin = resources.getDimension(R.dimen.voip_active_speaker_miniature_margin).toInt()
        set.connect(
            R.id.local_participant_background,
            ConstraintSet.BOTTOM,
            R.id.hinge_bottom,
            ConstraintSet.BOTTOM,
            margin
        )
        // Don't know why but if we use END instead of RIGHT, margin isn't applied...
        set.connect(
            R.id.local_participant_background,
            ConstraintSet.RIGHT,
            R.id.conference_constraint_layout,
            ConstraintSet.RIGHT,
            margin
        )

        val size = resources.getDimension(R.dimen.voip_active_speaker_miniature_size).toInt()
        set.constrainWidth(R.id.local_participant_background, size)
        set.constrainHeight(R.id.local_participant_background, size)

        val avatarSize = resources.getDimension(R.dimen.voip_conference_active_speaker_miniature_avatar_size).toInt()
        set.constrainWidth(R.id.local_participant_avatar, avatarSize)
        set.constrainHeight(R.id.local_participant_avatar, avatarSize)

        Log.i("[Conference Call] Updating active speaker layout for 2 participants")
        if (corePreferences.enableAnimations) {
            animateConstraintLayout(constraintLayout, set)
        } else {
            set.applyTo(constraintLayout)
        }
    }

    private fun switchToActiveSpeakerLayoutWhenAlone() {
        if (conferenceViewModel.conferenceDisplayMode.value != ConferenceDisplayMode.ACTIVE_SPEAKER) return

        val constraintLayout =
            binding.root.findViewById<ConstraintLayout>(R.id.conference_constraint_layout)
                ?: return
        val set = getConstraintSet(constraintLayout)

        set.connect(
            R.id.local_participant_background,
            ConstraintSet.BOTTOM,
            R.id.hinge_bottom,
            ConstraintSet.BOTTOM,
            0
        )
        set.connect(
            R.id.local_participant_background,
            ConstraintSet.END,
            R.id.conference_constraint_layout,
            ConstraintSet.END,
            0
        )
        set.connect(
            R.id.local_participant_background,
            ConstraintSet.TOP,
            R.id.top_barrier,
            ConstraintSet.BOTTOM,
            0
        )
        set.connect(
            R.id.local_participant_background,
            ConstraintSet.START,
            R.id.conference_constraint_layout,
            ConstraintSet.START,
            0
        )

        set.constrainWidth(R.id.local_participant_background, 0)
        set.constrainHeight(R.id.local_participant_background, 0)
        set.constrainWidth(R.id.local_participant_avatar, 0)
        set.constrainHeight(R.id.local_participant_avatar, 0)

        Log.i("[Conference Call] Updating active speaker layout for 1 participant (myself)")
        if (corePreferences.enableAnimations) {
            animateConstraintLayout(constraintLayout, set)
        } else {
            set.applyTo(constraintLayout)
        }
    }
}

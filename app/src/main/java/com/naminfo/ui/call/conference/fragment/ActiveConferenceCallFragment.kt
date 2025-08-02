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
package com.naminfo.ui.call.conference.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.window.layout.FoldingFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.CallActiveConferenceFragmentBinding
import com.naminfo.ui.call.CallActivity
import com.naminfo.ui.call.fragment.GenericCallFragment
import com.naminfo.ui.call.viewmodel.CallsViewModel
import com.naminfo.ui.call.viewmodel.CurrentCallViewModel
import com.naminfo.utils.Event
import com.naminfo.utils.startAnimatedDrawable

class ActiveConferenceCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Active Conference Call Fragment]"
    }

    private lateinit var binding: CallActiveConferenceFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    private lateinit var callsViewModel: CallsViewModel

    private val actionsBottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                val drawable = AnimatedVectorDrawableCompat.create(
                    requireContext(),
                    R.drawable.animated_handle_to_caret
                )
                binding.bottomBar.mainActions.callActionsHandle.setImageDrawable(drawable)
            } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                val drawable = AnimatedVectorDrawableCompat.create(
                    requireContext(),
                    R.drawable.animated_caret_to_handle
                )
                binding.bottomBar.mainActions.callActionsHandle.setImageDrawable(drawable)
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) { }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val actionsBottomSheetBehavior = BottomSheetBehavior.from(binding.bottomBar.root)
            if (actionsBottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return
            }

            val callStatsBottomSheetBehavior = BottomSheetBehavior.from(binding.callStats.root)
            if (callStatsBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return
            }

            val callMediaEncryptionStatsBottomSheetBehavior = BottomSheetBehavior.from(
                binding.callMediaEncryptionStats.root
            )
            if (callMediaEncryptionStatsBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return
            }

            Log.i("$TAG Back gesture/click detected, no bottom sheet is expanded, going back")
            isEnabled = false
            try {
                Log.i("$TAG Back gesture detected, going to MainActivity")
                (requireActivity() as CallActivity).goToMainActivity()
            } catch (ise: IllegalStateException) {
                Log.w("$TAG Can't go back: $ise")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallActiveConferenceFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        observeToastEvents(callViewModel)
        observeToastEvents(callViewModel.conferenceModel)

        callsViewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }
        observeToastEvents(callsViewModel)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel
        binding.conferenceViewModel = callViewModel.conferenceModel
        binding.callsViewModel = callsViewModel
        binding.numpadModel = callViewModel.numpadModel

        sharedViewModel.foldingState.observe(viewLifecycleOwner) { feature ->
            updateHingeRelatedConstraints(feature)
        }

        val actionsBottomSheetBehavior = BottomSheetBehavior.from(binding.bottomBar.root)
        actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        actionsBottomSheetBehavior.addBottomSheetCallback(actionsBottomSheetCallback)

        val callStatsBottomSheetBehavior = BottomSheetBehavior.from(binding.callStats.root)
        callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        callStatsBottomSheetBehavior.skipCollapsed = true

        val callMediaEncryptionStatsBottomSheetBehavior = BottomSheetBehavior.from(
            binding.callMediaEncryptionStats.root
        )
        callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        callMediaEncryptionStatsBottomSheetBehavior.skipCollapsed = true

        callViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.start()
        }

        callViewModel.toggleExtraActionsBottomSheetEvent.observe(viewLifecycleOwner) {
            it.consume {
                val state = actionsBottomSheetBehavior.state
                if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                    val drawable = AnimatedVectorDrawableCompat.create(
                        requireContext(),
                        R.drawable.animated_caret_to_handle
                    )
                    binding.bottomBar.mainActions.callActionsHandle.setImageDrawable(drawable)
                    binding.bottomBar.mainActions.callActionsHandle.startAnimatedDrawable()
                    actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                } else if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    val drawable = AnimatedVectorDrawableCompat.create(
                        requireContext(),
                        R.drawable.animated_handle_to_caret
                    )
                    binding.bottomBar.mainActions.callActionsHandle.setImageDrawable(drawable)
                    binding.bottomBar.mainActions.callActionsHandle.startAnimatedDrawable()
                    actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }

        callViewModel.conferenceModel.fullScreenMode.observe(viewLifecycleOwner) { hide ->
            Log.i("$TAG Switching full screen mode to [${if (hide) "ON" else "OFF"}]")
            sharedViewModel.toggleFullScreenEvent.value = Event(hide)
            callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            if (hide != callViewModel.fullScreenMode.value) {
                callViewModel.fullScreenMode.value = hide
            }
        }

        callViewModel.conferenceModel.conferenceLayout.observe(viewLifecycleOwner) { layout ->
            // Collapse bottom sheet after changing conference layout
            actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        callViewModel.conferenceModel.participants.observe(viewLifecycleOwner) { participants ->
            coreContext.postOnCoreThread { core ->
                if (participants.size == 1) {
                    Log.i("$TAG We are alone in that conference, using nativePreviewWindowId")
                    core.nativePreviewWindowId = binding.localPreviewVideoSurface

                    if (callViewModel.conferenceModel.fullScreenMode.value == true && callViewModel.conferenceModel.isMeParticipantSendingVideo.value == false) {
                        // Don't forget to leave full screen mode, otherwise we won't be able to leave it by touching video surface...
                        callViewModel.conferenceModel.fullScreenMode.postValue(false)
                    }
                }
            }
        }

        callViewModel.conferenceModel.firstParticipantOtherThanOurselvesJoinedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (callViewModel.conferenceModel.fullScreenMode.value == false) {
                    Log.i("$TAG First participant joined conference, switching to full screen mode")
                    callViewModel.conferenceModel.toggleFullScreen()
                }
            }
        }

        callViewModel.conferenceModel.goToConversationEvent.observe(viewLifecycleOwner) {
            it.consume { conversationId ->
                if (findNavController().currentDestination?.id == R.id.activeConferenceCallFragment) {
                    Log.i("$TAG Display conversation with conversation ID [$conversationId]")
                    val action =
                        ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToInCallConversationFragment(
                            conversationId
                        )
                    findNavController().navigate(action)
                }
            }
        }

        callViewModel.goToCallEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.activeConferenceCallFragment) {
                    Log.i("$TAG Going to active call fragment")
                    val action =
                        ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToActiveCallFragment()
                    findNavController().navigate(action)
                }
            }
        }

        binding.setBackClickListener {
            (requireActivity() as CallActivity).goToMainActivity()
        }

        binding.setCallsListClickListener {
            Log.i("$TAG Going to calls list fragment")
            if (findNavController().currentDestination?.id == R.id.activeConferenceCallFragment) {
                val action =
                    ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToCallsListFragment()
                findNavController().navigate(action)
            }
        }

        binding.setParticipantsListClickListener {
            Log.i("$TAG Going to conference participants list fragment")
            if (findNavController().currentDestination?.id == R.id.activeConferenceCallFragment) {
                val action =
                    ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToConferenceParticipantsListFragment()
                findNavController().navigate(action)
            }
        }

        binding.setShareConferenceClickListener {
            val sipUri = callViewModel.conferenceModel.sipUri.value.orEmpty()
            if (sipUri.isNotEmpty()) {
                Log.i("$TAG Sharing conference SIP URI [$sipUri]")

                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val label = "Conference SIP address"
                clipboard.setPrimaryClip(ClipData.newPlainText(label, sipUri))
            }
        }

        binding.setCallStatisticsClickListener {
            callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        binding.setCallMediaEncryptionStatisticsClickListener {
            callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
    }

    override fun onResume() {
        super.onResume()

        (binding.root as? ViewGroup)?.doOnLayout {
            setupVideoPreview(binding.localPreviewVideoSurface)
        }

        coreContext.postOnCoreThread {
            // Need to be done manually
            callViewModel.updateCallDuration()
        }
    }

    override fun onPause() {
        super.onPause()

        cleanVideoPreview(binding.localPreviewVideoSurface)
    }

    private fun updateHingeRelatedConstraints(feature: FoldingFeature) {
        Log.i("$TAG Updating constraint layout hinges: $feature")

        val constraintLayout = binding.constraintLayout
        val set = ConstraintSet()
        set.clone(constraintLayout)

        if (feature.isSeparating && feature.state == FoldingFeature.State.HALF_OPENED && feature.orientation == FoldingFeature.Orientation.HORIZONTAL) {
            set.setGuidelinePercent(R.id.hinge_top, 0.5f)
            set.setGuidelinePercent(R.id.hinge_bottom, 0.5f)
            callViewModel.halfOpenedFolded.value = true
        } else {
            set.setGuidelinePercent(R.id.hinge_top, 0f)
            set.setGuidelinePercent(R.id.hinge_bottom, 1f)
            callViewModel.halfOpenedFolded.value = false
        }

        set.applyTo(constraintLayout)
    }
}

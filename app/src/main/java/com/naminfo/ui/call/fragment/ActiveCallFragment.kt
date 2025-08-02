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
package com.naminfo.ui.call.fragment

import android.app.Dialog
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
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
import com.naminfo.databinding.CallActiveFragmentBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.call.CallActivity
import com.naminfo.ui.call.model.ZrtpAlertDialogModel
import com.naminfo.ui.call.model.ZrtpSasConfirmationDialogModel
import com.naminfo.ui.call.viewmodel.CallsViewModel
import com.naminfo.ui.call.viewmodel.CurrentCallViewModel
import com.naminfo.utils.DialogUtils
import com.naminfo.utils.Event
import com.naminfo.utils.addCharacterAtPosition
import com.naminfo.utils.removeCharacterAtPosition
import com.naminfo.utils.startAnimatedDrawable

@UiThread
class ActiveCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Active Call Fragment]"
    }

    private lateinit var binding: CallActiveFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    private lateinit var callsViewModel: CallsViewModel

    private var zrtpSasDialog: Dialog? = null

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

            val numpadBottomSheetBehavior = BottomSheetBehavior.from(binding.callNumpad.root)
            if (numpadBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                numpadBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
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
                Log.w(
                    "$TAG Can't go back: $ise"
                )
            }
        }
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        return when (findNavController().currentDestination?.id) {
            R.id.newCallFragment, R.id.callsListFragment, R.id.transferCallFragment, R.id.inCallConversationFragment -> {
                // Holds fragment in place while new fragment slides over it
                AnimationUtils.loadAnimation(activity, R.anim.hold)
            }
            else -> {
                super.onCreateAnimation(transit, enter, nextAnim)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallActiveFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        observeToastEvents(callViewModel)

        callsViewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }
        observeToastEvents(callsViewModel)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel
        binding.callsViewModel = callsViewModel
        binding.numpadModel = callViewModel.numpadModel

        val actionsBottomSheetBehavior = BottomSheetBehavior.from(binding.bottomBar.root)
        actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        actionsBottomSheetBehavior.addBottomSheetCallback(actionsBottomSheetCallback)

        val numpadBottomSheetBehavior = BottomSheetBehavior.from(binding.callNumpad.root)
        numpadBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        numpadBottomSheetBehavior.skipCollapsed = true

        val callStatsBottomSheetBehavior = BottomSheetBehavior.from(binding.callStats.root)
        callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        callStatsBottomSheetBehavior.skipCollapsed = true

        val callMediaEncryptionStatsBottomSheetBehavior = BottomSheetBehavior.from(
            binding.callMediaEncryptionStats.root
        )
        callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        callMediaEncryptionStatsBottomSheetBehavior.skipCollapsed = true

        binding.setBackClickListener {
            (requireActivity() as CallActivity).goToMainActivity()
        }

        binding.setTransferCallClickListener {
            if (findNavController().currentDestination?.id == R.id.activeCallFragment) {
                val action =
                    ActiveCallFragmentDirections.actionActiveCallFragmentToTransferCallFragment()
                findNavController().navigate(action)
            }
        }

        binding.setNewCallClickListener {
            if (findNavController().currentDestination?.id == R.id.activeCallFragment) {
                val action =
                    ActiveCallFragmentDirections.actionActiveCallFragmentToNewCallFragment()
                findNavController().navigate(action)
            }
        }

        binding.setCallsListClickListener {
            if (findNavController().currentDestination?.id == R.id.activeCallFragment) {
                val action =
                    ActiveCallFragmentDirections.actionActiveCallFragmentToCallsListFragment()
                findNavController().navigate(action)
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

        sharedViewModel.foldingState.observe(viewLifecycleOwner) { feature ->
            updateHingeRelatedConstraints(feature)
        }

        callViewModel.fullScreenMode.observe(viewLifecycleOwner) { hide ->
            Log.i("$TAG Switching full screen mode to [${if (hide) "ON" else "OFF"}]")
            sharedViewModel.toggleFullScreenEvent.value = Event(hide)
            numpadBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            callStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        callViewModel.showZrtpSasDialogEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                showZrtpSasValidationDialog(pair.first, pair.second, false)
            }
        }

        callViewModel.showZrtpSasCacheMismatchDialogEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                callMediaEncryptionStatsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                showZrtpSasValidationDialog(pair.first, pair.second, true)
            }
        }

        callViewModel.zrtpAuthTokenVerifiedEvent.observe(viewLifecycleOwner) {
            it.consume { verified ->
                if (verified) {
                    (requireActivity() as GenericActivity).showBlueToast(
                        getString(R.string.call_can_be_trusted_toast),
                        R.drawable.trusted,
                        doNotTint = true
                    )
                } else {
                    // Only allow "trying again" once
                    showZrtpAlertDialog()
                }
            }
        }

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

        callViewModel.showNumpadBottomSheetEvent.observe(viewLifecycleOwner) {
            it.consume {
                actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                numpadBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        callViewModel.removedCharacterAtCurrentPositionEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.callNumpad.digitsHistory.removeCharacterAtPosition()
            }
        }

        callViewModel.appendDigitToSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { digit ->
                binding.callNumpad.digitsHistory.addCharacterAtPosition(digit)
            }
        }

        callViewModel.isRemoteRecordingEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val isRemoteRecording = pair.first
                val displayName = pair.second
                val toastTag = "REMOTE_RECORDING"

                if (isRemoteRecording) {
                    Log.i("$TAG Showing [$displayName] is recording toast")
                    val message = getString(R.string.call_remote_is_recording, displayName)
                    (requireActivity() as GenericActivity).showPersistentRedToast(
                        message,
                        R.drawable.record_fill,
                        toastTag
                    )
                } else {
                    Log.i("$TAG Removing [$displayName] is recording toast")
                    (requireActivity() as CallActivity).removePersistentRedToast(toastTag)
                }
            }
        }

        callViewModel.goToConferenceEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.activeCallFragment) {
                    Log.i("$TAG Going to conference fragment")
                    val action =
                        ActiveCallFragmentDirections.actionActiveCallFragmentToActiveConferenceCallFragment()
                    findNavController().navigate(action)
                }
            }
        }

        callViewModel.isReceivingVideo.observe(viewLifecycleOwner) { receiving ->
            if (!receiving && callViewModel.fullScreenMode.value == true) {
                Log.i("$TAG We are no longer receiving video, leaving full screen mode")
                callViewModel.fullScreenMode.value = false
            }
        }

        callViewModel.isSendingVideo.observe(viewLifecycleOwner) { sending ->
            coreContext.postOnCoreThread { core ->
                core.nativePreviewWindowId = if (sending) {
                    Log.i("$TAG We are sending video, setting capture preview surface")
                    binding.localPreviewVideoSurface
                } else {
                    Log.i("$TAG We are not sending video, clearing capture preview surface")
                    null
                }
            }
        }

        callViewModel.chatRoomCreationErrorEvent.observe(viewLifecycleOwner) {
            it.consume { error ->
                (requireActivity() as GenericActivity).showRedToast(
                    getString(error),
                    R.drawable.warning_circle
                )
            }
        }

        callViewModel.goToConversationEvent.observe(viewLifecycleOwner) {
            it.consume { conversationId ->
                if (findNavController().currentDestination?.id == R.id.activeCallFragment) {
                    Log.i(
                        "$TAG Display conversation with conversation ID [$conversationId]"
                    )
                    val action =
                        ActiveCallFragmentDirections.actionActiveCallFragmentToInCallConversationFragment(
                            conversationId
                        )
                    findNavController().navigate(action)
                }
            }
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

        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Fragment resuming, setting native video window ID")
            core.nativeVideoWindowId = binding.remoteVideoSurface

            // Need to be done manually
            callViewModel.updateCallDuration()
        }

        if (callViewModel.isZrtpAlertDialogVisible) {
            Log.i("$TAG Fragment resuming, showing ZRTP alert dialog")
            showZrtpAlertDialog()
        } else if (callViewModel.isZrtpDialogVisible) {
            Log.i("$TAG Fragment resuming, showing ZRTP SAS validation dialog")
            callViewModel.showZrtpSasDialogIfPossible()
        }
    }

    override fun onPause() {
        super.onPause()

        zrtpSasDialog?.dismiss()
        zrtpSasDialog = null

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

    private fun showZrtpSasValidationDialog(
        authTokenToRead: String,
        authTokensToListen: List<String>,
        cacheMismatch: Boolean
    ) {
        if (zrtpSasDialog != null) {
            zrtpSasDialog?.dismiss()
        }

        val model = ZrtpSasConfirmationDialogModel(
            authTokenToRead,
            authTokensToListen,
            cacheMismatch
        )
        val dialog = DialogUtils.getZrtpSasConfirmationDialog(requireActivity(), model)

        model.skipEvent.observe(viewLifecycleOwner) { event ->
            event.consume {
                callViewModel.skipZrtpSas()
                dialog.dismiss()
                callViewModel.isZrtpDialogVisible = false
            }
        }

        model.authTokenClickedEvent.observe(viewLifecycleOwner) { event ->
            event.consume { authToken ->
                callViewModel.updateZrtpSas(authToken)
                dialog.dismiss()
                callViewModel.isZrtpDialogVisible = false
            }
        }

        dialog.show()
        zrtpSasDialog = dialog
        callViewModel.isZrtpDialogVisible = true
    }

    private fun showZrtpAlertDialog() {
        if (zrtpSasDialog != null) {
            zrtpSasDialog?.dismiss()
        }

        val model = ZrtpAlertDialogModel(false)
        val dialog = DialogUtils.getZrtpAlertDialog(requireActivity(), model)

        model.tryAgainEvent.observe(viewLifecycleOwner) { event ->
            event.consume {
                callViewModel.showZrtpSasDialogIfPossible()
                dialog.dismiss()
                callViewModel.isZrtpAlertDialogVisible = false
            }
        }

        model.hangUpEvent.observe(viewLifecycleOwner) { event ->
            event.consume {
                callViewModel.hangUp()
                dialog.dismiss()
                callViewModel.isZrtpAlertDialogVisible = false
            }
        }

        dialog.show()
        zrtpSasDialog = dialog
        callViewModel.isZrtpDialogVisible = false
        callViewModel.isZrtpAlertDialogVisible = true
    }
}

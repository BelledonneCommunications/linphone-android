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
package org.linphone.ui.voip.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.window.layout.FoldingFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipActiveCallFragmentBinding
import org.linphone.ui.voip.VoipActivity
import org.linphone.ui.voip.model.ZrtpSasConfirmationDialogModel
import org.linphone.ui.voip.viewmodel.CurrentCallViewModel
import org.linphone.ui.voip.viewmodel.SharedCallViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class ActiveCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Active Call Fragment]"
    }

    private lateinit var binding: VoipActiveCallFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    // For moving video preview purposes

    private var previewX: Float = 0f
    private var previewY: Float = 0f

    private val previewTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previewX = view.x - event.rawX
                previewY = view.y - event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + previewX)
                    .y(event.rawY + previewY)
                    .setDuration(0)
                    .start()
                true
            }
            else -> {
                view.performClick()
                false
            }
        }
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.newCallFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return AnimationUtils.loadAnimation(activity, R.anim.hold)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = VoipActiveCallFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomBar.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.setNewCallClickListener {
            val action = ActiveCallFragmentDirections.actionActiveCallFragmentToNewCallFragment()
            findNavController().navigate(action)
        }

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedCallViewModel::class.java]
        }

        sharedViewModel.foldingState.observe(viewLifecycleOwner) { feature ->
            updateHingeRelatedConstraints(feature)
        }

        callViewModel.fullScreenMode.observe(viewLifecycleOwner) { hide ->
            Log.i("$TAG Switching full screen mode to ${if (hide) "ON" else "OFF"}")
            sharedViewModel.toggleFullScreenEvent.value = Event(hide)
        }

        callViewModel.isRemoteDeviceTrusted.observe(viewLifecycleOwner) { trusted ->
            if (trusted) {
                (requireActivity() as VoipActivity).showBlueToast(
                    getString(R.string.toast_call_can_be_trusted),
                    R.drawable.trusted
                )
                binding.avatar.avatarBorderWidth = AppUtils.getDimension(
                    R.dimen.avatar_trust_border_width
                ).toInt()
            }
        }

        callViewModel.showZrtpSasDialogEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val model = ZrtpSasConfirmationDialogModel(pair.first, pair.second)
                val dialog = DialogUtils.getZrtpSasConfirmationDialog(requireActivity(), model)

                model.dismissEvent.observe(viewLifecycleOwner) { event ->
                    event.consume {
                        dialog.dismiss()
                    }
                }

                model.trustVerified.observe(viewLifecycleOwner) { event ->
                    event.consume { verified ->
                        callViewModel.updateZrtpSas(verified)
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }
        }

        callViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.start()
        }

        callViewModel.toggleExtraActionsBottomSheetEvent.observe(viewLifecycleOwner) {
            it.consume {
                val state = bottomSheetBehavior.state
                if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                } else if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED, BottomSheetBehavior.STATE_HIDDEN -> {
                        callViewModel.isActionsMenuExpanded.value = false
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        callViewModel.isActionsMenuExpanded.value = true
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onResume() {
        super.onResume()

        coreContext.postOnCoreThread { core ->
            core.nativeVideoWindowId = binding.remoteVideoSurface
            coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface
            binding.localPreviewVideoSurface.setOnTouchListener(previewTouchListener)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onPause() {
        super.onPause()
        binding.localPreviewVideoSurface.setOnTouchListener(null)
    }

    private fun updateHingeRelatedConstraints(feature: FoldingFeature) {
        Log.i("$TAG Updating constraint layout hinges: $feature")

        val constraintLayout = binding.constraintLayout
        val set = ConstraintSet()
        set.clone(constraintLayout)

        if (feature.state == FoldingFeature.State.HALF_OPENED) {
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

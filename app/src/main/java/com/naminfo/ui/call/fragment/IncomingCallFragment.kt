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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.CallIncomingFragmentBinding
import com.naminfo.ui.call.viewmodel.CurrentCallViewModel
import com.naminfo.utils.AppUtils
import kotlin.math.max
import kotlin.math.min

@UiThread
class IncomingCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Incoming Call Fragment]"
    }

    private lateinit var binding: CallIncomingFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    private val marginSize = AppUtils.getDimension(R.dimen.sliding_accept_decline_call_margin)
    private val areaSize = AppUtils.getDimension(R.dimen.call_button_size) + marginSize
    private var initialX = 0f
    private var slidingButtonX = 0f
    private val slidingButtonTouchListener = View.OnTouchListener { view, event ->
        val width = binding.bottomBar.lockedScreenBottomBar.root.width.toFloat()
        val aboveAnswer = view.x + view.width > width - areaSize
        val aboveDecline = view.x < areaSize

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (initialX == 0f) {
                    initialX = view.x
                }
                slidingButtonX = view.x - event.rawX
                true
            }
            MotionEvent.ACTION_UP -> {
                if (aboveAnswer) {
                    // Accept
                    callViewModel.answer()
                } else if (aboveDecline) {
                    // Decline
                    callViewModel.hangUp()
                } else {
                    // Animate going back to initial position
                    view.animate()
                        .x(initialX)
                        .setDuration(500)
                        .start()
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(min(max(marginSize, event.rawX + slidingButtonX), width - view.width - marginSize))
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallIncomingFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        observeToastEvents(callViewModel)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel

        callViewModel.isIncomingEarlyMedia.observe(viewLifecycleOwner) { earlyMedia ->
            if (earlyMedia) {
                coreContext.postOnCoreThread { core ->
                    Log.i("$TAG Incoming early-media call, setting video surface")
                    core.nativeVideoWindowId = binding.remoteVideoSurface
                }
            }
        }

        binding.bottomBar.lockedScreenBottomBar.slidingButton.setOnTouchListener(slidingButtonTouchListener)
    }

    override fun onResume() {
        super.onResume()

        callViewModel.refreshKeyguardLockedStatus()
        coreContext.notificationsManager.setIncomingCallFragmentCurrentlyDisplayed(true)
    }

    override fun onPause() {
        coreContext.notificationsManager.setIncomingCallFragmentCurrentlyDisplayed(false)

        super.onPause()
    }
}

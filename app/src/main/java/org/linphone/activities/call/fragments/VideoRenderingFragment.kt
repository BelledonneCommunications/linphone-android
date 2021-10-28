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
package org.linphone.activities.call.fragments

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.call.VideoZoomHelper
import org.linphone.activities.call.viewmodels.CallsViewModel
import org.linphone.activities.call.viewmodels.ConferenceViewModel
import org.linphone.activities.call.viewmodels.ControlsFadingViewModel
import org.linphone.databinding.CallVideoFragmentBinding

class VideoRenderingFragment : GenericFragment<CallVideoFragmentBinding>() {
    private lateinit var controlsFadingViewModel: ControlsFadingViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var conferenceViewModel: ConferenceViewModel

    private var previewX: Float = 0f
    private var previewY: Float = 0f
    private lateinit var videoZoomHelper: VideoZoomHelper

    override fun getLayoutId(): Int = R.layout.call_video_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        controlsFadingViewModel = requireActivity().run {
            ViewModelProvider(this)[ControlsFadingViewModel::class.java]
        }
        binding.controlsFadingViewModel = controlsFadingViewModel

        callsViewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        conferenceViewModel = requireActivity().run {
            ViewModelProvider(this)[ConferenceViewModel::class.java]
        }
        binding.conferenceViewModel = conferenceViewModel

        coreContext.core.nativeVideoWindowId = binding.remoteVideoSurface
        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface

        binding.setPreviewTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previewX = v.x - event.rawX
                    previewY = v.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + previewX)
                        .y(event.rawY + previewY)
                        .setDuration(0)
                        .start()
                }
                else -> {
                    v.performClick()
                    false
                }
            }
            true
        }

        videoZoomHelper = VideoZoomHelper(requireContext(), binding.remoteVideoSurface)
    }
}

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

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.navigateToCallsList
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.activities.voip.views.RoundCornersTextureView
import org.linphone.databinding.VoipCurrentFragmentBindingImpl
import org.linphone.mediastream.video.capture.CaptureTextureView

class CurrentFragment : GenericFragment<VoipCurrentFragmentBindingImpl>() {
    private lateinit var controlsViewModel: ControlsViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var conferenceViewModel: ConferenceViewModel

    override fun getLayoutId(): Int = R.layout.voip_current_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

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

        callsViewModel.noMoreCallEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    requireActivity().finish()
                }
            }
        )

        controlsViewModel.goToCallsListEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    controlsViewModel.hideExtraButtons()
                    navigateToCallsList()
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

    override fun onDestroy() {
        super.onDestroy()

        coreContext.core.nativeVideoWindowId = null
        coreContext.core.nativePreviewWindowId = null
    }
}

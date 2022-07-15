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
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.voip.ConferenceDisplayMode
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.databinding.VoipConferenceLayoutFragmentBinding

class ConferenceLayoutFragment : GenericVideoPreviewFragment<VoipConferenceLayoutFragmentBinding>() {
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)

    override fun getLayoutId(): Int = R.layout.voip_conference_layout_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.conferenceViewModel = conferenceViewModel

        binding.controlsViewModel = controlsViewModel

        setupLocalViewPreview(binding.localPreviewVideoSurface, binding.switchCamera)

        binding.setCancelClickListener {
            goBack()
        }

        conferenceViewModel.conferenceParticipantDevices.observe(
            viewLifecycleOwner
        ) {
            if (it.size > conferenceViewModel.maxParticipantsForMosaicLayout && conferenceViewModel.conferenceDisplayMode.value == ConferenceDisplayMode.GRID) {
                showTooManyParticipantsForMosaicLayoutDialog()
            }
        }

        conferenceViewModel.conferenceDisplayMode.observe(
            viewLifecycleOwner
        ) {
            binding.localPreviewVideoSurface.visibility = if (it == ConferenceDisplayMode.AUDIO_ONLY) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        binding.setDismissDialogClickListener {
            val dialog = binding.root.findViewById<ConstraintLayout>(R.id.too_many_participants_dialog)
            dialog?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()

        if (conferenceViewModel.conferenceParticipantDevices.value.orEmpty().size > conferenceViewModel.maxParticipantsForMosaicLayout) {
            showTooManyParticipantsForMosaicLayoutDialog()
        }

        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface
    }

    private fun showTooManyParticipantsForMosaicLayoutDialog() {
        val dialog = binding.root.findViewById<ConstraintLayout>(R.id.too_many_participants_dialog)
        dialog?.visibility = View.VISIBLE
    }
}

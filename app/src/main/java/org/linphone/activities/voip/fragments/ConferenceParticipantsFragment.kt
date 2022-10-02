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
import android.widget.Toast
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.navigateToAddParticipants
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipConferenceParticipantsFragmentBinding

class ConferenceParticipantsFragment : GenericVideoPreviewFragment<VoipConferenceParticipantsFragmentBinding>() {
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)

    // Only display events happening during while this fragment is visible
    private var skipEvents = true

    override fun getLayoutId(): Int = R.layout.voip_conference_participants_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.conferenceViewModel = conferenceViewModel

        binding.controlsViewModel = controlsViewModel

        setupLocalViewPreview(binding.localPreviewVideoSurface, binding.switchCamera)

        conferenceViewModel.conferenceExists.observe(
            viewLifecycleOwner
        ) { exists ->
            if (!exists) {
                Log.w("[Conference Participants] Conference no longer exists, going back")
                goBack()
            }
        }

        conferenceViewModel.participantAdminStatusChangedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { participantData ->
                val participantName =
                    participantData.contact.value?.name ?: participantData.displayName.value
                val message = if (participantData.participant.isAdmin) {
                    getString(R.string.conference_admin_set).format(participantName)
                } else {
                    getString(R.string.conference_admin_unset).format(participantName)
                }
                if (!skipEvents) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.setCancelClickListener {
            goBack()
        }

        binding.setEditClickListener {
            navigateToAddParticipants()
        }
    }

    override fun onResume() {
        super.onResume()

        skipEvents = false
        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface
    }

    override fun onPause() {
        super.onPause()

        skipEvents = true
    }
}

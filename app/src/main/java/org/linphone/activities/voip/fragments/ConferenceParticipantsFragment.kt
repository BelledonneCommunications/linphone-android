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
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipConferenceParticipantsFragmentBinding

class ConferenceParticipantsFragment : GenericFragment<VoipConferenceParticipantsFragmentBinding>() {
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)

    override fun getLayoutId(): Int = R.layout.voip_conference_participants_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.callsViewModel = callsViewModel

        binding.conferenceViewModel = conferenceViewModel

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
                    participantData.contact.value?.fullName ?: participantData.displayName.value
                val message = if (participantData.participant.isAdmin) {
                    getString(R.string.conference_admin_set).format(participantName)
                } else {
                    getString(R.string.conference_admin_unset).format(participantName)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        binding.setCancelClickListener {
            goBack()
        }

        binding.setEditClickListener {
            // TODO: go to conferences view outside of call activity in edition mode
        }
    }
}

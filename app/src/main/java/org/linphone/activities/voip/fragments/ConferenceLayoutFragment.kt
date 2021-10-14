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
import android.widget.LinearLayout
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.core.ConferenceLayout
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipConferenceLayoutFragmentBinding

class ConferenceLayoutFragment : GenericFragment<VoipConferenceLayoutFragmentBinding>() {
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)

    override fun getLayoutId(): Int = R.layout.voip_conference_layout_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.conferenceViewModel = conferenceViewModel

        binding.setCancelClickListener {
            goBack()
        }

        conferenceViewModel.conferenceMosaicDisplayMode.observe(
            viewLifecycleOwner,
            {
                if (it) {
                    Log.i("[Conference] Trying to change conference layout to Grid")
                    val conference = conferenceViewModel.conference.value
                    if (conference != null) {
                        conference.layout = ConferenceLayout.Grid
                    } else {
                        Log.e("[Conference] Conference is null in ConferenceViewModel")
                    }
                }
            }
        )

        conferenceViewModel.conferenceActiveSpeakerDisplayMode.observe(
            viewLifecycleOwner,
            {
                if (it) {
                    Log.i("[Conference] Trying to change conference layout to ActiveSpeaker")
                    val conference = conferenceViewModel.conference.value
                    if (conference != null) {
                        conference.layout = ConferenceLayout.ActiveSpeaker
                    } else {
                        Log.e("[Conference] Conference is null in ConferenceViewModel")
                    }
                }
            }
        )

        binding.setDismissDialogClickListener {
            val dialog = binding.root.findViewById<LinearLayout>(R.id.too_many_participants_dialog)
            dialog?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()

        if (conferenceViewModel.conferenceParticipantDevices.value.orEmpty().size > corePreferences.maxConferenceParticipantsForMosaicLayout) {
            val dialog = binding.root.findViewById<LinearLayout>(R.id.too_many_participants_dialog)
            dialog?.visibility = View.VISIBLE
        }
    }
}

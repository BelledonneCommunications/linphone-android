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
package org.linphone.activities.main.conference.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.goBackToDialer
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.conference.viewmodels.ConferenceSchedulingViewModel
import org.linphone.databinding.ConferenceSchedulingSummaryFragmentBinding

class ConferenceSchedulingSummaryFragment : GenericFragment<ConferenceSchedulingSummaryFragmentBinding>() {
    private val viewModel: ConferenceSchedulingViewModel by navGraphViewModels(R.id.conference_scheduling_nav_graph)

    override fun getLayoutId(): Int = R.layout.conference_scheduling_summary_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        binding.setCreateConferenceClickListener {
            viewModel.createConference()
        }

        viewModel.conferenceCreationCompletedEvent.observe(
            viewLifecycleOwner,
            {
                goBackToDialer()
            }
        )

        viewModel.copyToClipboardEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { address ->
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Conference address", address)
                    clipboard.setPrimaryClip(clip)

                    val activity = requireActivity() as MainActivity
                    activity.showSnackBar(R.string.conference_schedule_address_copied_to_clipboard)
                }
            }
        )

        viewModel.computeParticipantsData()
    }
}

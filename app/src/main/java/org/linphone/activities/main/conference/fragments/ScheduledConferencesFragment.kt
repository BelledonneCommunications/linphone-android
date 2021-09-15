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

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.conference.adapters.ScheduledConferencesAdapter
import org.linphone.activities.main.conference.viewmodels.ScheduledConferencesViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToConferenceScheduling
import org.linphone.activities.navigateToConferenceWaitingRoom
import org.linphone.databinding.ConferencesScheduledFragmentBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

class ScheduledConferencesFragment : GenericFragment<ConferencesScheduledFragmentBinding>() {
    private lateinit var viewModel: ScheduledConferencesViewModel
    private lateinit var adapter: ScheduledConferencesAdapter

    override fun getLayoutId(): Int = R.layout.conferences_scheduled_fragment

    private var deleteConferenceInfoDialog: Dialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(
            this
        )[ScheduledConferencesViewModel::class.java]
        binding.viewModel = viewModel

        adapter = ScheduledConferencesAdapter(
            viewLifecycleOwner
        )
        binding.conferenceInfoList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        binding.conferenceInfoList.layoutManager = layoutManager

        // Displays date header
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.conferenceInfoList.addItemDecoration(headerItemDecoration)

        viewModel.conferences.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)
        }

        adapter.copyAddressToClipboardEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { address ->
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Conference address", address.asStringUriOnly())
                clipboard.setPrimaryClip(clip)

                (activity as MainActivity).showSnackBar(R.string.conference_schedule_address_copied_to_clipboard)
            }
        }

        adapter.joinConferenceEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { pair ->
                navigateToConferenceWaitingRoom(pair.first, pair.second)
            }
        }

        adapter.deleteConferenceInfoEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { data ->
                val dialogViewModel =
                    DialogViewModel(AppUtils.getString(R.string.conference_info_confirm_removal))
                deleteConferenceInfoDialog =
                    DialogUtils.getVoipDialog(requireContext(), dialogViewModel)

                dialogViewModel.showCancelButton(
                    {
                        deleteConferenceInfoDialog?.dismiss()
                    },
                    getString(R.string.dialog_cancel)
                )

                dialogViewModel.showDeleteButton(
                    {
                        viewModel.deleteConferenceInfo(data)
                        deleteConferenceInfoDialog?.dismiss()
                        (requireActivity() as MainActivity).showSnackBar(R.string.conference_info_removed)
                    },
                    getString(R.string.dialog_delete)
                )

                deleteConferenceInfoDialog?.show()
            }
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setNewConferenceClickListener {
            navigateToConferenceScheduling()
        }
    }
}

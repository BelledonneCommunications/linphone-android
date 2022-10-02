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

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.voip.viewmodels.ConferenceParticipantsViewModel
import org.linphone.activities.voip.viewmodels.ConferenceParticipantsViewModelFactory
import org.linphone.activities.voip.viewmodels.ConferenceViewModel
import org.linphone.contact.ContactsSelectionAdapter
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipConferenceParticipantsAddFragmentBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.PermissionHelper

class ConferenceAddParticipantsFragment : GenericFragment<VoipConferenceParticipantsAddFragmentBinding>() {
    private val conferenceViewModel: ConferenceViewModel by navGraphViewModels(R.id.call_nav_graph)
    private lateinit var viewModel: ConferenceParticipantsViewModel
    private lateinit var adapter: ContactsSelectionAdapter

    override fun getLayoutId(): Int = R.layout.voip_conference_participants_add_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        val conference = conferenceViewModel.conference.value
        conference ?: return

        viewModel = ViewModelProvider(
            this,
            ConferenceParticipantsViewModelFactory(conference)
        )[ConferenceParticipantsViewModel::class.java]

        binding.viewModel = viewModel

        adapter = ContactsSelectionAdapter(viewLifecycleOwner)
        adapter.setLimeCapabilityRequired(false) // TODO: Use right value from conference
        binding.contactsList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.layoutManager = layoutManager

        // Divider between items
        binding.contactsList.addItemDecoration(AppUtils.getDividerDecoration(requireContext(), layoutManager))

        binding.setApplyClickListener {
            viewModel.applyChanges()
            goBack()
        }

        viewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)
        }
        viewModel.sipContactsSelected.observe(
            viewLifecycleOwner
        ) {
            viewModel.applyFilter()
        }

        viewModel.selectedAddresses.observe(
            viewLifecycleOwner
        ) {
            adapter.updateSelectedAddresses(it)
        }
        viewModel.filter.observe(
            viewLifecycleOwner
        ) {
            viewModel.applyFilter()
        }

        adapter.selectedContact.observe(
            viewLifecycleOwner
        ) {
            it.consume { searchResult ->
                viewModel.toggleSelectionForSearchResult(searchResult)
            }
        }

        if (corePreferences.enableNativeAddressBookIntegration) {
            if (!PermissionHelper.get().hasReadContactsPermission()) {
                Log.i("[Conference Add Participants] Asking for READ_CONTACTS permission")
                requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 0)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Conference Add Participants] READ_CONTACTS permission granted")
                LinphoneApplication.coreContext.fetchContacts()
            } else {
                Log.w("[Conference Add Participants] READ_CONTACTS permission denied")
            }
        }
    }
}

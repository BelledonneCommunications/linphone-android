/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.settings.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.settings.viewmodels.ContactsSettingsViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsContactsFragmentBinding
import org.linphone.utils.PermissionHelper

class ContactsSettingsFragment : GenericFragment<SettingsContactsFragmentBinding>() {
    private lateinit var viewModel: ContactsSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_contacts_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(ContactsSettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { findNavController().popBackStack() }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

        viewModel.launcherShortcutsEvent.observe(viewLifecycleOwner, {
            it.consume { newValue ->
                if (newValue) {
                    Compatibility.createShortcutsToContacts(requireContext())
                } else {
                    Compatibility.removeShortcuts(requireContext())
                    if (corePreferences.chatRoomShortcuts) {
                        Compatibility.createShortcutsToChatRooms(requireContext())
                    }
                }
            }
        })

        viewModel.askWriteContactsPermissionForPresenceStorageEvent.observe(viewLifecycleOwner, {
            it.consume {
                Log.i("[Contacts Settings] Asking for WRITE_CONTACTS permission to be able to store presence")
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), 1)
            }
        })

        if (!PermissionHelper.required(requireContext()).hasReadContactsPermission()) {
            Log.i("[Contacts Settings] Asking for READ_CONTACTS permission")
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            0 -> {
                val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    Log.i("[Contacts Settings] READ_CONTACTS permission granted")
                    viewModel.readContactsPermissionGranted.value = true
                    coreContext.contactsManager.onReadContactsPermissionGranted()
                    coreContext.contactsManager.fetchContactsAsync()
                } else {
                    Log.w("[Contacts Settings] READ_CONTACTS permission denied")
                }
            }
            1 -> {
                val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    Log.i("[Contacts Settings] WRITE_CONTACTS permission granted")
                    corePreferences.storePresenceInNativeContact = true
                    coreContext.contactsManager.storePresenceInformationForAllContacts()
                } else {
                    Log.w("[Contacts Settings] WRITE_CONTACTS permission denied")
                }
            }
        }
    }
}

/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.settings.viewmodels.LdapSettingsViewModel
import org.linphone.activities.main.settings.viewmodels.LdapSettingsViewModelFactory
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsLdapFragmentBinding

class LdapSettingsFragment : GenericSettingFragment<SettingsLdapFragmentBinding>() {
    private lateinit var viewModel: LdapSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_ldap_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.sharedMainViewModel = sharedViewModel

        val configIndex = arguments?.getInt("LdapConfigIndex")
        if (configIndex == null) {
            Log.e("[LDAP Settings] Config index not specified!")
            goBack()
            return
        }

        try {
            viewModel = ViewModelProvider(this, LdapSettingsViewModelFactory(configIndex))[LdapSettingsViewModel::class.java]
        } catch (nsee: NoSuchElementException) {
            Log.e("[LDAP Settings] Failed to find LDAP object, aborting!")
            goBack()
            return
        }
        binding.viewModel = viewModel

        viewModel.ldapConfigDeletedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                goBack()
            }
        }
    }
}

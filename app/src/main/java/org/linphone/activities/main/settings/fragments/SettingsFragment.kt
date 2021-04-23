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

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.settings.viewmodels.SettingsViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToAccountSettings
import org.linphone.activities.navigateToAudioSettings
import org.linphone.activities.navigateToTunnelSettings
import org.linphone.activities.navigateToVideoSettings
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsFragmentBinding

class SettingsFragment : SecureFragment<SettingsFragmentBinding>() {
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var viewModel: SettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { findNavController().popBackStack() }

        sharedViewModel.accountRemoved.observe(viewLifecycleOwner, {
            Log.i("[Settings] Account removed, update accounts list")
            viewModel.updateAccountsList()
        })

        val identity = arguments?.getString("Identity")
        if (identity != null) {
            Log.i("[Settings] Found identity parameter in arguments: $identity")
            arguments?.clear()
            navigateToAccountSettings(identity)
        }

        viewModel.accountsSettingsListener = object : SettingListenerStub() {
            override fun onAccountClicked(identity: String) {
                Log.i("[Settings] Navigation to settings for account with identity: $identity")
                navigateToAccountSettings(identity)
            }
        }

        viewModel.tunnelSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToTunnelSettings()
            }
        }

        viewModel.audioSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToAudioSettings()
            }
        }

        viewModel.videoSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToVideoSettings()
            }
        }

        viewModel.callSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToCallSettings()
            }
        }

        viewModel.chatSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToChatSettings()
            }
        }

        viewModel.networkSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToNetworkSettings()
            }
        }

        viewModel.contactsSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToContactsSettings()
            }
        }

        viewModel.advancedSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToAdvancedSettings()
            }
        }
    }
}

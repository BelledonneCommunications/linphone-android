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
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.slidingpanelayout.widget.SlidingPaneLayout
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

        /* Shared view model & sliding pane related */

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        view.doOnPreDraw { sharedViewModel.canSlidingPaneBeClosed.value = binding.slidingPane.isSlideable }

        sharedViewModel.closeSlidingPaneEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (!binding.slidingPane.closePane()) {
                    goBack()
                }
            }
        })
        sharedViewModel.layoutChangedEvent.observe(viewLifecycleOwner, {
            it.consume {
                sharedViewModel.canSlidingPaneBeClosed.value = binding.slidingPane.isSlideable
            }
        })
        binding.slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        /* End of hared view model & sliding pane related */

        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { goBack() }

        sharedViewModel.accountRemoved.observe(viewLifecycleOwner, {
            Log.i("[Settings] Account removed, update accounts list")
            viewModel.updateAccountsList()
        })

        val identity = arguments?.getString("Identity")
        if (identity != null) {
            Log.i("[Settings] Found identity parameter in arguments: $identity")
            arguments?.clear()
            binding.slidingPane.openPane()
            navigateToAccountSettings(identity)
        }

        viewModel.accountsSettingsListener = object : SettingListenerStub() {
            override fun onAccountClicked(identity: String) {
                Log.i("[Settings] Navigation to settings for account with identity: $identity")
                binding.slidingPane.openPane()
                navigateToAccountSettings(identity)
            }
        }

        viewModel.tunnelSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToTunnelSettings()
            }
        }

        viewModel.audioSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToAudioSettings()
            }
        }

        viewModel.videoSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToVideoSettings()
            }
        }

        viewModel.callSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToCallSettings()
            }
        }

        viewModel.chatSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToChatSettings()
            }
        }

        viewModel.networkSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToNetworkSettings()
            }
        }

        viewModel.contactsSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToContactsSettings()
            }
        }

        viewModel.advancedSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                binding.slidingPane.openPane()
                navigateToAdvancedSettings()
            }
        }
    }
}

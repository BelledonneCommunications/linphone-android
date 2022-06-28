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
import androidx.navigation.fragment.NavHostFragment
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.transition.MaterialSharedAxis
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.settings.viewmodels.SettingsViewModel
import org.linphone.activities.navigateToAccountSettings
import org.linphone.activities.navigateToAudioSettings
import org.linphone.activities.navigateToTunnelSettings
import org.linphone.activities.navigateToVideoSettings
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsFragmentBinding

class SettingsFragment : SecureFragment<SettingsFragmentBinding>() {
    private lateinit var viewModel: SettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        useMaterialSharedAxisXForwardAnimation = false
        if (corePreferences.enableAnimations) {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        }

        /* Shared view model & sliding pane related */

        view.doOnPreDraw { sharedViewModel.isSlidingPaneSlideable.value = binding.slidingPane.isSlideable }

        // Account settings loading can take some time, so wait until it is ready before opening the pane
        sharedViewModel.accountSettingsFragmentOpenedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                binding.slidingPane.openPane()
            }
        }

        sharedViewModel.layoutChangedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                sharedViewModel.isSlidingPaneSlideable.value = binding.slidingPane.isSlideable
                if (binding.slidingPane.isSlideable) {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    if (navHostFragment.navController.currentDestination?.id == R.id.emptySettingsFragment) {
                        Log.i("[Settings] Foldable device has been folded, closing side pane with empty fragment")
                        binding.slidingPane.closePane()
                    }
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            MasterFragment.SlidingPaneBackPressedCallback(binding.slidingPane)
        )

        binding.slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        /* End of shared view model & sliding pane related */

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        binding.viewModel = viewModel

        sharedViewModel.accountRemoved.observe(
            viewLifecycleOwner
        ) {
            Log.i("[Settings] Account removed, update accounts list")
            viewModel.updateAccountsList()
        }

        sharedViewModel.defaultAccountChanged.observe(
            viewLifecycleOwner
        ) {
            Log.i("[Settings] Default account changed, update accounts list")
            viewModel.updateAccountsList()
        }

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
                navigateToTunnelSettings(binding.slidingPane)
            }
        }

        viewModel.audioSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToAudioSettings(binding.slidingPane)
            }
        }

        viewModel.videoSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToVideoSettings(binding.slidingPane)
            }
        }

        viewModel.callSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToCallSettings(binding.slidingPane)
            }
        }

        viewModel.chatSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToChatSettings(binding.slidingPane)
            }
        }

        viewModel.networkSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToNetworkSettings(binding.slidingPane)
            }
        }

        viewModel.contactsSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToContactsSettings(binding.slidingPane)
            }
        }

        viewModel.advancedSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToAdvancedSettings(binding.slidingPane)
            }
        }

        viewModel.conferencesSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                navigateToConferencesSettings(binding.slidingPane)
            }
        }
    }
}

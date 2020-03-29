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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.settings.viewmodels.SettingsViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsFragmentBinding

class SettingsFragment : Fragment() {
    private lateinit var binding: SettingsFragmentBinding
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { findNavController().popBackStack() }

        sharedViewModel.proxyConfigRemoved.observe(viewLifecycleOwner, Observer {
            Log.i("[Settings] Proxy config removed, update accounts list")
            viewModel.updateAccountsList()
        })

        val identity = arguments?.getString("identity")
        if (identity != null) {
            val args = Bundle()
            args.putString("Identity", identity)
            Log.i("[Settings] Found identity parameter in arguments: $identity")
            arguments?.clear()

            if (!resources.getBoolean(R.bool.isTablet)) {
                if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                    findNavController().navigate(
                        R.id.action_settingsFragment_to_accountSettingsFragment,
                        args
                    )
                }
            } else {
                val navHostFragment =
                    childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                navHostFragment.navController.navigate(R.id.action_global_accountSettingsFragment, args)
            }
        }

        viewModel.accountsSettingsListener = object : SettingListenerStub() {
            override fun onAccountClicked(identity: String) {
                val args = Bundle()
                args.putString("Identity", identity)
                Log.i("[Settings] Navigation to settings for proxy with identity: $identity")

                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(
                            R.id.action_settingsFragment_to_accountSettingsFragment,
                            args
                        )
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_accountSettingsFragment, args)
                }
            }
        }

        viewModel.tunnelSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_tunnelSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_tunnelSettingsFragment)
                }
            }
        }

        viewModel.audioSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_audioSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_audioSettingsFragment)
                }
            }
        }

        viewModel.videoSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_videoSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_videoSettingsFragment)
                }
            }
        }

        viewModel.callSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_callSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_callSettingsFragment)
                }
            }
        }

        viewModel.chatSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_chatSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_chatSettingsFragment)
                }
            }
        }

        viewModel.networkSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_networkSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_networkSettingsFragment)
                }
            }
        }

        viewModel.contactsSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_contactsSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_contactsSettingsFragment)
                }
            }
        }

        viewModel.advancedSettingsListener = object : SettingListenerStub() {
            override fun onClicked() {
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                        findNavController().navigate(R.id.action_settingsFragment_to_advancedSettingsFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_advancedSettingsFragment)
                }
            }
        }
    }
}

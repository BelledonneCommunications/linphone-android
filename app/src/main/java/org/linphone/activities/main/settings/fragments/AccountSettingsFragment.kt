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
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.settings.viewmodels.AccountSettingsViewModel
import org.linphone.activities.main.settings.viewmodels.AccountSettingsViewModelFactory
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToPhoneLinking
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsAccountFragmentBinding

class AccountSettingsFragment : GenericFragment<SettingsAccountFragmentBinding>() {
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var viewModel: AccountSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_account_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        val identity = arguments?.getString("Identity")
        if (identity == null) {
            Log.e("[Account Settings] Identity is null, aborting!")
            (activity as MainActivity).showSnackBar(R.string.error)
            findNavController().navigateUp()
            return
        }

        viewModel = ViewModelProvider(this, AccountSettingsViewModelFactory(identity)).get(AccountSettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { findNavController().popBackStack() }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

        viewModel.linkPhoneNumberEvent.observe(viewLifecycleOwner, {
            it.consume {
                val authInfo = viewModel.account.findAuthInfo()
                if (authInfo == null) {
                    Log.e("[Account Settings] Failed to find auth info for account ${viewModel.account}")
                } else {
                    val args = Bundle()
                    args.putString("Username", authInfo.username)
                    args.putString("Password", authInfo.password)
                    args.putString("HA1", authInfo.ha1)
                    navigateToPhoneLinking(args)
                }
            }
        })

        viewModel.accountRemovedEvent.observe(viewLifecycleOwner, {
            it.consume {
                sharedViewModel.accountRemoved.value = true
                findNavController().navigateUp()
            }
        })
    }
}

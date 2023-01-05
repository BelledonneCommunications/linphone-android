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
package org.linphone.activities.assistant.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.assistant.viewmodels.*
import org.linphone.activities.navigateToEchoCancellerCalibration
import org.linphone.activities.navigateToPhoneAccountValidation
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantPhoneAccountLinkingFragmentBinding

class PhoneAccountLinkingFragment : AbstractPhoneFragment<AssistantPhoneAccountLinkingFragmentBinding>() {
    private lateinit var sharedAssistantViewModel: SharedAssistantViewModel
    override lateinit var viewModel: PhoneAccountLinkingViewModel

    override fun getLayoutId(): Int = R.layout.assistant_phone_account_linking_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedAssistantViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedAssistantViewModel::class.java]
        }

        val accountCreator = sharedAssistantViewModel.getAccountCreator()
        viewModel = ViewModelProvider(this, PhoneAccountLinkingViewModelFactory(accountCreator))[PhoneAccountLinkingViewModel::class.java]
        binding.viewModel = viewModel

        val username = arguments?.getString("Username")
        Log.i("[Phone Account Linking] username to link is $username")
        viewModel.username.value = username

        val password = arguments?.getString("Password")
        accountCreator.password = password

        val ha1 = arguments?.getString("HA1")
        accountCreator.ha1 = ha1

        val allowSkip = arguments?.getBoolean("AllowSkip", false)
        viewModel.allowSkip.value = allowSkip

        binding.setInfoClickListener {
            showPhoneNumberInfoDialog()
        }

        binding.setSelectCountryClickListener {
            val countryPickerFragment = CountryPickerFragment()
            countryPickerFragment.listener = viewModel
            countryPickerFragment.show(childFragmentManager, "CountryPicker")
        }

        viewModel.goToSmsValidationEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val args = Bundle()
                args.putBoolean("IsLinking", true)
                args.putString("PhoneNumber", viewModel.accountCreator.phoneNumber)
                navigateToPhoneAccountValidation(args)
            }
        }

        viewModel.leaveAssistantEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (coreContext.core.isEchoCancellerCalibrationRequired) {
                    navigateToEchoCancellerCalibration()
                } else {
                    requireActivity().finish()
                }
            }
        }

        viewModel.onErrorEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { message ->
                (requireActivity() as SnackBarActivity).showSnackBar(message)
            }
        }

        checkPermissions()
    }
}

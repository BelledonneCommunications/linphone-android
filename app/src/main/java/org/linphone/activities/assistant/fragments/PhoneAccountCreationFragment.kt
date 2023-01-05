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
import org.linphone.R
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.assistant.viewmodels.PhoneAccountCreationViewModel
import org.linphone.activities.assistant.viewmodels.PhoneAccountCreationViewModelFactory
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.activities.navigateToPhoneAccountValidation
import org.linphone.databinding.AssistantPhoneAccountCreationFragmentBinding

class PhoneAccountCreationFragment :
    AbstractPhoneFragment<AssistantPhoneAccountCreationFragmentBinding>() {
    private lateinit var sharedAssistantViewModel: SharedAssistantViewModel
    override lateinit var viewModel: PhoneAccountCreationViewModel

    override fun getLayoutId(): Int = R.layout.assistant_phone_account_creation_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedAssistantViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedAssistantViewModel::class.java]
        }

        viewModel = ViewModelProvider(
            this,
            PhoneAccountCreationViewModelFactory(sharedAssistantViewModel.getAccountCreator())
        )[PhoneAccountCreationViewModel::class.java]
        binding.viewModel = viewModel

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
                args.putBoolean("IsCreation", true)
                args.putString("PhoneNumber", viewModel.accountCreator.phoneNumber)
                navigateToPhoneAccountValidation(args)
            }
        }

        viewModel.onErrorEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { message ->
                (requireActivity() as AssistantActivity).showSnackBar(message)
            }
        }

        checkPermissions()
    }
}

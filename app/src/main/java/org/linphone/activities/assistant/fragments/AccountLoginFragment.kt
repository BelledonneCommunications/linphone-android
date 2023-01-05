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

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.assistant.viewmodels.AccountLoginViewModel
import org.linphone.activities.assistant.viewmodels.AccountLoginViewModelFactory
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToEchoCancellerCalibration
import org.linphone.activities.navigateToPhoneAccountValidation
import org.linphone.databinding.AssistantAccountLoginFragmentBinding
import org.linphone.utils.DialogUtils

class AccountLoginFragment : AbstractPhoneFragment<AssistantAccountLoginFragmentBinding>() {
    override lateinit var viewModel: AccountLoginViewModel
    private lateinit var sharedAssistantViewModel: SharedAssistantViewModel

    override fun getLayoutId(): Int = R.layout.assistant_account_login_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedAssistantViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedAssistantViewModel::class.java]
        }

        viewModel = ViewModelProvider(
            this,
            AccountLoginViewModelFactory(sharedAssistantViewModel.getAccountCreator())
        )[AccountLoginViewModel::class.java]
        binding.viewModel = viewModel

        if (resources.getBoolean(R.bool.isTablet)) {
            viewModel.loginWithUsernamePassword.value = true
        }

        binding.setInfoClickListener {
            showPhoneNumberInfoDialog()
        }

        binding.setSelectCountryClickListener {
            val countryPickerFragment = CountryPickerFragment()
            countryPickerFragment.listener = viewModel
            countryPickerFragment.show(childFragmentManager, "CountryPicker")
        }

        binding.setForgotPasswordClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = Uri.parse(getString(R.string.assistant_forgotten_password_link))
            startActivity(intent)
        }

        viewModel.goToSmsValidationEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val args = Bundle()
                args.putBoolean("IsLogin", true)
                args.putString("PhoneNumber", viewModel.accountCreator.phoneNumber)
                navigateToPhoneAccountValidation(args)
            }
        }

        viewModel.leaveAssistantEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                coreContext.newAccountConfigured(true)

                if (coreContext.core.isEchoCancellerCalibrationRequired) {
                    navigateToEchoCancellerCalibration()
                } else {
                    requireActivity().finish()
                }
            }
        }

        viewModel.invalidCredentialsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val dialogViewModel =
                    DialogViewModel(getString(R.string.assistant_error_invalid_credentials))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

                dialogViewModel.showCancelButton {
                    viewModel.removeInvalidProxyConfig()
                    dialog.dismiss()
                }

                dialogViewModel.showDeleteButton(
                    {
                        viewModel.continueEvenIfInvalidCredentials()
                        dialog.dismiss()
                    },
                    getString(R.string.assistant_continue_even_if_credentials_invalid)
                )

                dialog.show()
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

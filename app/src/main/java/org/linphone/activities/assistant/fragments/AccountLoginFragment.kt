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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.assistant.viewmodels.AccountLoginViewModel
import org.linphone.activities.assistant.viewmodels.AccountLoginViewModelFactory
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.databinding.AssistantAccountLoginFragmentBinding
import org.linphone.utils.DialogUtils

class AccountLoginFragment : AbstractPhoneFragment() {
    private lateinit var binding: AssistantAccountLoginFragmentBinding
    override lateinit var viewModel: AccountLoginViewModel
    private lateinit var sharedViewModel: SharedAssistantViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantAccountLoginFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedAssistantViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel = ViewModelProvider(this, AccountLoginViewModelFactory(sharedViewModel.getAccountCreator())).get(AccountLoginViewModel::class.java)
        binding.viewModel = viewModel

        if (resources.getBoolean(R.bool.isTablet)) {
            viewModel.loginWithUsernamePassword.value = true
        }

        binding.setInfoClickListener {
            showPhoneNumberInfoDialog()
        }

        binding.setSelectCountryClickListener {
            CountryPickerFragment(viewModel).show(childFragmentManager, "CountryPicker")
        }

        binding.setForgotPasswordClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = Uri.parse(getString(R.string.assistant_forgotten_password_link))
            startActivity(intent)
        }

        viewModel.goToSmsValidationEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.accountLoginFragment) {
                    val args = Bundle()
                    args.putBoolean("IsLogin", true)
                    args.putString("PhoneNumber", viewModel.accountCreator.phoneNumber)
                    findNavController().navigate(R.id.action_accountLoginFragment_to_phoneAccountValidationFragment, args)
                }
            }
        })

        viewModel.leaveAssistantEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                if (coreContext.core.isEchoCancellerCalibrationRequired) {
                    if (findNavController().currentDestination?.id == R.id.accountLoginFragment) {
                        findNavController().navigate(R.id.action_accountLoginFragment_to_echoCancellerCalibrationFragment)
                    }
                } else {
                    requireActivity().finish()
                }
            }
        })

        viewModel.invalidCredentialsEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                val dialogViewModel = DialogViewModel(getString(R.string.assistant_error_invalid_credentials))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

                dialogViewModel.showCancelButton {
                    viewModel.removeInvalidProxyConfig()
                    dialog.dismiss()
                }

                dialogViewModel.showDeleteButton({
                    viewModel.continueEvenIfInvalidCredentials()
                    dialog.dismiss()
                }, getString(R.string.assistant_continue_even_if_credentials_invalid))

                dialog.show()
            }
        })

        checkPermission()
    }
}

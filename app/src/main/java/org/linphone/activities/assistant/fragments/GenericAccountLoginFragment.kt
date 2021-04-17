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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.assistant.viewmodels.GenericLoginViewModel
import org.linphone.activities.assistant.viewmodels.GenericLoginViewModelFactory
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.databinding.AssistantGenericAccountLoginFragmentBinding
import org.linphone.utils.DialogUtils

class GenericAccountLoginFragment : Fragment() {
    private lateinit var binding: AssistantGenericAccountLoginFragmentBinding
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: GenericLoginViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantGenericAccountLoginFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedAssistantViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel = ViewModelProvider(this, GenericLoginViewModelFactory(sharedViewModel.getAccountCreator(true))).get(GenericLoginViewModel::class.java)
        binding.viewModel = viewModel

        viewModel.leaveAssistantEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                if (coreContext.core.isEchoCancellerCalibrationRequired) {
                    if (findNavController().currentDestination?.id == R.id.genericAccountLoginFragment) {
                        findNavController().navigate(R.id.action_genericAccountLoginFragment_to_echoCancellerCalibrationFragment)
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
    }
}

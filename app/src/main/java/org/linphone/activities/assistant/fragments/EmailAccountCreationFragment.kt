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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.assistant.viewmodels.EmailAccountCreationViewModel
import org.linphone.activities.assistant.viewmodels.EmailAccountCreationViewModelFactory
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.databinding.AssistantEmailAccountCreationFragmentBinding

class EmailAccountCreationFragment : Fragment() {
    private lateinit var binding: AssistantEmailAccountCreationFragmentBinding
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: EmailAccountCreationViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantEmailAccountCreationFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedAssistantViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel = ViewModelProvider(this, EmailAccountCreationViewModelFactory(sharedViewModel.getAccountCreator())).get(EmailAccountCreationViewModel::class.java)
        binding.viewModel = viewModel

        viewModel.goToEmailValidationEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.emailAccountCreationFragment) {
                    findNavController().navigate(R.id.action_emailAccountCreationFragment_to_emailAccountValidationFragment)
                }
            }
        })
    }
}

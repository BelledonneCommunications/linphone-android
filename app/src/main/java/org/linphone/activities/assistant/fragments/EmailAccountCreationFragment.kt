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
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.viewmodels.EmailAccountCreationViewModel
import org.linphone.activities.assistant.viewmodels.EmailAccountCreationViewModelFactory
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.activities.main.navigateToEmailAccountValidation
import org.linphone.databinding.AssistantEmailAccountCreationFragmentBinding

class EmailAccountCreationFragment : GenericFragment<AssistantEmailAccountCreationFragmentBinding>() {
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: EmailAccountCreationViewModel

    override fun getLayoutId(): Int = R.layout.assistant_email_account_creation_fragment

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
                navigateToEmailAccountValidation()
            }
        })
    }
}

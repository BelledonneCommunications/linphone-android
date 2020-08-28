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
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.viewmodels.WelcomeViewModel
import org.linphone.databinding.AssistantWelcomeFragmentBinding

class WelcomeFragment : GenericFragment<AssistantWelcomeFragmentBinding>() {
    private lateinit var viewModel: WelcomeViewModel

    override fun getLayoutId(): Int = R.layout.assistant_welcome_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(WelcomeViewModel::class.java)
        binding.viewModel = viewModel

        binding.setCreateAccountClickListener {
            if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
                if (resources.getBoolean(R.bool.isTablet)) {
                    findNavController().navigate(R.id.action_welcomeFragment_to_emailAccountCreationFragment)
                } else {
                    findNavController().navigate(R.id.action_welcomeFragment_to_phoneAccountCreationFragment)
                }
            }
        }

        binding.setAccountLoginClickListener {
            if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
                findNavController().navigate(R.id.action_welcomeFragment_to_accountLoginFragment)
            }
        }

        binding.setGenericAccountLoginClickListener {
            if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
                findNavController().navigate(R.id.action_welcomeFragment_to_genericAccountLoginFragment)
            }
        }

        binding.setRemoteProvisioningClickListener {
            if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
                findNavController().navigate(R.id.action_welcomeFragment_to_remoteProvisioningFragment)
            }
        }
    }
}

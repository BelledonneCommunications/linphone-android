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
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.databinding.AssistantWelcomeFragmentBinding

class WelcomeFragment : Fragment() {
    private lateinit var binding: AssistantWelcomeFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantWelcomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

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

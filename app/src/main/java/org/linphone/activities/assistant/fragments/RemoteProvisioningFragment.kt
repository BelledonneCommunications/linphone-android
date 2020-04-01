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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.assistant.viewmodels.RemoteProvisioningViewModel
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.databinding.AssistantRemoteProvisioningFragmentBinding

class RemoteProvisioningFragment : Fragment() {
    private lateinit var binding: AssistantRemoteProvisioningFragmentBinding
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: RemoteProvisioningViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantRemoteProvisioningFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedAssistantViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel = ViewModelProvider(this).get(RemoteProvisioningViewModel::class.java)
        binding.viewModel = viewModel

        binding.setQrCodeClickListener {
            if (findNavController().currentDestination?.id == R.id.remoteProvisioningFragment) {
                findNavController().navigate(R.id.action_remoteProvisioningFragment_to_qrCodeFragment)
            }
        }

        viewModel.fetchSuccessfulEvent.observe(viewLifecycleOwner, Observer {
            it.consume { success ->
                if (success) {
                    if (coreContext.core.isEchoCancellerCalibrationRequired) {
                        if (findNavController().currentDestination?.id == R.id.remoteProvisioningFragment) {
                            findNavController().navigate(R.id.action_remoteProvisioningFragment_to_echoCancellerCalibrationFragment)
                        }
                    } else {
                        requireActivity().finish()
                    }
                } else {
                    val activity = requireActivity() as AssistantActivity
                    activity.showSnackBar(R.string.assistant_remote_provisioning_failure)
                }
            }
        })

        viewModel.urlToFetch.value = sharedViewModel.remoteProvisioningUrl.value ?: coreContext.core.provisioningUri
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedViewModel.remoteProvisioningUrl.value = null
    }
}

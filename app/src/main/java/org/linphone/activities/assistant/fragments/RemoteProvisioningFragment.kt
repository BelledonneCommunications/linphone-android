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
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.assistant.viewmodels.RemoteProvisioningViewModel
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.activities.navigateToEchoCancellerCalibration
import org.linphone.activities.navigateToQrCode
import org.linphone.databinding.AssistantRemoteProvisioningFragmentBinding

class RemoteProvisioningFragment : GenericFragment<AssistantRemoteProvisioningFragmentBinding>() {
    private lateinit var sharedAssistantViewModel: SharedAssistantViewModel
    private lateinit var viewModel: RemoteProvisioningViewModel

    override fun getLayoutId(): Int = R.layout.assistant_remote_provisioning_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedAssistantViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedAssistantViewModel::class.java]
        }

        viewModel = ViewModelProvider(this)[RemoteProvisioningViewModel::class.java]
        binding.viewModel = viewModel

        binding.setQrCodeClickListener {
            navigateToQrCode()
        }

        viewModel.fetchSuccessfulEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { success ->
                if (success) {
                    if (coreContext.core.isEchoCancellerCalibrationRequired) {
                        navigateToEchoCancellerCalibration()
                    } else {
                        requireActivity().finish()
                    }
                } else {
                    val activity = requireActivity() as AssistantActivity
                    activity.showSnackBar(R.string.assistant_remote_provisioning_failure)
                }
            }
        }

        viewModel.urlToFetch.value = sharedAssistantViewModel.remoteProvisioningUrl.value ?: coreContext.core.provisioningUri
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::sharedAssistantViewModel.isInitialized) {
            sharedAssistantViewModel.remoteProvisioningUrl.value = null
        }
    }
}

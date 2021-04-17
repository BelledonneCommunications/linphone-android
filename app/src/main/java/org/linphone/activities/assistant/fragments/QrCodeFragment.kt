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

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.viewmodels.QrCodeViewModel
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantQrCodeFragmentBinding
import org.linphone.utils.PermissionHelper

class QrCodeFragment : GenericFragment<AssistantQrCodeFragmentBinding>() {
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: QrCodeViewModel

    override fun getLayoutId(): Int = R.layout.assistant_qr_code_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedAssistantViewModel::class.java)
        }

        viewModel = ViewModelProvider(this).get(QrCodeViewModel::class.java)
        binding.viewModel = viewModel

        viewModel.qrCodeFoundEvent.observe(viewLifecycleOwner, {
            it.consume { url ->
                sharedViewModel.remoteProvisioningUrl.value = url
                findNavController().navigateUp()
            }
        })
        viewModel.setBackCamera()

        if (!PermissionHelper.required(requireContext()).hasCameraPermission()) {
            Log.i("[QR Code] Asking for CAMERA permission")
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 0)
        }
    }

    override fun onResume() {
        super.onResume()

        coreContext.core.nativePreviewWindowId = binding.qrCodeCaptureTexture
        coreContext.core.enableQrcodeVideoPreview(true)
        coreContext.core.enableVideoPreview(true)
    }

    override fun onPause() {
        coreContext.core.nativePreviewWindowId = null
        coreContext.core.enableQrcodeVideoPreview(false)
        coreContext.core.enableVideoPreview(false)

        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            Log.i("[QR Code] CAMERA permission granted")
            coreContext.core.reloadVideoDevices()
            viewModel.setBackCamera()
        } else {
            Log.w("[QR Code] CAMERA permission denied")
            findNavController().navigateUp()
        }
    }
}

/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package org.linphone.ui.assistant.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantQrCodeScannerFragmentBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.assistant.viewmodel.QrCodeViewModel

@UiThread
class QrCodeScannerFragment : Fragment() {
    companion object {
        private const val TAG = "[Qr Code Scanner Fragment]"
    }

    private lateinit var binding: AssistantQrCodeScannerFragmentBinding

    private val viewModel: QrCodeViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG Camera permission has been granted")
            enableQrCodeVideoScanner()
        } else {
            Log.e("$TAG Camera permission has been denied, leaving this fragment")
            goBack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantQrCodeScannerFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        viewModel.qrCodeFoundEvent.observe(viewLifecycleOwner) {
            it.consume { isValid ->
                if (!isValid) {
                    (requireActivity() as AssistantActivity).showRedToast(
                        getString(R.string.toast_assistant_qr_code_invalid),
                        R.drawable.warning_circle
                    )
                } else {
                    requireActivity().finish()
                }
            }
        }

        if (!isCameraPermissionGranted()) {
            Log.w("$TAG Camera permission wasn't granted yet, asking for it now")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()

        if (isCameraPermissionGranted()) {
            viewModel.setBackCamera()
            enableQrCodeVideoScanner()
        }
    }

    override fun onPause() {
        coreContext.postOnCoreThread { core ->
            core.nativePreviewWindowId = null
            core.isVideoPreviewEnabled = false
            core.isQrcodeVideoPreviewEnabled = false
        }

        super.onPause()
    }

    private fun goBack() {
        findNavController().popBackStack()
    }

    private fun isCameraPermissionGranted(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.i("$TAG Camera permission is ${if (granted) "granted" else "denied"}")
        return granted
    }

    private fun enableQrCodeVideoScanner() {
        coreContext.postOnCoreThread { core ->
            core.nativePreviewWindowId = binding.qrCodePreview
            core.isQrcodeVideoPreviewEnabled = true
            core.isVideoPreviewEnabled = true
        }
    }
}

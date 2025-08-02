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
package com.naminfo.ui.assistant.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.AssistantQrCodeScannerFragmentBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.GenericFragment
import com.naminfo.ui.assistant.viewmodel.QrCodeViewModel
import com.naminfo.ui.main.sso.fragment.SingleSignOnFragmentDirections

@UiThread
class QrCodeScannerFragment : GenericFragment() {
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
            Log.i("$TAG CAMERA permission has been granted")
            enableQrCodeVideoScanner()
        } else {
            Log.e("$TAG CAMERA permission has been denied, leaving this fragment")
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
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.qrCodeFoundEvent.observe(viewLifecycleOwner) {
            it.consume { isValid ->
                if (isValid) {
                    requireActivity().finish()
                }
            }
        }

        viewModel.onErrorEvent.observe(viewLifecycleOwner) {
            it.consume {
                // Core has restarted but something went wrong, restart video capture
                enableQrCodeVideoScanner()
            }
        }

        coreContext.bearerAuthenticationRequestedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val serverUrl = pair.first
                val username = pair.second

                Log.i(
                    "$TAG Navigating to Single Sign On Fragment with server URL [$serverUrl] and username [$username]"
                )
                if (findNavController().currentDestination?.id == R.id.qrCodeScannerFragment) {
                    val action = SingleSignOnFragmentDirections.actionGlobalSingleSignOnFragment(
                        serverUrl,
                        username
                    )
                    findNavController().navigate(action)
                }
            }
        }

        if (!isCameraPermissionGranted()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA)) {
                Log.w("$TAG CAMERA permission wasn't granted yet, asking for it now")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                Log.i("$TAG Permission request for CAMERA will be automatically denied, go to android app settings instead")
                (requireActivity() as GenericActivity).goToAndroidPermissionSettings()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (isCameraPermissionGranted()) {
            Log.i(
                "$TAG Record video permission is granted, starting video preview with back cam if possible"
            )
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
        Log.i("$TAG CAMERA permission is ${if (granted) "granted" else "denied"}")
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

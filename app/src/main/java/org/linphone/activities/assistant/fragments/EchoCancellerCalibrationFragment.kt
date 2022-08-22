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
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.viewmodels.EchoCancellerCalibrationViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantEchoCancellerCalibrationFragmentBinding
import org.linphone.utils.PermissionHelper

class EchoCancellerCalibrationFragment : GenericFragment<AssistantEchoCancellerCalibrationFragmentBinding>() {
    companion object {
        const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 0
    }

    private lateinit var viewModel: EchoCancellerCalibrationViewModel

    override fun getLayoutId(): Int = R.layout.assistant_echo_canceller_calibration_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[EchoCancellerCalibrationViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.echoCalibrationTerminated.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                requireActivity().finish()
            }
        }

        if (!PermissionHelper.required(requireContext()).hasRecordAudioPermission()) {
            Log.i("[Echo Canceller Calibration] Asking for RECORD_AUDIO permission")
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_REQUEST_CODE)
        } else {
            viewModel.startEchoCancellerCalibration()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            val granted =
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Echo Canceller Calibration] RECORD_AUDIO permission granted")
                viewModel.startEchoCancellerCalibration()
            } else {
                Log.w("[Echo Canceller Calibration] RECORD_AUDIO permission denied")
                requireActivity().finish()
            }
        }
    }
}

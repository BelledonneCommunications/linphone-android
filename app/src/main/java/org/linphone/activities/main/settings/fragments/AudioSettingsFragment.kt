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
package org.linphone.activities.main.settings.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import org.linphone.BR
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.settings.viewmodels.AudioSettingsViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsAudioFragmentBinding
import org.linphone.utils.PermissionHelper

class AudioSettingsFragment : GenericSettingFragment<SettingsAudioFragmentBinding>() {
    private lateinit var viewModel: AudioSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_audio_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this)[AudioSettingsViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.askAudioRecordPermissionForEchoCancellerCalibrationEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("[Audio Settings] Asking for RECORD_AUDIO permission for echo canceller calibration")
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
            }
        }

        viewModel.askAudioRecordPermissionForEchoTesterEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("[Audio Settings] Asking for RECORD_AUDIO permission for echo tester")
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2)
            }
        }

        initAudioCodecsList()

        if (!PermissionHelper.required(requireContext()).hasRecordAudioPermission()) {
            Log.i("[Audio Settings] Asking for RECORD_AUDIO permission")
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            Log.i("[Audio Settings] RECORD_AUDIO permission granted")
            if (requestCode == 1) {
                viewModel.startEchoCancellerCalibration()
            } else if (requestCode == 2) {
                viewModel.startEchoTester()
            }
        } else {
            Log.w("[Audio Settings] RECORD_AUDIO permission denied")
        }
    }

    private fun initAudioCodecsList() {
        val list = arrayListOf<ViewDataBinding>()
        for (payload in coreContext.core.audioPayloadTypes) {
            val binding = DataBindingUtil.inflate<ViewDataBinding>(LayoutInflater.from(requireContext()), R.layout.settings_widget_switch, null, false)
            binding.setVariable(BR.title, payload.mimeType)
            binding.setVariable(BR.subtitle, "${payload.clockRate} Hz")
            binding.setVariable(BR.checked, payload.enabled())
            binding.setVariable(
                BR.listener,
                object : SettingListenerStub() {
                    override fun onBoolValueChanged(newValue: Boolean) {
                        payload.enable(newValue)
                    }
                }
            )
            binding.lifecycleOwner = viewLifecycleOwner
            list.add(binding)
        }
        viewModel.audioCodecs.value = list
    }
}

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
package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.databinding.SettingsAdvancedFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.settings.viewmodel.SettingsViewModel
import org.linphone.utils.Event

@UiThread
class SettingsAdvancedFragment : GenericMainFragment() {
    private lateinit var binding: SettingsAdvancedFragmentBinding

    private lateinit var viewModel: SettingsViewModel

    private val inputAudioDeviceDropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            viewModel.setInputAudioDevice(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val outputAudioDeviceDropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            viewModel.setOutputAudioDevice(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsAdvancedFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.setAndroidSettingsClickListener {
            (requireActivity() as GenericActivity).goToAndroidPermissionSettings()
        }

        viewModel.inputAudioDeviceIndex.observe(viewLifecycleOwner) {
            setupInputAudioDevicePicker()
        }

        viewModel.outputAudioDeviceIndex.observe(viewLifecycleOwner) {
            setupOutputAudioDevicePicker()
        }

        viewModel.keepAliveServiceSettingChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                sharedViewModel.refreshDrawerMenuQuitButtonEvent.postValue(Event(true))
            }
        }

        startPostponedEnterTransition()
    }

    override fun onPause() {
        viewModel.updateDeviceName()
        viewModel.updateRemoteProvisioningUrl()

        super.onPause()
    }

    private fun setupInputAudioDevicePicker() {
        val index = viewModel.inputAudioDeviceIndex.value ?: 0
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.inputAudioDeviceLabels
        )
        adapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.inputAudioDevice.adapter = adapter
        binding.inputAudioDevice.onItemSelectedListener = inputAudioDeviceDropdownListener
        binding.inputAudioDevice.setSelection(index)
    }

    private fun setupOutputAudioDevicePicker() {
        val index = viewModel.outputAudioDeviceIndex.value ?: 0
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.outputAudioDeviceLabels
        )
        adapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.outputAudioDevice.adapter = adapter
        binding.outputAudioDevice.onItemSelectedListener = outputAudioDeviceDropdownListener
        binding.outputAudioDevice.setSelection(index)
    }
}

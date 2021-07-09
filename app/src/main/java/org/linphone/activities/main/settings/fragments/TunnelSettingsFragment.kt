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

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.settings.viewmodels.TunnelSettingsViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.databinding.SettingsTunnelFragmentBinding
import org.linphone.utils.Event

class TunnelSettingsFragment : GenericFragment<SettingsTunnelFragmentBinding>() {
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var viewModel: TunnelSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_tunnel_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }
        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this).get(TunnelSettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { goBack() }
    }

    override fun goBack() {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
    }
}

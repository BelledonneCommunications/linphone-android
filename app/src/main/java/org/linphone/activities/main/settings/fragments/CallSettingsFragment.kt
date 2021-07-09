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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.settings.viewmodels.CallSettingsViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.databinding.SettingsCallFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.utils.Event

class CallSettingsFragment : GenericFragment<SettingsCallFragmentBinding>() {
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var viewModel: CallSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_call_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }
        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this).get(CallSettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { goBack() }

        viewModel.systemWideOverlayEnabledEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (!Compatibility.canDrawOverlay(requireContext())) {
                    val intent = Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:${requireContext().packageName}"))
                    startActivityForResult(intent, 0)
                }
            }
        })

        viewModel.goToAndroidNotificationSettingsEvent.observe(viewLifecycleOwner, { it.consume {
            if (Build.VERSION.SDK_INT >= Version.API26_O_80) {
                val i = Intent()
                i.action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                i.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                i.putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    getString(R.string.notification_channel_service_id)
                )
                i.addCategory(Intent.CATEGORY_DEFAULT)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                startActivity(i)
            }
        } })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (!Compatibility.canDrawOverlay(requireContext())) {
            viewModel.systemWideOverlayListener.onBoolValueChanged(false)
        }
    }

    override fun goBack() {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
    }
}

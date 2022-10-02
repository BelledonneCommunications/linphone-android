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
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.settings.viewmodels.CallSettingsViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsCallFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.telecom.TelecomHelper

class CallSettingsFragment : GenericSettingFragment<SettingsCallFragmentBinding>() {
    private lateinit var viewModel: CallSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_call_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this)[CallSettingsViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.systemWideOverlayEnabledEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (!Compatibility.canDrawOverlay(requireContext())) {
                    val intent = Intent(
                        "android.settings.action.MANAGE_OVERLAY_PERMISSION",
                        Uri.parse("package:${requireContext().packageName}")
                    )
                    startActivityForResult(intent, 0)
                }
            }
        }

        viewModel.goToAndroidNotificationSettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
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
            }
        }

        viewModel.enableTelecomManagerEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) {
                    if (!Compatibility.hasTelecomManagerPermissions(requireContext())) {
                        Compatibility.requestTelecomManagerPermissions(requireActivity(), 1)
                    } else if (!TelecomHelper.exists()) {
                        corePreferences.useTelecomManager = true
                        Log.w("[Telecom Helper] Doesn't exists yet, creating it")
                        if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) {
                            TelecomHelper.create(requireContext())
                            updateTelecomManagerAccount()
                        } else {
                            Log.e("[Telecom Helper] Telecom Helper can't be created, device doesn't support connection service")
                        }
                    }
                } else {
                    Log.e("[Telecom Helper] Telecom Helper can't be created, device doesn't support connection service!")
                }
            }
        }

        viewModel.goToAndroidNotificationSettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
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
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0 && !Compatibility.canDrawOverlay(requireContext())) {
            viewModel.overlayListener.onBoolValueChanged(false)
        } else if (requestCode == 1) {
            if (!TelecomHelper.exists()) {
                Log.w("[Telecom Helper] Doesn't exists yet, creating it")
                if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) {
                    TelecomHelper.create(requireContext())
                } else {
                    Log.e("[Telecom Helper] Telecom Helper can't be created, device doesn't support connection service")
                }
            }
            updateTelecomManagerAccount()
        }
    }

    private fun updateTelecomManagerAccount() {
        if (!TelecomHelper.exists()) {
            Log.e("[Telecom Helper] Doesn't exists, can't update account!")
            return
        }
        // We have to refresh the account object otherwise isAccountEnabled will always return false...
        val account = TelecomHelper.get().findExistingAccount(requireContext())
        TelecomHelper.get().updateAccount(account)
        val enabled = TelecomHelper.get().isAccountEnabled()
        Log.i("[Call Settings] Telecom Manager is ${if (enabled) "enabled" else "disabled"}")
        viewModel.useTelecomManager.value = enabled
        corePreferences.useTelecomManager = enabled
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        for (index in grantResults.indices) {
            val result = grantResults[index]
            if (result != PackageManager.PERMISSION_GRANTED) {
                Log.w("[Call Settings] ${permissions[index]} permission denied but required for telecom manager")
                viewModel.useTelecomManager.value = false
                corePreferences.useTelecomManager = false
                return
            }
        }

        if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) {
            TelecomHelper.create(requireContext())
            updateTelecomManagerAccount()
        } else {
            Log.e("[Telecom Helper] Telecom Helper can't be created, device doesn't support connection service")
        }
    }
}

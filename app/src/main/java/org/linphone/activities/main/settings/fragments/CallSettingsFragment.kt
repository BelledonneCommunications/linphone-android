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

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.settings.viewmodels.CallSettingsViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsCallFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.telecom.TelecomHelper
import org.linphone.utils.PermissionHelper

class CallSettingsFragment : GenericFragment<SettingsCallFragmentBinding>() {
    private lateinit var viewModel: CallSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_call_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(CallSettingsViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { findNavController().popBackStack() }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

        viewModel.systemWideOverlayEnabledEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (!Compatibility.canDrawOverlay(requireContext())) {
                    val intent = Intent(
                        "android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse(
                            "package:${requireContext().packageName}"
                        )
                    )
                    startActivityForResult(intent, 0)
                }
            }
        })

        viewModel.enableTelecomManagerEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (!PermissionHelper.get().hasTelecomManagerPermissions()) {
                    val permissions = arrayOf(
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.MANAGE_OWN_CALLS
                    )
                    requestPermissions(permissions, 1)
                } else if (!TelecomHelper.get().isAccountEnabled()) {
                    startEnableAccountActivity()
                }
            }
        })

        viewModel.goToAndroidNotificationSettingsEvent.observe(viewLifecycleOwner, {
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
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0 && !Compatibility.canDrawOverlay(requireContext())) {
            viewModel.overlayListener.onBoolValueChanged(false)
        } else if (requestCode == 1) {
            // We have to refresh the account object otherwise isAccountEnabled will always return false...
            val account = TelecomHelper.get().findExistingAccount(requireContext())
            TelecomHelper.get().updateAccount(account)
            val enabled = TelecomHelper.get().isAccountEnabled()
            viewModel.useTelecomManager.value = enabled
            corePreferences.useTelecomManager = enabled
        }
    }

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

        if (!TelecomHelper.get().isAccountEnabled()) {
            startEnableAccountActivity()
        }
    }

    private fun startEnableAccountActivity() {
        // Starts a system activity for the user to manually enable the phone account
        Log.i("[Call Settings] Starting com.android.server.telecom.settings.EnableAccountPreferenceActivity")
        val phoneAccountEnable = Intent()
        phoneAccountEnable.component = ComponentName(
            "com.android.server.telecom",
            "com.android.server.telecom.settings.EnableAccountPreferenceActivity"
        )
        startActivityForResult(phoneAccountEnable, 1)
    }
}

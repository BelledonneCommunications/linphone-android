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

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.settings.viewmodels.AdvancedSettingsViewModel
import org.linphone.core.tools.Log
import org.linphone.core.tools.compatibility.DeviceUtils
import org.linphone.databinding.SettingsAdvancedFragmentBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.PowerManagerUtils

class AdvancedSettingsFragment : GenericSettingFragment<SettingsAdvancedFragmentBinding>() {
    private lateinit var viewModel: AdvancedSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_advanced_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this)[AdvancedSettingsViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.uploadFinishedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { url ->
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Logs url", url)
                clipboard.setPrimaryClip(clip)

                val activity = requireActivity() as MainActivity
                activity.showSnackBar(R.string.logs_url_copied_to_clipboard)

                AppUtils.shareUploadedLogsUrl(activity, url)
            }
        }

        viewModel.uploadErrorEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val activity = requireActivity() as MainActivity
                activity.showSnackBar(R.string.logs_upload_failure)
            }
        }

        viewModel.resetCompleteEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val activity = requireActivity() as MainActivity
                activity.showSnackBar(R.string.logs_reset_complete)
            }
        }

        viewModel.setNightModeEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { value ->
                AppCompatDelegate.setDefaultNightMode(
                    when (value) {
                        0 -> AppCompatDelegate.MODE_NIGHT_NO
                        1 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }

        viewModel.backgroundModeEnabled.value = !DeviceUtils.isAppUserRestricted(requireContext())

        viewModel.goToBatterySettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                try {
                    val intent = Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                    startActivity(intent)
                } catch (anfe: ActivityNotFoundException) {
                    Log.e("[Advanced Settings] ActivityNotFound exception: ", anfe)
                }
            }
        }

        viewModel.powerManagerSettingsVisibility.value = PowerManagerUtils.getDevicePowerManagerIntent(requireContext()) != null
        viewModel.goToPowerManagerSettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val intent = PowerManagerUtils.getDevicePowerManagerIntent(requireActivity())
                if (intent != null) {
                    try {
                        startActivity(intent)
                    } catch (se: SecurityException) {
                        Log.e("[Advanced Settings] Security exception: ", se)
                    }
                }
            }
        }

        viewModel.goToAndroidSettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                ContextCompat.startActivity(requireContext(), intent, null)
            }
        }
    }
}

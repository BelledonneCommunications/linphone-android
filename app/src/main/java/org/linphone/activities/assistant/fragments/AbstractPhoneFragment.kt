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
import androidx.databinding.ViewDataBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.viewmodels.AbstractPhoneViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.utils.PermissionHelper
import org.linphone.utils.PhoneNumberUtils

abstract class AbstractPhoneFragment<T : ViewDataBinding> : GenericFragment<T>() {
    companion object {
        const val READ_PHONE_STATE_PERMISSION_REQUEST_CODE = 0
    }

    abstract val viewModel: AbstractPhoneViewModel

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_PHONE_STATE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("[Assistant] READ_PHONE_STATE/READ_PHONE_NUMBERS permission granted")
                updateFromDeviceInfo()
            } else {
                Log.w("[Assistant] READ_PHONE_STATE/READ_PHONE_NUMBERS permission denied")
            }
        }
    }

    protected fun checkPermissions() {
        if (!resources.getBoolean(R.bool.isTablet)) {
            if (!PermissionHelper.get().hasReadPhoneStateOrPhoneNumbersPermission()) {
                Log.i("[Assistant] Asking for READ_PHONE_STATE/READ_PHONE_NUMBERS permission")
                Compatibility.requestReadPhoneStateOrNumbersPermission(this, READ_PHONE_STATE_PERMISSION_REQUEST_CODE)
            } else {
                updateFromDeviceInfo()
            }
        }
    }

    private fun updateFromDeviceInfo() {
        val phoneNumber = PhoneNumberUtils.getDevicePhoneNumber(requireContext())
        val dialPlan = PhoneNumberUtils.getDialPlanForCurrentCountry(requireContext())
        viewModel.updateFromPhoneNumberAndOrDialPlan(phoneNumber, dialPlan)
    }

    protected fun showPhoneNumberInfoDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.assistant_phone_number_info_title))
            .setMessage(
                getString(R.string.assistant_phone_number_link_info_content) + "\n" +
                    getString(
                        R.string.assistant_phone_number_link_info_content_already_account
                    )
            )
            .setNegativeButton(getString(R.string.dialog_ok), null)
            .show()
    }
}

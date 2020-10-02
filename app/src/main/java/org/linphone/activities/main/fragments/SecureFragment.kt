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
package org.linphone.activities.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.databinding.ViewDataBinding
import org.linphone.activities.GenericFragment
import org.linphone.core.tools.Log

abstract class SecureFragment<T : ViewDataBinding> : GenericFragment<T>() {
    protected var isSecure: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Assume we might want to be secure to prevent quick visible blink while screen recording.
        Log.d("[Secure Fragment] onCreateView")
        enableSecureMode(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onResume() {
        Log.d("[Secure Fragment] ${if (isSecure) "Enabling" else "Disabling"} secure flag on window")
        enableSecureMode(isSecure)
        super.onResume()
    }

    private fun enableSecureMode(enable: Boolean) {
        val window = requireActivity().window
        val windowManager = requireActivity().windowManager

        val flags: Int = window.attributes.flags
        if ((enable && flags and WindowManager.LayoutParams.FLAG_SECURE != 0) ||
            (!enable && flags and WindowManager.LayoutParams.FLAG_SECURE == 0)) {
            Log.d("[Secure Fragment] Secure flag is already ${if (enable) "enabled" else "disabled"}, skipping...")
            return
        }

        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (ViewCompat.isAttachedToWindow(window.decorView)) {
            Log.d("[Secure Fragment] Redrawing window decorView to apply flag")
            windowManager.updateViewLayout(window.decorView, window.attributes)
        }
    }
}

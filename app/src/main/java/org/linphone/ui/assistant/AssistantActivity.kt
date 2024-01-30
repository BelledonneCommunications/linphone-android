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
package org.linphone.ui.assistant

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.assistant.fragment.PermissionsFragmentDirections
import org.linphone.utils.ToastUtils
import org.linphone.utils.slideInToastFromTopForDuration

@UiThread
class AssistantActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Assistant Activity]"
    }

    private lateinit var binding: AssistantActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.assistant_activity)
        binding.lifecycleOwner = this

        coreContext.postOnCoreThread { core ->
            if (core.accountList.isEmpty()) {
                Log.i("$TAG No account configured, disabling back gesture")
                coreContext.postOnMainThread {
                    // Disable back gesture / button
                    onBackPressedDispatcher.addCallback { }
                }
            }
        }

        (binding.root as? ViewGroup)?.doOnPreDraw {
            if (!areAllPermissionsGranted()) {
                Log.w("$TAG Not all required permissions are granted, showing Permissions fragment")
                val action = PermissionsFragmentDirections.actionGlobalPermissionsFragment()
                binding.assistantNavContainer.findNavController().navigate(action)
            }
        }
    }

    fun showGreenToast(message: String, @DrawableRes icon: Int) {
        val greenToast = ToastUtils.getGreenToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(greenToast.root)

        greenToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    fun showRedToast(message: String, @DrawableRes icon: Int) {
        val redToast = ToastUtils.getRedToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    private fun areAllPermissionsGranted(): Boolean {
        for (permission in Compatibility.getAllRequiredPermissionsArray()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w("$TAG Permission [$permission] hasn't been granted yet!")
                return false
            }
        }

        val granted = Compatibility.hasFullScreenIntentPermission(this)
        if (granted) {
            Log.i("$TAG All permissions have been granted!")
        }
        return granted
    }
}

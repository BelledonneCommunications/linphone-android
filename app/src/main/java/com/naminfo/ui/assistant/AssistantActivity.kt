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
package com.naminfo.ui.assistant

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import kotlin.math.max
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import com.naminfo.compatibility.Compatibility
import org.linphone.core.tools.Log
import com.naminfo.databinding.AssistantActivityBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.assistant.fragment.PermissionsFragmentDirections

@UiThread
class AssistantActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Assistant Activity]"

        const val SKIP_LANDING_EXTRA = "SkipLandingIfAtLeastAnAccount"
    }

    private lateinit var binding: AssistantActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.assistant_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                insets.left,
                insets.top,
                insets.right,
                max(insets.bottom, keyboard.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }

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
            } else if (intent.getBooleanExtra(SKIP_LANDING_EXTRA, false)) {
                Log.w(
                    "$TAG We were asked to leave assistant if at least an account is already configured"
                )
                coreContext.postOnCoreThread { core ->
                    if (core.accountList.isNotEmpty()) {
                        coreContext.postOnMainThread {
                            try {
                                Log.w("$TAG At least one account was found, leaving assistant")
                                finish()
                            } catch (ise: IllegalStateException) {
                                Log.e("$TAG Can't finish activity: $ise")
                            }
                        }
                    }
                }
            }
        }
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

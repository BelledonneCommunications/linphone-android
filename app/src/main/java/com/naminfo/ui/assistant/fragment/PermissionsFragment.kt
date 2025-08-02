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
package com.naminfo.ui.assistant.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import com.naminfo.compatibility.Compatibility
import org.linphone.core.tools.Log
import com.naminfo.databinding.AssistantPermissionsFragmentBinding
import com.naminfo.ui.GenericFragment
import com.naminfo.ui.assistant.AssistantActivity

@UiThread
class PermissionsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Permissions Fragment]"
    }

    private lateinit var binding: AssistantPermissionsFragmentBinding

    private var leaving = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (isGranted) {
                Log.i("Permission [$permissionName] is now granted")
            } else {
                Log.i("Permission [$permissionName] has been denied")
                allGranted = false
            }
        }

        if (!allGranted) {
            Log.w(
                "$TAG Not all permissions were granted, leaving anyway, they will be asked again later..."
            )
        }
        leave()
    }

    private val telecomManagerPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG MANAGE_OWN_CALLS permission has been granted")
        } else {
            Log.w("$TAG MANAGE_OWN_CALLS permission has been denied, leaving this fragment")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantPermissionsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setSkipClickListener {
            Log.i("$TAG User clicked skip...")
            leave()
        }

        binding.setGrantAllClickListener {
            Log.i("$TAG Requesting all permissions")
            requestPermissionLauncher.launch(
                Compatibility.getAllRequiredPermissionsArray()
            )
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.MANAGE_OWN_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("$TAG Request MANAGE_OWN_CALLS permission")
            telecomManagerPermissionLauncher.launch(Manifest.permission.MANAGE_OWN_CALLS)
        }

        if (!Compatibility.hasFullScreenIntentPermission(requireContext())) {
            Log.w(
                "$TAG Android 14 or newer detected & full screen intent permission hasn't been granted!"
            )
            Compatibility.requestFullScreenIntentPermission(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()

        if (!leaving && areAllPermissionsGranted()) {
            Log.i("$TAG All permissions have been granted, skipping")
            leave()
        }
    }

    private fun leave() {
        if (leaving) return
        leaving = true

        if (requireActivity().intent.getBooleanExtra(AssistantActivity.SKIP_LANDING_EXTRA, false)) {
            Log.w(
                "$TAG We were asked to leave assistant if at least an account is already configured"
            )
            coreContext.postOnCoreThread { core ->
                if (core.accountList.isNotEmpty()) {
                    coreContext.postOnMainThread {
                        Log.w("$TAG At least one account was found, leaving assistant")
                        try {
                            requireActivity().finish()
                        } catch (ise: IllegalStateException) {
                            Log.e("$TAG Failed to finish activity: $ise")
                        }
                    }
                } else {
                    coreContext.postOnMainThread {
                        Log.w("$TAG No account was found, going to landing fragment")
                        try {
                            goToLoginFragment()
                        } catch (ise: IllegalStateException) {
                            Log.e("$TAG Failed to navigate to login fragment: $ise")
                        }
                    }
                }
            }
        } else {
            goToLoginFragment()
        }
    }

    private fun goToLoginFragment() {
        if (findNavController().currentDestination?.id == R.id.permissionsFragment) {
            val action =
                PermissionsFragmentDirections.actionPermissionsFragmentToLandingFragment()
            findNavController().navigate(action)
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        for (permission in Compatibility.getAllRequiredPermissionsArray()) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w("$TAG Permission [$permission] hasn't been granted yet!")
                return false
            }
        }
        return Compatibility.hasFullScreenIntentPermission(requireContext())
    }
}

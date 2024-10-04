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
package org.linphone.activities.main.about

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.databinding.AboutFragmentBinding
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.services.DiagnosticsService
import org.linphone.services.UserService
import org.linphone.utils.Log

class AboutFragment : SecureFragment<AboutFragmentBinding>() {
    private lateinit var viewModel: AboutViewModel
    private var userSubscription: Disposable? = null

    override fun getLayoutId(): Int = R.layout.about_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[AboutViewModel::class.java]
        binding.viewModel = viewModel

        binding.setPrivacyPolicyClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_privacy_policy_link))
            )
            try {
                startActivity(browserIntent)
            } catch (se: SecurityException) {
                Log.e("[About] Failed to start browser intent, $se")
            }
        }

        binding.setLicenseClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_license_link))
            )
            try {
                startActivity(browserIntent)
            } catch (se: SecurityException) {
                Log.e("[About] Failed to start browser intent, $se")
            }
        }

        binding.setWeblateClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_weblate_link))
            )
            try {
                startActivity(browserIntent)
            } catch (se: SecurityException) {
                Log.e("[About] Failed to start browser intent, $se")
            }
        }

        binding.setUploadLogsClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val activity = requireActivity() as MainActivity
                try {
                    DiagnosticsService.uploadDiagnostics(requireContext())
                    activity.showSnackBar("Logs uploaded to server")
                } catch (e: Exception) {
                    Log.e("[About] Failed to upload logs, $e")
                    activity.showSnackBar("Failed to upload logs! " + e.message)
                }
            }
        }

        binding.setClearLogsClickListener {
            try {
                DiagnosticsService.clearLogs(requireContext())

                val activity = requireActivity() as MainActivity
                activity.showSnackBar("Logs have been cleared")
            } catch (se: SecurityException) {
                Log.e("[About] Failed to start browser intent, $se")

                val activity = requireActivity() as MainActivity
                activity.showSnackBar("Failed to clear logs!")
            }
        }

        userSubscription = UserService.getInstance(requireContext()).user
            .subscribe { u -> viewModel.user = u }

        viewModel.region = DimensionsEnvironmentService.getInstance(requireContext()).getCurrentEnvironment()?.name
    }

    override fun onDestroyView() {
        super.onDestroyView()

        userSubscription?.dispose()
    }
}

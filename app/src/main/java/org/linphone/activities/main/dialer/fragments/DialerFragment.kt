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
package org.linphone.activities.main.dialer.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.dialer.viewmodels.DialerViewModel
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToConfigFileViewer
import org.linphone.activities.navigateToContacts
import org.linphone.core.tools.Log
import org.linphone.databinding.DialerFragmentBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils

class DialerFragment : SecureFragment<DialerFragmentBinding>() {
    private lateinit var viewModel: DialerViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    private var uploadLogsInitiatedByUs = false

    override fun getLayoutId(): Int = R.layout.dialer_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(DialerViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        binding.setNewContactClickListener {
            navigateToContacts(viewModel.enteredUri.value)
        }

        binding.setTransferCallClickListener {
            viewModel.transferCall()
            // Transfer has been consumed
            sharedViewModel.pendingCallTransfer = false
            viewModel.transferVisibility.value = false
        }

        if (arguments?.containsKey("Transfer") == true) {
            sharedViewModel.pendingCallTransfer = arguments?.getBoolean("Transfer") ?: false
            Log.i("[Dialer] Is pending call transfer: ${sharedViewModel.pendingCallTransfer}")
        }

        if (arguments?.containsKey("URI") == true) {
            val address = arguments?.getString("URI") ?: ""
            Log.i("[Dialer] Found URI to call: $address")
            val skipAutoCall = arguments?.getBoolean("SkipAutoCallStart") ?: false

            if (corePreferences.callRightAway && !skipAutoCall) {
                Log.i("[Dialer] Call right away setting is enabled, start the call to $address")
                viewModel.directCall(address)
            } else {
                sharedViewModel.dialerUri = address
            }
        }
        arguments?.clear()

        viewModel.enteredUri.observe(viewLifecycleOwner, {
            if (it == corePreferences.debugPopupCode) {
                displayDebugPopup()
                viewModel.enteredUri.value = ""
            }
        })

        viewModel.uploadFinishedEvent.observe(viewLifecycleOwner, {
            it.consume { url ->
                // To prevent being trigger when using the Send Logs button in About page
                if (uploadLogsInitiatedByUs) {
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Logs url", url)
                    clipboard.setPrimaryClip(clip)

                    val activity = requireActivity() as MainActivity
                    activity.showSnackBar(R.string.logs_url_copied_to_clipboard)

                    AppUtils.shareUploadedLogsUrl(activity, url)
                }
            }
        })

        viewModel.updateAvailableEvent.observe(viewLifecycleOwner, {
            it.consume { url ->
                displayNewVersionAvailableDialog(url)
            }
        })

        Log.i("[Dialer] Pending call transfer mode = ${sharedViewModel.pendingCallTransfer}")
        viewModel.transferVisibility.value = sharedViewModel.pendingCallTransfer

        checkForUpdate()
    }

    override fun onPause() {
        sharedViewModel.dialerUri = viewModel.enteredUri.value ?: ""
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        if (resources.getBoolean(R.bool.isTablet)) {
            coreContext.core.nativePreviewWindowId = binding.videoPreviewWindow
        }

        viewModel.updateShowVideoPreview()
        viewModel.autoInitiateVideoCalls.value = coreContext.core.videoActivationPolicy.automaticallyInitiate
        uploadLogsInitiatedByUs = false

        viewModel.enteredUri.value = sharedViewModel.dialerUri
    }

    private fun displayDebugPopup() {
        val alertDialog = MaterialAlertDialogBuilder(requireContext())
        alertDialog.setTitle(getString(R.string.debug_popup_title))

        val items = if (corePreferences.debugLogs) {
            resources.getStringArray(R.array.popup_send_log)
        } else {
            resources.getStringArray(R.array.popup_enable_log)
        }

        alertDialog.setItems(items) { _, which ->
            when (items[which]) {
                getString(R.string.debug_popup_disable_logs) -> {
                    corePreferences.debugLogs = false
                }
                getString(R.string.debug_popup_enable_logs) -> {
                    corePreferences.debugLogs = true
                }
                getString(R.string.debug_popup_send_logs) -> {
                    uploadLogsInitiatedByUs = true
                    viewModel.uploadLogs()
                }
                getString(R.string.debug_popup_show_config_file) -> {
                    navigateToConfigFileViewer()
                }
            }
        }

        alertDialog.show()
    }

    private fun checkForUpdate() {
        val url: String? = corePreferences.checkIfUpdateAvailableUrl
        if (url != null && url.isNotEmpty()) {
            val lastTimestamp: Int = corePreferences.lastUpdateAvailableCheckTimestamp
            val currentTimeStamp = System.currentTimeMillis().toInt()
            val interval: Int = corePreferences.checkUpdateAvailableInterval
            if (lastTimestamp == 0 || currentTimeStamp - lastTimestamp >= interval) {
                val currentVersion = BuildConfig.VERSION_NAME
                Log.i("[Dialer] Checking for update using url [$url] and current version [$currentVersion]")
                coreContext.core.checkForUpdate(currentVersion)
                corePreferences.lastUpdateAvailableCheckTimestamp = currentTimeStamp
            }
        }
    }

    private fun displayNewVersionAvailableDialog(url: String) {
        val viewModel = DialogViewModel(getString(R.string.dialog_update_available))
        val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

        viewModel.showCancelButton {
            dialog.dismiss()
        }

        viewModel.showOkButton({
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
            dialog.dismiss()
        }, getString(R.string.dialog_ok))

        dialog.show()
    }
}

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

import android.Manifest
import android.annotation.TargetApi
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.dialer.viewmodels.DialerViewModel
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToConferenceScheduling
import org.linphone.activities.navigateToConfigFileViewer
import org.linphone.activities.navigateToContacts
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.DialerFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.telecom.TelecomHelper
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class DialerFragment : SecureFragment<DialerFragmentBinding>() {
    private lateinit var viewModel: DialerViewModel

    private var uploadLogsInitiatedByUs = false

    override fun getLayoutId(): Int = R.layout.dialer_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[DialerViewModel::class.java]
        binding.viewModel = viewModel

        useMaterialSharedAxisXForwardAnimation = false
        sharedViewModel.updateDialerAnimationsBasedOnDestination.observe(
            viewLifecycleOwner
        ) {
            it.consume { id ->
                val forward = when (id) {
                    R.id.masterChatRoomsFragment -> false
                    else -> true
                }
                if (corePreferences.enableAnimations) {
                    val portraitOrientation =
                        resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
                    val axis =
                        if (portraitOrientation) MaterialSharedAxis.X else MaterialSharedAxis.Y
                    enterTransition = MaterialSharedAxis(axis, forward)
                    reenterTransition = MaterialSharedAxis(axis, forward)
                    returnTransition = MaterialSharedAxis(axis, !forward)
                    exitTransition = MaterialSharedAxis(axis, !forward)
                }
            }
        }

        binding.setNewContactClickListener {
            sharedViewModel.updateDialerAnimationsBasedOnDestination.value = Event(R.id.masterContactsFragment)
            sharedViewModel.updateContactsAnimationsBasedOnDestination.value = Event(R.id.dialerFragment)
            navigateToContacts(viewModel.enteredUri.value)
        }

        binding.setNewConferenceClickListener {
            sharedViewModel.updateDialerAnimationsBasedOnDestination.value = Event(R.id.conferenceSchedulingFragment)
            navigateToConferenceScheduling()
        }

        binding.setTransferCallClickListener {
            if (viewModel.transferCall()) {
                // Transfer has been consumed, otherwise it might have been a "bis" use
                sharedViewModel.pendingCallTransfer = false
                viewModel.transferVisibility.value = false
                coreContext.onCallStarted()
            }
        }

        viewModel.enteredUri.observe(
            viewLifecycleOwner
        ) {
            if (it == corePreferences.debugPopupCode) {
                displayDebugPopup()
                viewModel.enteredUri.value = ""
            }
        }

        viewModel.uploadFinishedEvent.observe(
            viewLifecycleOwner
        ) {
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
        }

        viewModel.updateAvailableEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { url ->
                displayNewVersionAvailableDialog(url)
            }
        }

        viewModel.onMessageToNotifyEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { id ->
                Toast.makeText(requireContext(), id, Toast.LENGTH_SHORT).show()
            }
        }

        if (corePreferences.firstStart) {
            Log.w("[Dialer] First start detected, wait for assistant to be finished to check for update & request permissions")
            return
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

        Log.i("[Dialer] Pending call transfer mode = ${sharedViewModel.pendingCallTransfer}")
        viewModel.transferVisibility.value = sharedViewModel.pendingCallTransfer

        checkForUpdate()

        checkPermissions()
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

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("[Dialer] READ_PHONE_STATE permission has been granted")
                coreContext.initPhoneStateListener()
            }
            checkPermissions()
        } else if (requestCode == 1) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                }
            }
            if (allGranted) {
                Log.i("[Dialer] Telecom Manager permission have been granted")
                enableTelecomManager()
            } else {
                Log.w("[Dialer] Telecom Manager permission have been denied (at least one of them)")
            }
        } else if (requestCode == 2) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("[Dialer] POST_NOTIFICATIONS permission has been granted")
            }
            checkTelecomManagerPermissions()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun checkPermissions() {
        if (!PermissionHelper.get().hasReadPhoneStatePermission()) {
            Log.i("[Dialer] Asking for READ_PHONE_STATE permission")
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 0)
        } else if (!PermissionHelper.get().hasPostNotificationsPermission()) {
            // Don't check the following the previous permission is being asked
            Log.i("[Dialer] Asking for POST_NOTIFICATIONS permission")
            Compatibility.requestPostNotificationsPermission(this, 2)
        } else if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            // Don't check the following the previous permissions are being asked
            checkTelecomManagerPermissions()
        }
    }

    @TargetApi(Version.API26_O_80)
    private fun checkTelecomManagerPermissions() {
        if (!corePreferences.useTelecomManager) {
            Log.i("[Dialer] Telecom Manager feature is disabled")
            if (corePreferences.manuallyDisabledTelecomManager) {
                Log.w("[Dialer] User has manually disabled Telecom Manager feature")
            } else {
                if (Compatibility.hasTelecomManagerPermissions(requireContext())) {
                    enableTelecomManager()
                } else {
                    Log.i("[Dialer] Asking for Telecom Manager permissions")
                    Compatibility.requestTelecomManagerPermissions(requireActivity(), 1)
                }
            }
        } else {
            Log.i("[Dialer] Telecom Manager feature is already enabled")
        }
    }

    @TargetApi(Version.API26_O_80)
    private fun enableTelecomManager() {
        Log.i("[Dialer] Telecom Manager permissions granted")
        if (!TelecomHelper.exists()) {
            Log.i("[Dialer] Creating Telecom Helper")
            if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) {
                TelecomHelper.create(requireContext())
            } else {
                Log.e("[Dialer] Telecom Helper can't be created, device doesn't support connection service!")
                return
            }
        } else {
            Log.e("[Dialer] Telecom Manager was already created ?!")
        }
        corePreferences.useTelecomManager = true
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
        val lastTimestamp: Int = corePreferences.lastUpdateAvailableCheckTimestamp
        val currentTimeStamp = System.currentTimeMillis().toInt()
        val interval: Int = corePreferences.checkUpdateAvailableInterval
        if (lastTimestamp == 0 || currentTimeStamp - lastTimestamp >= interval) {
            val currentVersion = BuildConfig.VERSION_NAME
            Log.i("[Dialer] Checking for update using current version [$currentVersion]")
            coreContext.core.checkForUpdate(currentVersion)
            corePreferences.lastUpdateAvailableCheckTimestamp = currentTimeStamp
        }
    }

    private fun displayNewVersionAvailableDialog(url: String) {
        val viewModel = DialogViewModel(getString(R.string.dialog_update_available))
        val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

        viewModel.showCancelButton {
            dialog.dismiss()
        }

        viewModel.showOkButton(
            {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e("[Dialer] Can't start ACTION_VIEW intent, IllegalStateException: $ise")
                } finally {
                    dialog.dismiss()
                }
            },
            getString(R.string.dialog_ok)
        )

        dialog.show()
    }
}

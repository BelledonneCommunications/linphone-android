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
package org.linphone.ui.call

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.CallActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.call.fragment.ActiveCallFragmentDirections
import org.linphone.ui.call.fragment.ActiveConferenceCallFragmentDirections
import org.linphone.ui.call.fragment.AudioDevicesMenuDialogFragment
import org.linphone.ui.call.fragment.ConferenceLayoutMenuDialogFragment
import org.linphone.ui.call.fragment.IncomingCallFragmentDirections
import org.linphone.ui.call.fragment.OutgoingCallFragmentDirections
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.call.viewmodel.CallsViewModel
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.ui.call.viewmodel.SharedCallViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.ToastUtils
import org.linphone.utils.slideInToastFromTop
import org.linphone.utils.slideInToastFromTopForDuration

@UiThread
class CallActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Call Activity]"
    }

    private lateinit var binding: CallActivityBinding

    private lateinit var sharedViewModel: SharedCallViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var callViewModel: CurrentCallViewModel

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.call_activity)
        binding.lifecycleOwner = this

        lifecycleScope.launch(Dispatchers.Main) {
            WindowInfoTracker
                .getOrCreate(this@CallActivity)
                .windowLayoutInfo(this@CallActivity)
                .collect { newLayoutInfo ->
                    updateCurrentLayout(newLayoutInfo)
                }
        }

        sharedViewModel = run {
            ViewModelProvider(this)[SharedCallViewModel::class.java]
        }

        callViewModel = run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        binding.callViewModel = callViewModel

        callsViewModel = run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }
        binding.callsViewModel = callsViewModel

        callViewModel.showAudioDevicesListEvent.observe(this) {
            it.consume { devices ->
                showAudioRoutesMenu(devices)
            }
        }

        callViewModel.conferenceModel.showLayoutMenuEvent.observe(this) {
            it.consume {
                showConferenceLayoutMenu()
            }
        }

        callViewModel.showLowWifiSignalEvent.observe(this) {
            it.consume { show ->
                val tag = "LOW_WIFI_SIGNAL"
                if (show) {
                    showPersistentRedToast(
                        getString(R.string.toast_alert_low_wifi_signal),
                        R.drawable.wifi_low,
                        tag
                    )
                } else {
                    removePersistentRedToast(tag)
                    showGreenToast(
                        getString(R.string.toast_alert_low_wifi_signal_cleared),
                        R.drawable.wifi_high,
                        2000
                    )
                }
            }
        }

        callViewModel.showLowCellularSignalEvent.observe(this) {
            it.consume { show ->
                val tag = "LOW_CELLULAR_SIGNAL"
                if (show) {
                    showPersistentRedToast(
                        getString(R.string.toast_alert_low_cellular_signal),
                        R.drawable.cell_signal_low,
                        tag
                    )
                } else {
                    removePersistentRedToast(tag)
                    showGreenToast(
                        getString(R.string.toast_alert_low_cellular_signal_cleared),
                        R.drawable.cell_signal_full,
                        2000
                    )
                }
            }
        }

        callViewModel.transferInProgressEvent.observe(this) {
            it.consume { remote ->
                showGreenToast(
                    getString(R.string.toast_call_transfer_in_progress, remote),
                    R.drawable.phone_transfer
                )
            }
        }

        callViewModel.transferFailedEvent.observe(this) {
            it.consume { remote ->
                showRedToast(
                    getString(R.string.toast_call_transfer_failed, remote),
                    R.drawable.warning_circle
                )
            }
        }

        callViewModel.goToEndedCallEvent.observe(this) {
            it.consume {
                val action = ActiveCallFragmentDirections.actionGlobalEndedCallFragment()
                findNavController(R.id.call_nav_container).navigate(action)
            }
        }

        callsViewModel.showIncomingCallEvent.observe(this) {
            it.consume {
                val action = IncomingCallFragmentDirections.actionGlobalIncomingCallFragment()
                findNavController(R.id.call_nav_container).navigate(action)
            }
        }

        callsViewModel.showOutgoingCallEvent.observe(this) {
            it.consume {
                val action = OutgoingCallFragmentDirections.actionGlobalOutgoingCallFragment()
                findNavController(R.id.call_nav_container).navigate(action)
            }
        }

        callsViewModel.goToActiveCallEvent.observe(this) {
            it.consume { singleCall ->
                val navController = findNavController(R.id.call_nav_container)
                val action = when (navController.currentDestination?.id) {
                    R.id.outgoingCallFragment -> {
                        if (singleCall) {
                            Log.i("$TAG Going from outgoing call fragment to call fragment")
                            OutgoingCallFragmentDirections.actionOutgoingCallFragmentToActiveCallFragment()
                        } else {
                            Log.i(
                                "$TAG Going from outgoing call fragment to conference call fragment"
                            )
                            OutgoingCallFragmentDirections.actionOutgoingCallFragmentToActiveConferenceCallFragment()
                        }
                    }
                    R.id.incomingCallFragment -> {
                        if (singleCall) {
                            Log.i("$TAG Going from incoming call fragment to call fragment")
                            IncomingCallFragmentDirections.actionIncomingCallFragmentToActiveCallFragment()
                        } else {
                            Log.i(
                                "$TAG Going from incoming call fragment to conference call fragment"
                            )
                            IncomingCallFragmentDirections.actionIncomingCallFragmentToActiveConferenceCallFragment()
                        }
                    }
                    R.id.activeConferenceCallFragment -> {
                        if (singleCall) {
                            Log.i("$TAG Going from conference call fragment to call fragment")
                            ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToActiveCallFragment()
                        } else {
                            Log.i(
                                "$TAG Going from conference call fragment to conference call fragment"
                            )
                            ActiveConferenceCallFragmentDirections.actionGlobalActiveConferenceCallFragment()
                        }
                    }
                    else -> {
                        if (singleCall) {
                            Log.i("$TAG Going from call fragment to call fragment")
                            ActiveCallFragmentDirections.actionGlobalActiveCallFragment()
                        } else {
                            Log.i("$TAG Going from call fragment to conference call fragment")
                            ActiveCallFragmentDirections.actionActiveCallFragmentToActiveConferenceCallFragment()
                        }
                    }
                }
                navController.navigate(action)
            }
        }

        callsViewModel.noCallFoundEvent.observe(this) {
            it.consume {
                finish()
            }
        }

        callsViewModel.goToCallsListEvent.observe(this) {
            it.consume {
                val navController = findNavController(R.id.call_nav_container)
                if (navController.currentDestination?.id == R.id.activeCallFragment) {
                    val action =
                        ActiveCallFragmentDirections.actionActiveCallFragmentToCallsListFragment()
                    navController.navigate(action)
                }
            }
        }

        callsViewModel.changeSystemTopBarColorToMultipleCallsEvent.observe(this) {
            it.consume { useInCallColor ->
                val color = if (useInCallColor) {
                    getColor(R.color.success_500)
                } else {
                    getColor(R.color.main1_500)
                }
                window.statusBarColor = color
            }
        }

        sharedViewModel.toggleFullScreenEvent.observe(this) {
            it.consume { hide ->
                hideUI(hide)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    override fun onResume() {
        super.onResume()

        val isInPipMode = isInPictureInPictureMode
        if (::callViewModel.isInitialized) {
            Log.i("$TAG onResume: is in PiP mode? $isInPipMode")
            callViewModel.pipMode.value = isInPipMode
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (::callViewModel.isInitialized && callViewModel.isVideoEnabled.value == true) {
            Log.i("$TAG User leave hint, entering PiP mode")
            val supportsPip = packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
            Log.i("$TAG Is PiP supported: $supportsPip")
            if (supportsPip) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(AppUtils.getPipRatio(this))
                    .build()
                try {
                    if (!enterPictureInPictureMode(params)) {
                        Log.e("$TAG Failed to enter PiP mode")
                        callViewModel.pipMode.value = false
                    } else {
                        Log.i("$TAG Entered PiP mode")
                    }
                } catch (e: Exception) {
                    Log.e("$TAG Can't build PiP params: $e")
                }
            }
        }
    }

    private fun updateCurrentLayout(newLayoutInfo: WindowLayoutInfo) {
        if (newLayoutInfo.displayFeatures.isNotEmpty()) {
            for (feature in newLayoutInfo.displayFeatures) {
                val foldingFeature = feature as? FoldingFeature
                if (foldingFeature != null) {
                    Log.i(
                        "$TAG Folding feature state changed: ${foldingFeature.state}, orientation is ${foldingFeature.orientation}"
                    )
                    sharedViewModel.foldingState.value = foldingFeature
                }
            }
        }
    }

    fun showBlueToast(message: String, @DrawableRes icon: Int, doNotTint: Boolean = false) {
        val blueToast = ToastUtils.getBlueToast(this, binding.toastsArea, message, icon, doNotTint)
        binding.toastsArea.addView(blueToast.root)

        blueToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    private fun showRedToast(
        message: String,
        @DrawableRes icon: Int,
        duration: Long = 4000,
        doNotTint: Boolean = false
    ) {
        val redToast = ToastUtils.getRedToast(this, binding.toastsArea, message, icon, doNotTint)
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope,
            duration
        )
    }

    fun showPersistentRedToast(
        message: String,
        @DrawableRes icon: Int,
        tag: String,
        doNotTint: Boolean = false
    ) {
        val redToast = ToastUtils.getRedToast(this, binding.toastsArea, message, icon, doNotTint)
        redToast.root.tag = tag
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTop(
            binding.toastsArea as ViewGroup,
            true
        )
    }

    fun removePersistentRedToast(tag: String) {
        for (child in binding.toastsArea.children) {
            if (child.tag == tag) {
                binding.toastsArea.removeView(child)
            }
        }
    }

    fun showGreenToast(
        message: String,
        @DrawableRes icon: Int,
        duration: Long = 4000,
        doNotTint: Boolean = false
    ) {
        val greenToast = ToastUtils.getGreenToast(
            this,
            binding.toastsArea,
            message,
            icon,
            doNotTint
        )
        binding.toastsArea.addView(greenToast.root)

        greenToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope,
            duration
        )
    }

    private fun hideUI(hide: Boolean) {
        Log.i("$TAG Switching full screen mode to ${if (hide) "ON" else "OFF"}")
        val windowInsetsCompat = WindowInsetsControllerCompat(window, window.decorView)
        if (hide) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            windowInsetsCompat.let {
                it.hide(WindowInsetsCompat.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            windowInsetsCompat.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    private fun showAudioRoutesMenu(devicesList: List<AudioDeviceModel>) {
        val modalBottomSheet = AudioDevicesMenuDialogFragment(devicesList)
        modalBottomSheet.show(supportFragmentManager, AudioDevicesMenuDialogFragment.TAG)
        bottomSheetDialog = modalBottomSheet
    }

    private fun showConferenceLayoutMenu() {
        val modalBottomSheet = ConferenceLayoutMenuDialogFragment(callViewModel.conferenceModel)
        modalBottomSheet.show(supportFragmentManager, ConferenceLayoutMenuDialogFragment.TAG)
        bottomSheetDialog = modalBottomSheet
    }
}

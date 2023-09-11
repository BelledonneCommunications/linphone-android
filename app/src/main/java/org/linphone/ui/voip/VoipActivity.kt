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
package org.linphone.ui.voip

import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipActivityBinding
import org.linphone.ui.voip.fragment.ActiveCallFragmentDirections
import org.linphone.ui.voip.fragment.AudioDevicesMenuDialogFragment
import org.linphone.ui.voip.fragment.IncomingCallFragmentDirections
import org.linphone.ui.voip.fragment.OutgoingCallFragmentDirections
import org.linphone.ui.voip.model.AudioDeviceModel
import org.linphone.ui.voip.viewmodel.CallsViewModel
import org.linphone.ui.voip.viewmodel.CurrentCallViewModel
import org.linphone.ui.voip.viewmodel.SharedCallViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.slideInToastFromTop
import org.linphone.utils.slideInToastFromTopForDuration

@UiThread
class VoipActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[VoIP Activity]"
    }

    private lateinit var binding: VoipActivityBinding

    private lateinit var sharedViewModel: SharedCallViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var callViewModel: CurrentCallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        val inCallBlackColor = ContextCompat.getColor(
            this,
            R.color.in_call_black
        )
        window.navigationBarColor = inCallBlackColor

        while (!LinphoneApplication.coreContext.isReady()) {
            Thread.sleep(20)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.voip_activity)
        binding.lifecycleOwner = this

        lifecycleScope.launch(Dispatchers.Main) {
            WindowInfoTracker
                .getOrCreate(this@VoipActivity)
                .windowLayoutInfo(this@VoipActivity)
                .collect { newLayoutInfo ->
                    updateCurrentLayout(newLayoutInfo)
                }
        }

        sharedViewModel = run {
            ViewModelProvider(this)[SharedCallViewModel::class.java]
        }

        callsViewModel = run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        callViewModel = run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        callViewModel.showAudioDevicesListEvent.observe(this) {
            it.consume { devices ->
                showAudioRoutesMenu(devices)
            }
        }

        callsViewModel.showIncomingCallEvent.observe(this) {
            it.consume {
                val action = IncomingCallFragmentDirections.actionGlobalIncomingCallFragment()
                findNavController(R.id.voip_nav_container).navigate(action)
            }
        }

        callsViewModel.showOutgoingCallEvent.observe(this) {
            it.consume {
                val action = OutgoingCallFragmentDirections.actionGlobalOutgoingCallFragment()
                findNavController(R.id.voip_nav_container).navigate(action)
            }
        }

        callsViewModel.goToActiveCallEvent.observe(this) {
            it.consume {
                val navController = findNavController(R.id.voip_nav_container)
                val action = when (navController.currentDestination?.id) {
                    R.id.outgoingCallFragment -> {
                        OutgoingCallFragmentDirections.actionOutgoingCallFragmentToActiveCallFragment()
                    }
                    R.id.incomingCallFragment -> {
                        IncomingCallFragmentDirections.actionIncomingCallFragmentToActiveCallFragment()
                    }
                    else -> {
                        ActiveCallFragmentDirections.actionGlobalActiveCallFragment()
                    }
                }
                navController.navigate(action)
            }
        }

        callsViewModel.noMoreCallEvent.observe(this) {
            it.consume {
                finish()
            }
        }

        callsViewModel.showLowSignalEvent.observe(this) {
            it.consume { show ->
                if (show) {
                    showRedToast(
                        getString(R.string.toast_alert_low_wifi_signal),
                        R.drawable.wifi_low
                    )
                } else {
                    hideRedToast()
                    showGreenToast(
                        getString(R.string.toast_alert_low_wifi_signal_cleared),
                        R.drawable.wifi_high
                    )
                }
            }
        }

        sharedViewModel.toggleFullScreenEvent.observe(this) {
            it.consume { hide ->
                hideUI(hide)
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

    fun showBlueToast(message: String, @DrawableRes icon: Int) {
        val blueToast = AppUtils.getBlueToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(blueToast.root)

        blueToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    private fun showRedToast(message: String, @DrawableRes icon: Int) {
        val redToast = AppUtils.getRedToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTop(
            binding.toastsArea as ViewGroup,
            true
        )
    }

    private fun hideRedToast() {
        // TODO: improve
        binding.toastsArea.removeAllViews()
    }

    private fun showGreenToast(message: String, @DrawableRes icon: Int) {
        val greenToast = AppUtils.getGreenToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(greenToast.root)

        greenToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope,
            2000
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
    }
}

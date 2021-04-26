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
package org.linphone.activities.call

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.call.viewmodels.ControlsFadingViewModel
import org.linphone.activities.call.viewmodels.SharedCallViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.CallActivityBinding

class CallActivity : ProximitySensorActivity() {
    private lateinit var binding: CallActivityBinding
    private lateinit var viewModel: ControlsFadingViewModel
    private lateinit var sharedViewModel: SharedCallViewModel

    private var previewX: Float = 0f
    private var previewY: Float = 0f
    private lateinit var videoZoomHelper: VideoZoomHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Compatibility.setShowWhenLocked(this, true)
        Compatibility.setTurnScreenOn(this, true)

        binding = DataBindingUtil.setContentView(this, R.layout.call_activity)
        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(ControlsFadingViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = ViewModelProvider(this).get(SharedCallViewModel::class.java)

        sharedViewModel.toggleDrawerEvent.observe(this, {
            it.consume {
                if (binding.statsMenu.isDrawerOpen(Gravity.LEFT)) {
                    binding.statsMenu.closeDrawer(binding.sideMenuContent, true)
                } else {
                    binding.statsMenu.openDrawer(binding.sideMenuContent, true)
                }
            }
        })

        sharedViewModel.resetHiddenInterfaceTimerInVideoCallEvent.observe(this, {
            it.consume {
                viewModel.showMomentarily()
            }
        })

        coreContext.core.nativeVideoWindowId = binding.remoteVideoSurface
        coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface

        binding.setPreviewTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previewX = v.x - event.rawX
                    previewY = v.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + previewX)
                        .y(event.rawY + previewY)
                        .setDuration(0)
                        .start()
                }
                else -> {
                    v.performClick()
                    false
                }
            }
            true
        }

        videoZoomHelper = VideoZoomHelper(this, binding.remoteVideoSurface)

        viewModel.proximitySensorEnabled.observe(this, {
            enableProximitySensor(it)
        })
    }

    override fun onResume() {
        super.onResume()

        if (coreContext.core.callsNb == 0) {
            Log.w("[Call Activity] Resuming but no call found...")
            finish()
        } else {
            coreContext.removeCallOverlay()
        }

        if (corePreferences.fullScreenCallUI) {
            hideSystemUI()
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    GlobalScope.launch {
                        delay(2000)
                        withContext(Dispatchers.Main) {
                            hideSystemUI()
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        val core = coreContext.core
        if (core.callsNb > 0) {
            coreContext.createCallOverlay()
        }

        super.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (coreContext.isVideoCallOrConferenceActive()) {
            Compatibility.enterPipMode(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (isInPictureInPictureMode) {
            viewModel.areControlsHidden.value = true
        }

        if (corePreferences.hideCameraPreviewInPipMode) {
            viewModel.isVideoPreviewHidden.value = isInPictureInPictureMode
        } else {
            viewModel.isVideoPreviewResizedForPip.value = isInPictureInPictureMode
        }
    }

    override fun getTheme(): Resources.Theme {
        val theme = super.getTheme()
        if (corePreferences.fullScreenCallUI) {
            theme.applyStyle(R.style.FullScreenTheme, true)
        }
        return theme
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}

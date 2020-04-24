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
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
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

        binding = DataBindingUtil.setContentView(this, R.layout.call_activity)
        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(ControlsFadingViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = ViewModelProvider(this).get(SharedCallViewModel::class.java)

        sharedViewModel.toggleDrawerEvent.observe(this, Observer {
            it.consume {
                if (binding.statsMenu.isDrawerOpen(Gravity.LEFT)) {
                    binding.statsMenu.closeDrawer(binding.sideMenuContent, true)
                } else {
                    binding.statsMenu.openDrawer(binding.sideMenuContent, true)
                }
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
                    v.animate().x(event.rawX + previewX).y(event.rawY + previewY).setDuration(0).start()
                }
                else -> false
            }
            true
        }

        videoZoomHelper = VideoZoomHelper(this, binding.remoteVideoSurface)

        viewModel.videoEnabledEvent.observe(this, Observer {
            it.consume { videoEnabled ->
                enableProximitySensor(!videoEnabled)
            }
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

        if (coreContext.core.currentCall?.currentParams?.videoEnabled() == true) {
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
        viewModel.isVideoPreviewHidden.value = isInPictureInPictureMode
    }
}

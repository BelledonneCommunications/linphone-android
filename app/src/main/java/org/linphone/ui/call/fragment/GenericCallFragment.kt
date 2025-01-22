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
package org.linphone.ui.call.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import org.linphone.core.tools.Log
import org.linphone.ui.GenericFragment
import org.linphone.ui.call.view.RoundCornersTextureView
import org.linphone.ui.call.viewmodel.SharedCallViewModel

@UiThread
abstract class GenericCallFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Generic Call Fragment]"
    }

    protected lateinit var sharedViewModel: SharedCallViewModel

    // For moving video preview purposes
    private val videoPreviewTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                sharedViewModel.videoPreviewX = view.x - event.rawX
                sharedViewModel.videoPreviewY = view.y - event.rawY
                true
            }
            MotionEvent.ACTION_UP -> {
                sharedViewModel.videoPreviewX = view.x
                sharedViewModel.videoPreviewY = view.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + sharedViewModel.videoPreviewX)
                    .y(event.rawY + sharedViewModel.videoPreviewY)
                    .setDuration(0)
                    .start()
                true
            }
            else -> {
                view.performClick()
                false
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedCallViewModel::class.java]
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun setupVideoPreview(localPreviewVideoSurface: RoundCornersTextureView) {
        if (requireActivity().isInPictureInPictureMode) {
            Log.i("$TAG Activity is in PiP mode, do not move video preview")
            return
        }

        // To restore video preview position if possible
        if (sharedViewModel.videoPreviewX != 0f && sharedViewModel.videoPreviewY != 0f) {
            Log.i("$TAG Restoring video preview position with position X [${sharedViewModel.videoPreviewX}] and Y [${sharedViewModel.videoPreviewY}]")
            localPreviewVideoSurface.x = sharedViewModel.videoPreviewX
            localPreviewVideoSurface.y = sharedViewModel.videoPreviewY
        }

        localPreviewVideoSurface.setOnTouchListener(videoPreviewTouchListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun cleanVideoPreview(localPreviewVideoSurface: RoundCornersTextureView) {
        localPreviewVideoSurface.setOnTouchListener(null)
    }
}

/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
package org.linphone.activities.voip.fragments

import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import androidx.databinding.ViewDataBinding
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.GenericFragment

abstract class GenericVideoPreviewFragment<T : ViewDataBinding> : GenericFragment<T>() {
    private var previewX: Float = 0f
    private var previewY: Float = 0f
    private var switchX: Float = 0f
    private var switchY: Float = 0f

    private var switchCameraImageView: ImageView? = null
    private lateinit var videoPreviewTextureView: TextureView

    private val previewTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previewX = view.x - event.rawX
                previewY = view.y - event.rawY
                switchX = (switchCameraImageView?.x ?: 0f) - event.rawX
                switchY = (switchCameraImageView?.y ?: 0f) - event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + previewX)
                    .y(event.rawY + previewY)
                    .setDuration(0)
                    .start()
                switchCameraImageView?.apply {
                    animate()
                        .x(event.rawX + switchX)
                        .y(event.rawY + switchY)
                        .setDuration(0)
                        .start()
                }
                true
            }
            else -> {
                view.performClick()
                false
            }
        }
    }

    protected fun setupLocalViewPreview(localVideoPreview: TextureView, switchCamera: ImageView?) {
        videoPreviewTextureView = localVideoPreview
        switchCameraImageView = switchCamera
        videoPreviewTextureView.setOnTouchListener(previewTouchListener)
    }

    override fun onResume() {
        super.onResume()

        if (::videoPreviewTextureView.isInitialized) {
            coreContext.core.nativePreviewWindowId = videoPreviewTextureView
        }
    }
}

/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.voip.views

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import org.linphone.R
import org.linphone.mediastream.video.capture.CaptureTextureView

class RoundCornersTextureView : CaptureTextureView {
    constructor(context: Context) : super(context) {
        // Default values for one to one video call
        mAlignTopRight = true
        mDisplayMode = DisplayMode.BLACK_BARS
        setRoundCorners()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        readAttributes(attrs)
        setRoundCorners()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        readAttributes(attrs)
        setRoundCorners()
    }

    private fun readAttributes(attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RoundCornersTextureView,
            0,
            0
        ).apply {
            try {
                mAlignTopRight = getBoolean(R.styleable.RoundCornersTextureView_alignTopRight, true)
                val mode = getInteger(R.styleable.RoundCornersTextureView_displayMode, DisplayMode.BLACK_BARS.ordinal)
                mDisplayMode = when (mode) {
                    1 -> DisplayMode.OCCUPY_ALL_SPACE
                    2 -> DisplayMode.HYBRID
                    else -> DisplayMode.BLACK_BARS
                }
            } finally {
                recycle()
            }
        }
    }

    private fun setRoundCorners() {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val rect = if (actualDisplayMode == DisplayMode.BLACK_BARS && mAlignTopRight) {
                    val previewRect = previewRectF ?: return
                    Rect(
                        previewRect.left.toInt(),
                        previewRect.top.toInt(),
                        previewRect.right.toInt(),
                        previewRect.bottom.toInt()
                    )
                } else {
                    Rect(
                        0,
                        0,
                        width,
                        height
                    )
                }
                outline.setRoundRect(rect, context.resources.getDimension(R.dimen.voip_round_corners_texture_view_radius))
            }
        }
        clipToOutline = true
    }

    override fun setAspectRatio(width: Int, height: Int) {
        super.setAspectRatio(width, height)

        val previewSize = previewVideoSize
        if (previewSize.width > 0 && previewSize.height > 0) {
            setRoundCorners()
        }
    }
}

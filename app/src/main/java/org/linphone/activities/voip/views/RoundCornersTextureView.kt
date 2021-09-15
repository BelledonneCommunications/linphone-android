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
import org.linphone.utils.AppUtils

class RoundCornersTextureView : CaptureTextureView {
    constructor(context: Context) : super(context) {
        setRoundCorners()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setRoundCorners()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        setRoundCorners()
    }

    private fun setRoundCorners() {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val rect = Rect(0, 0, view.measuredWidth, view.measuredHeight)
                outline.setRoundRect(rect, AppUtils.getDimension(R.dimen.voip_round_corners_texture_view_radius))
            }
        }
        clipToOutline = true
    }

    override fun rotateToMatchDisplayOrientation(rotation: Int) {
        super.rotateToMatchDisplayOrientation(rotation)

        if (mCapturedVideoWidth != 0 && mCapturedVideoHeight != 0) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    var ratioWidth = view.measuredWidth
                    var ratioHeight = 0
                    if (width < height * mCapturedVideoWidth / mCapturedVideoHeight) {
                        ratioHeight = width * mCapturedVideoHeight / mCapturedVideoWidth
                    } else {
                        ratioWidth = height * mCapturedVideoWidth / mCapturedVideoHeight
                    }

                    val rect = Rect(view.measuredWidth - ratioWidth, 0, view.measuredWidth, view.measuredHeight - ratioHeight)
                    outline.setRoundRect(
                        rect,
                        AppUtils.getDimension(R.dimen.voip_round_corners_texture_view_radius)
                    )
                }
            }
            clipToOutline = true
        }
    }
}

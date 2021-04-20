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
package org.linphone.activities.main.chat.views

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.ceil

/**
 * The purpose of this class is to have a TextView declared with wrap_content as width that won't
 * fill it's parent if it is multi line.
 */
class MultiLineWrapContentWidthTextView : AppCompatTextView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var wSpec = widthSpec
        val widthMode = MeasureSpec.getMode(wSpec)

        if (widthMode == MeasureSpec.AT_MOST) {
            val layout = layout
            if (layout != null) {
                val maxWidth = (ceil(getMaxLineWidth(layout).toDouble()).toInt() +
                        totalPaddingLeft +
                        totalPaddingRight)
                wSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            }
        }

        super.onMeasure(wSpec, heightSpec)
    }

    private fun getMaxLineWidth(layout: Layout): Float {
        var maxWidth = 0.0f
        val lines = layout.lineCount
        for (i in 0 until lines) {
            if (layout.getLineWidth(i) > maxWidth) {
                maxWidth = layout.getLineWidth(i)
            }
        }
        return maxWidth
    }
}

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
package com.naminfo.utils

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import java.util.regex.Pattern

@AnyThread
class PatternClickableSpan {
    private var patterns: ArrayList<SpannablePatternItem> = ArrayList()

    inner class SpannablePatternItem(
        var pattern: Pattern,
        var listener: SpannableClickedListener
    )

    class StyledClickableSpan(var listener: SpannableClickedListener) : ClickableSpan() {
        override fun onClick(widget: View) {
            val tv = widget as TextView
            val span = tv.text as Spanned
            val start = span.getSpanStart(this)
            val end = span.getSpanEnd(this)
            val text = span.subSequence(start, end)
            listener.onSpanClicked(text.toString())
        }
    }

    fun add(
        pattern: Pattern,
        listener: SpannableClickedListener
    ): PatternClickableSpan {
        patterns.add(SpannablePatternItem(pattern, listener))
        return this
    }

    fun build(ssb: SpannableStringBuilder): SpannableStringBuilder {
        for (item in patterns) {
            val matcher = item.pattern.matcher(ssb)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val url = StyledClickableSpan(item.listener)
                ssb.setSpan(url, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return ssb
    }
}

interface SpannableClickedListener {
    @UiThread
    fun onSpanClicked(text: String)
}

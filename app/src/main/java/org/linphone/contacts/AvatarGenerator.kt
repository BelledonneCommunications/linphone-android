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
package org.linphone.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import org.linphone.R
import org.linphone.utils.AppUtils
import androidx.core.graphics.createBitmap

class AvatarGenerator(private val context: Context) {
    private var textSize: Float = AppUtils.getDimension(R.dimen.avatar_initials_text_size)
    private var textColor: Int = ContextCompat.getColor(context, R.color.gray_main2_600)
    private var avatarSize: Int = AppUtils.getDimension(R.dimen.avatar_list_cell_size).toInt()
    private var initials = " "
    private var transparentColor: Int = ContextCompat.getColor(context, R.color.transparent_color)
    private var backgroundColor: Int = ContextCompat.getColor(context, R.color.gray_main2_200)

    init {
        val textTypedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.color_avatar_text, textTypedValue, true)
        // This will fail for notifications
        if (textTypedValue.data != 0) {
            textColor = textTypedValue.data
        }

        val backgroundTypedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.color_avatar_background, backgroundTypedValue, true)
        // This will fail for notifications
        if (backgroundTypedValue.data != 0) {
            backgroundColor = backgroundTypedValue.data
        }
    }

    fun setTextSize(size: Float) = apply {
        textSize = size
    }

    fun setAvatarSize(size: Int) = apply {
        avatarSize = size
    }

    fun setInitials(label: String) = apply {
        initials = label
    }

    fun buildBitmap(useTransparentBackground: Boolean): Bitmap {
        val textPainter = getTextPainter()
        val painter = if (useTransparentBackground) getTransparentPainter() else getBackgroundPainter()

        val bitmap = createBitmap(avatarSize, avatarSize)
        val canvas = Canvas(bitmap)
        val areaRect = Rect(0, 0, avatarSize, avatarSize)
        val bounds = RectF(areaRect)
        bounds.right = textPainter.measureText(initials, 0, initials.length)
        bounds.bottom = textPainter.descent() - textPainter.ascent()
        bounds.left += (areaRect.width() - bounds.right) / 2.0f
        bounds.top += (areaRect.height() - bounds.bottom) / 2.0f

        val halfSize = (avatarSize / 2).toFloat()
        canvas.drawCircle(halfSize, halfSize, halfSize, painter)
        canvas.drawText(initials, bounds.left, bounds.top - textPainter.ascent(), textPainter)

        return bitmap
    }

    fun buildIcon(): IconCompat {
        return IconCompat.createWithAdaptiveBitmap(buildBitmap(false))
    }

    private fun getTextPainter(): TextPaint {
        val textPainter = TextPaint()
        textPainter.isAntiAlias = true
        textPainter.textSize = textSize
        textPainter.color = textColor
        textPainter.typeface = ResourcesCompat.getFont(context, R.font.noto_sans_800)
        return textPainter
    }

    private fun getTransparentPainter(): Paint {
        val painter = Paint()
        painter.isAntiAlias = true
        painter.color = transparentColor
        return painter
    }

    private fun getBackgroundPainter(): Paint {
        val painter = Paint()
        painter.isAntiAlias = true
        painter.color = backgroundColor
        return painter
    }
}

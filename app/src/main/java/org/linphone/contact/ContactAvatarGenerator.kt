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
package org.linphone.contact

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.ContextCompat
import org.linphone.R
import org.linphone.utils.AppUtils

class ContactAvatarGenerator(private val context: Context) {
    private var textSize: Float
    private var textColor: Int
    private var avatarSize: Int
    private var name = " "
    private var backgroundColor: Int

    init {
        val theme = context.theme

        val backgroundColorTypedValue = TypedValue()
        theme.resolveAttribute(R.attr.primaryTextColor, backgroundColorTypedValue, true)
        backgroundColor = ContextCompat.getColor(context, backgroundColorTypedValue.resourceId)

        val textColorTypedValue = TypedValue()
        theme.resolveAttribute(R.attr.secondaryTextColor, textColorTypedValue, true)
        textColor = ContextCompat.getColor(context, textColorTypedValue.resourceId)

        textSize = AppUtils.getDimension(R.dimen.contact_avatar_text_size)

        avatarSize = AppUtils.getDimension(R.dimen.contact_avatar_size).toInt()
    }

    fun setTextSize(size: Float) = apply {
        textSize = size
    }

    fun setTextColorResource(resource: Int) = apply {
        textColor = ContextCompat.getColor(context, resource)
    }

    fun setAvatarSize(size: Int) = apply {
        avatarSize = size
    }

    fun setLabel(label: String) = apply {
        name = label
    }

    fun setBackgroundColorAttribute(attribute: Int) = apply {
        val theme = context.theme
        val backgroundColorTypedValue = TypedValue()
        theme.resolveAttribute(attribute, backgroundColorTypedValue, true)
        backgroundColor = ContextCompat.getColor(context, backgroundColorTypedValue.resourceId)
    }

    fun build(): BitmapDrawable {
        val label = AppUtils.getInitials(name)
        val textPainter = getTextPainter()
        val painter = getPainter()

        val bitmap = Bitmap.createBitmap(avatarSize, avatarSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val areaRect = Rect(0, 0, avatarSize, avatarSize)
        val bounds = RectF(areaRect)
        bounds.right = textPainter.measureText(label, 0, label.length)
        bounds.bottom = textPainter.descent() - textPainter.ascent()
        bounds.left += (areaRect.width() - bounds.right) / 2.0f
        bounds.top += (areaRect.height() - bounds.bottom) / 2.0f

        val halfSize = (avatarSize / 2).toFloat()
        canvas.drawCircle(halfSize, halfSize, halfSize, painter)
        canvas.drawText(label, bounds.left, bounds.top - textPainter.ascent(), textPainter)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun getTextPainter(): TextPaint {
        val textPainter = TextPaint()
        textPainter.isAntiAlias = true
        textPainter.textSize = textSize
        textPainter.color = textColor
        textPainter.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        return textPainter
    }

    private fun getPainter(): Paint {
        val painter = Paint()
        painter.isAntiAlias = true
        painter.color = backgroundColor
        return painter
    }
}

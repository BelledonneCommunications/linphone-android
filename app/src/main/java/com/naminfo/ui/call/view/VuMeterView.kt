/*
 * Copyright (c) 2010-2025 Belledonne Communications SARL.
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
package com.naminfo.ui.call.view

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.naminfo.R

class VuMeterView : View {
    companion object {
        private const val TAG = "[VuMeter View]"
    }

    private lateinit var paint: Paint
    private lateinit var matrix: Matrix
    private lateinit var vuMeterPaint: Paint

    private val color = ContextCompat.getColor(context, R.color.vu_meter)

    private var vuMeterPercentage: Float = 0f

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init()
    }

    private fun init() {
        paint = Paint()
        paint.isAntiAlias = true
        matrix = Matrix()

        vuMeterPaint = Paint()
        vuMeterPaint.strokeWidth = 2f
        vuMeterPaint.isAntiAlias = true
        vuMeterPaint.color = color
    }

    fun setVuMeterPercentage(percentage: Float) {
        vuMeterPercentage = percentage
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        createShader()
    }

    private fun createShader(): Shader {
        val level = (height - height * vuMeterPercentage).toFloat()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, height.toFloat(), width.toFloat(), level, vuMeterPaint)

        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        return shader
    }

    override fun onDraw(canvas: Canvas) {
        paint.shader = createShader()
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
    }
}

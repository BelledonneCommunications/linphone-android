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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.HorizontalScrollView
import java.lang.Exception
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils

class HorizontalScrollDotsView : View {
    private var count = 2
    private var selected = 0

    private var radius: Float = 5f
    private var margin: Float = 2f
    private var screenWidth: Float = 0f
    private var itemWidth: Float = 0f

    private lateinit var dotPaint: Paint
    private lateinit var selectedDotPaint: Paint

    private var horizontalScrollViewRef = 0
    private lateinit var horizontalScrollView: HorizontalScrollView
    private val scrollListener = OnScrollChangeListener { v, scrollX, _, _, _ ->
        val childWidth: Int = (v as HorizontalScrollView).getChildAt(0).measuredWidth
        val scrollViewWidth = v.measuredWidth
        val scrollableX = childWidth - scrollViewWidth

        if (scrollableX > 0) {
            val percent = (scrollX.toFloat() * 100 / scrollableX).toDouble()
            if (count > 1) {
                val selectedDot = percent / (100 / (count - 1))
                val dot = selectedDot.roundToInt()
                if (dot != selected) {
                    setSelectedDot(dot)
                }
            }
        }
    }

    constructor(context: Context) : super(context) { init(context) }

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.HorizontalScrollDot,
            defStyleAttr, 0
        ).apply {
            try {
                radius = getDimension(R.styleable.HorizontalScrollDot_dotRadius, 5f)

                count = getInt(R.styleable.HorizontalScrollDot_dotCount, 1)

                val color = getColor(R.styleable.HorizontalScrollDot_dotColor, context.resources.getColor(R.color.voip_gray_background))
                dotPaint.color = color
                val selectedColor = getColor(R.styleable.HorizontalScrollDot_selectedDotColor, context.resources.getColor(R.color.voip_dark_gray))
                selectedDotPaint.color = selectedColor

                selected = getInt(R.styleable.HorizontalScrollDot_selectedDot, 1)

                horizontalScrollViewRef = getResourceId(R.styleable.HorizontalScrollDot_horizontalScrollView, 0)
                Log.d("[Horizontal Scroll Dots] HorizontalScrollView reference set is $horizontalScrollViewRef")

                invalidate()
            } catch (e: Exception) {
                Log.e("[Horizontal Scroll Dots] $e")
            } finally {
                recycle()
            }
        }
    }

    fun init(context: Context) {
        radius = AppUtils.dpToPixels(context, 5f)
        margin = AppUtils.dpToPixels(context, 5f)

        dotPaint = Paint()
        dotPaint.color = Color.parseColor("#D8D8D8")
        selectedDotPaint = Paint()
        selectedDotPaint.color = Color.parseColor("#4B5964")

        val screenRect = Rect()
        getWindowVisibleDisplayFrame(screenRect)
        screenWidth = screenRect.width().toFloat()
        val marginBetweenItems = context.resources.getDimension(R.dimen.voip_active_speaker_miniature_margin)
        itemWidth = context.resources.getDimension(R.dimen.voip_active_speaker_miniature_size) + marginBetweenItems
        Log.d("[Horizontal Scroll Dots] Screen width is $screenWidth and item width is $itemWidth")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (horizontalScrollViewRef > 0) {
            try {
                horizontalScrollView = (parent as View).findViewById(horizontalScrollViewRef)
                horizontalScrollView.setOnScrollChangeListener(scrollListener)
                Log.d("[Horizontal Scroll Dots] HorizontalScrollView scroll listener set")
            } catch (e: Exception) {
                Log.e("[Horizontal Scroll Dots] Failed to find HorizontalScrollView from id $horizontalScrollViewRef: $e")
            }
        } else {
            Log.e("[Horizontal Scroll Dots] No HorizontalScrollView reference given")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0 until count) {
            if (i == selected) {
                canvas.drawCircle(
                    (i + 1) * margin + (i * 2 + 1) * radius,
                    radius,
                    radius,
                    selectedDotPaint
                )
            } else {
                canvas.drawCircle(
                    (i + 1) * margin + (i * 2 + 1) * radius,
                    radius,
                    radius,
                    dotPaint
                )
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = ((radius * 2 + margin) * count + margin).toInt()
        val height: Int = (radius * 2).toInt()

        setMeasuredDimension(width, height)
    }

    fun setDotCount(count: Int) {
        this.count = count
        requestLayout()
        invalidate()
    }

    fun setItemCount(items: Int) {
        val itemsPerScreen = (screenWidth / itemWidth)
        val dots = ceil(items.toDouble() / itemsPerScreen).toInt()

        Log.d("[Horizontal Scroll Dots] Calculated $count for $items items ($itemsPerScreen items fit in screen width), given that screen width is $screenWidth and item width is $itemWidth")
        setDotCount(dots)
    }

    fun setSelectedDot(index: Int) {
        selected = index
        invalidate()
    }
}

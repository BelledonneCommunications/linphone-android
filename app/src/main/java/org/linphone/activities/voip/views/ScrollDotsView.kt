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
import android.view.View.OnScrollChangeListener
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils

class ScrollDotsView : View {
    private var count = 2
    private var itemCount = 0
    private var selected = 0

    private var radius: Float = 5f
    private var margin: Float = 2f

    private var screenWidth: Float = 0f
    private var itemWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var itemHeight: Float = 0f

    private lateinit var dotPaint: Paint
    private lateinit var selectedDotPaint: Paint

    private var scrollViewRef = 0
    private lateinit var scrollView: FrameLayout
    private var isHorizontal = false

    private val scrollListener = OnScrollChangeListener { v, scrollX, scrollY, _, _ ->
        if (isHorizontal) {
            if (v !is HorizontalScrollView) {
                Log.e("[Scoll Dots] ScrollView reference isn't a HorizontalScrollView!")
                return@OnScrollChangeListener
            }

            val childWidth: Int = v.getChildAt(0).measuredWidth
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
        } else {
            if (v !is ScrollView) {
                Log.e("[Scoll Dots] ScrollView reference isn't a ScrollView!")
                return@OnScrollChangeListener
            }

            val childHeight: Int = v.getChildAt(0).measuredHeight
            val scrollViewHeight = v.measuredHeight
            val scrollableY = childHeight - scrollViewHeight

            if (scrollableY > 0) {
                val percent = (scrollY.toFloat() * 100 / scrollableY).toDouble()
                if (count > 1) {
                    val selectedDot = percent / (100 / (count - 1))
                    val dot = selectedDot.roundToInt()
                    if (dot != selected) {
                        setSelectedDot(dot)
                    }
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
            R.styleable.ScrollDot,
            defStyleAttr, 0
        ).apply {
            try {
                radius = getDimension(R.styleable.ScrollDot_dotRadius, 5f)

                count = getInt(R.styleable.ScrollDot_dotCount, 1)

                val color = getColor(R.styleable.ScrollDot_dotColor, context.resources.getColor(R.color.voip_gray_dots))
                dotPaint.color = color
                val selectedColor = getColor(R.styleable.ScrollDot_selectedDotColor, context.resources.getColor(R.color.voip_dark_gray))
                selectedDotPaint.color = selectedColor

                selected = getInt(R.styleable.ScrollDot_selectedDot, 1)

                scrollViewRef = getResourceId(R.styleable.ScrollDot_scrollView, 0)

                invalidate()
            } catch (e: Exception) {
                Log.e("[Scroll Dots] $e")
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
        screenHeight = screenRect.height().toFloat()

        val marginBetweenItems = context.resources.getDimension(R.dimen.voip_active_speaker_miniature_margin)
        itemWidth = context.resources.getDimension(R.dimen.voip_active_speaker_miniature_size) + marginBetweenItems
        itemHeight = context.resources.getDimension(R.dimen.voip_active_speaker_miniature_size) + marginBetweenItems

        Log.d("[Scroll Dots] Screen size is $screenWidth/$screenHeight and item size is $itemWidth/$itemHeight")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0 until count) {
            if (i == selected) {
                val position = (i + 1) * margin + (i * 2 + 1) * radius
                canvas.drawCircle(
                    if (isHorizontal) position else radius,
                    if (isHorizontal) radius else position,
                    radius,
                    selectedDotPaint
                )
            } else {
                val position = (i + 1) * margin + (i * 2 + 1) * radius
                canvas.drawCircle(
                    if (isHorizontal) position else radius,
                    if (isHorizontal) radius else position,
                    radius,
                    dotPaint
                )
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        checkOrientation()

        if (isHorizontal) {
            val width = ((radius * 2 + margin) * count + margin).toInt()
            val height: Int = (radius * 2).toInt()
            setMeasuredDimension(width, height)
        } else {
            val height = ((radius * 2 + margin) * count + margin).toInt()
            val width: Int = (radius * 2).toInt()
            setMeasuredDimension(width, height)
        }
    }

    private fun checkOrientation() {
        if (scrollViewRef > 0) {
            try {
                scrollView = (parent as View).findViewById(scrollViewRef)
                scrollView.setOnScrollChangeListener(scrollListener)
                Log.d("[Scroll Dots] ScrollView scroll listener set")
                isHorizontal = scrollView is HorizontalScrollView
                Log.d("[Scroll Dots] ScrollView is horizontal ? $isHorizontal")
                requestLayout()
                setItemCount(itemCount)
            } catch (e: Exception) {
                Log.e("[Scroll Dots] Failed to find ScrollView from id $scrollViewRef: $e")
            }
        } else {
            Log.e("[Scroll Dots] No ScrollView reference given")
        }
    }

    private fun setDotCount(count: Int) {
        this.count = count
        requestLayout()
        invalidate()
    }

    fun setItemCount(items: Int) {
        itemCount = items
        if (isHorizontal) {
            val itemsPerScreen = (screenWidth / itemWidth)
            val dots = ceil(items.toDouble() / itemsPerScreen).toInt()
            Log.d("[Scroll Dots] Calculated $count for $items items ($itemsPerScreen items fit in screen width), given that screen width is $screenWidth and item width is $itemWidth")
            setDotCount(dots)
        } else {
            val itemsPerScreen = (screenHeight / itemHeight)
            val dots = ceil(items.toDouble() / itemsPerScreen).toInt()
            Log.d("[Scroll Dots] Calculated $count for $items items ($itemsPerScreen items fit in screen height), given that screen height is $screenHeight and item height is $itemHeight")
            setDotCount(dots)
        }
    }

    fun setSelectedDot(index: Int) {
        selected = index
        invalidate()
    }
}

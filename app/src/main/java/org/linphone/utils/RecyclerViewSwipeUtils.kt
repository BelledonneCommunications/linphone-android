/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package org.linphone.utils

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.RecyclerView

class RecyclerViewSwipeUtils(callbacks: RecyclerViewSwipeUtilsCallback) : ItemTouchHelper(callbacks)

class RecyclerViewSwipeUtilsCallback(val rightButton: View? = null) : ItemTouchHelper.Callback() {
    companion object {
        private const val TAG = "[RecyclerViewSwipeUtilsCallback]"
    }

    private var swipeBack: Boolean = false
    private var rightButtonWidth: Int = 0

    init {
        if (rightButton != null) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            rightButton.measure(widthSpec, heightSpec)
            rightButtonWidth = rightButton.measuredWidth
        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(0, LEFT or RIGHT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ACTION_STATE_SWIPE) {
            recyclerView.setOnTouchListener { _, event ->
                swipeBack =
                    event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP

                val showRightButton = (rightButtonWidth != 0 && dX < -rightButtonWidth)
                val position = viewHolder.bindingAdapterPosition
                val clickable = !showRightButton || swipeBack
                try {
                    recyclerView.getChildAt(position).isClickable = clickable
                } catch (e: IndexOutOfBoundsException) {
                }
                if (rightButton != null && showRightButton) {
                    val itemView = viewHolder.itemView
                    val left = (itemView.right - rightButton.measuredWidth)
                    val top = itemView.top

                    canvas.save()
                    canvas.translate(left.toFloat(), top.toFloat())
                    rightButton.layout(
                        0,
                        0,
                        rightButton.width,
                        rightButton.height
                    )
                    rightButton.draw(canvas)
                    canvas.restore()
                }

                false
            }
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }
}

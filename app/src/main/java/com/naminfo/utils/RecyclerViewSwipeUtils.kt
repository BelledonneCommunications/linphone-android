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
package com.naminfo.utils

import android.annotation.SuppressLint
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.RecyclerView

class RecyclerViewSwipeUtils(
    callbacks: RecyclerViewSwipeUtilsCallback
) : ItemTouchHelper(callbacks)

class RecyclerViewSwipeUtilsCallback(
    @DrawableRes private val icon: Int,
    private val disableActionForViewHolderClass: Class<*>? = null,
    private val onSwiped: ((viewHolder: RecyclerView.ViewHolder) -> Unit)? = null
) : ItemTouchHelper.Callback() {
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (disableActionForViewHolderClass?.isInstance(viewHolder) == true) {
            return makeMovementFlags(0, 0)
        }
        return makeMovementFlags(0, RIGHT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) {
        if (direction == RIGHT) {
            onSwiped?.invoke(viewHolder)
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return .2f // Percentage of the screen width the swipe action has to reach to validate swipe move (default is .5f)
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
            val iconDrawable = ContextCompat.getDrawable(recyclerView.context, icon)
            val iconWidth = iconDrawable?.intrinsicWidth ?: 0
            val margin = 20
            if (iconDrawable != null && dX > iconWidth + margin) {
                val halfIcon = iconDrawable.intrinsicHeight / 2
                val top =
                    viewHolder.itemView.top + ((viewHolder.itemView.bottom - viewHolder.itemView.top) / 2 - halfIcon)

                // Icon won't move past the swipe threshold, thus indicating to the user
                // it has reached the required distance for swipe action to be done
                val threshold = getSwipeThreshold(viewHolder) * viewHolder.itemView.right
                val left = if (dX < threshold) {
                    viewHolder.itemView.left + dX.toInt() - iconWidth - margin
                } else {
                    viewHolder.itemView.left + threshold.toInt() - iconWidth - margin
                }

                iconDrawable.setBounds(
                    left,
                    top,
                    left + iconWidth,
                    top + iconDrawable.intrinsicHeight
                )
                iconDrawable.draw(canvas)
            }
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}

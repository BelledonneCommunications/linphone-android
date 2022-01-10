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
package org.linphone.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.linphone.core.tools.Log

/**
 * Helper class to properly display swipe actions in list items.
 */
class RecyclerViewSwipeUtils(
    direction: Int,
    configuration: RecyclerViewSwipeConfiguration,
    listener: RecyclerViewSwipeListener
) : ItemTouchHelper(RecyclerViewSwipeUtilsCallback(direction, configuration, listener))

class RecyclerViewSwipeConfiguration {
    class Action(
        val text: String = "",
        val textColor: Int = Color.WHITE,
        val backgroundColor: Int = 0,
        val icon: Int = 0,
        val iconTint: Int = 0,
        val preventFor: Class<*>? = null
    )

    val iconMargin = 16f

    val actionTextSizeUnit = TypedValue.COMPLEX_UNIT_SP
    // At least CROSSCALL Action-X3 device doesn't have SANS_SERIF typeface...
    val actionTextFont: Typeface? = Typeface.SANS_SERIF
    val actionTextSize = 14f

    var leftToRightAction = Action()
    var rightToLeftAction = Action()
}

private class RecyclerViewSwipeUtilsCallback(
    val direction: Int,
    val configuration: RecyclerViewSwipeConfiguration,
    val listener: RecyclerViewSwipeListener
) : ItemTouchHelper.SimpleCallback(0, direction) {

    fun leftToRightSwipe(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        if (configuration.leftToRightAction.backgroundColor != 0) {
            val background = ColorDrawable(configuration.leftToRightAction.backgroundColor)
            background.setBounds(
                viewHolder.itemView.left,
                viewHolder.itemView.top,
                viewHolder.itemView.left + dX.toInt(),
                viewHolder.itemView.bottom
            )
            background.draw(canvas)
        }

        val horizontalMargin: Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            configuration.iconMargin,
            recyclerView.context.resources.displayMetrics
        ).toInt()
        var iconWidth = 0

        if (configuration.leftToRightAction.icon != 0) {
            val icon =
                ContextCompat.getDrawable(
                    recyclerView.context,
                    configuration.leftToRightAction.icon
                )
            iconWidth = icon?.intrinsicWidth ?: 0
            if (icon != null && dX > iconWidth) {
                val halfIcon = icon.intrinsicHeight / 2
                val top =
                    viewHolder.itemView.top + ((viewHolder.itemView.bottom - viewHolder.itemView.top) / 2 - halfIcon)

                // Icon won't move past the swipe threshold, thus indicating to the user
                // it has reached the required distance for swipe action to be done
                val threshold = getSwipeThreshold(viewHolder) * viewHolder.itemView.right
                val left = if (dX < threshold) {
                    viewHolder.itemView.left + dX.toInt() - iconWidth
                } else {
                    viewHolder.itemView.left + threshold.toInt() - iconWidth
                }

                icon.setBounds(
                    left,
                    top,
                    left + iconWidth,
                    top + icon.intrinsicHeight
                )

                @Suppress("DEPRECATION")
                if (configuration.leftToRightAction.iconTint != 0) icon.setColorFilter(
                    configuration.leftToRightAction.iconTint,
                    PorterDuff.Mode.SRC_IN
                )
                icon.draw(canvas)
            }
        }

        if (configuration.leftToRightAction.text.isNotEmpty() && dX > horizontalMargin + iconWidth) {
            val textPaint = TextPaint()
            textPaint.isAntiAlias = true
            textPaint.textSize = TypedValue.applyDimension(
                configuration.actionTextSizeUnit,
                configuration.actionTextSize,
                recyclerView.context.resources.displayMetrics
            )
            textPaint.color = configuration.leftToRightAction.textColor
            textPaint.typeface = configuration.actionTextFont

            val margin = if (iconWidth > 0) horizontalMargin / 2 else 0
            val textX =
                (viewHolder.itemView.left + horizontalMargin + iconWidth + margin).toFloat()
            val textY =
                (viewHolder.itemView.top + (viewHolder.itemView.bottom - viewHolder.itemView.top) / 2.0 + textPaint.textSize / 2).toFloat()
            canvas.drawText(
                configuration.leftToRightAction.text,
                textX,
                textY,
                textPaint
            )
        }
    }

    fun rightToLeftSwipe(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        if (configuration.rightToLeftAction.backgroundColor != 0) {
            val background = ColorDrawable(configuration.rightToLeftAction.backgroundColor)
            background.setBounds(
                viewHolder.itemView.right + dX.toInt(),
                viewHolder.itemView.top,
                viewHolder.itemView.right,
                viewHolder.itemView.bottom
            )
            background.draw(canvas)
        }

        val horizontalMargin: Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            configuration.iconMargin,
            recyclerView.context.resources.displayMetrics
        ).toInt()
        var iconWidth = 0
        var imageLeftBorder = viewHolder.itemView.right

        if (configuration.rightToLeftAction.icon != 0) {
            val icon =
                ContextCompat.getDrawable(
                    recyclerView.context,
                    configuration.rightToLeftAction.icon
                )
            iconWidth = icon?.intrinsicWidth ?: 0
            if (icon != null && dX < viewHolder.itemView.right - iconWidth) {
                val halfIcon = icon.intrinsicHeight / 2
                val top =
                    viewHolder.itemView.top + ((viewHolder.itemView.bottom - viewHolder.itemView.top) / 2 - halfIcon)

                // Icon won't move past the swipe threshold, thus indicating to the user
                // it has reached the required distance for swipe action to be done
                val threshold = -(getSwipeThreshold(viewHolder) * viewHolder.itemView.right)
                val right = if (dX > threshold) {
                    viewHolder.itemView.right + dX.toInt()
                } else {
                    viewHolder.itemView.right + threshold.toInt()
                }
                imageLeftBorder = right - icon.intrinsicWidth

                icon.setBounds(
                    imageLeftBorder,
                    top,
                    right,
                    top + icon.intrinsicHeight
                )

                @Suppress("DEPRECATION")
                if (configuration.rightToLeftAction.iconTint != 0) icon.setColorFilter(
                    configuration.rightToLeftAction.iconTint,
                    PorterDuff.Mode.SRC_IN
                )
                icon.draw(canvas)
            }
        }

        if (configuration.rightToLeftAction.text.isNotEmpty() && dX < -horizontalMargin - iconWidth) {
            val textPaint = TextPaint()
            textPaint.isAntiAlias = true
            textPaint.textSize = TypedValue.applyDimension(
                configuration.actionTextSizeUnit,
                configuration.actionTextSize,
                recyclerView.context.resources.displayMetrics
            )
            textPaint.color = configuration.rightToLeftAction.textColor
            textPaint.typeface = configuration.actionTextFont

            val margin =
                if (imageLeftBorder == viewHolder.itemView.right) horizontalMargin else horizontalMargin / 2
            val textX =
                imageLeftBorder - textPaint.measureText(configuration.rightToLeftAction.text) - margin
            val textY =
                (viewHolder.itemView.top + (viewHolder.itemView.bottom - viewHolder.itemView.top) / 2.0 + textPaint.textSize / 2).toFloat()
            canvas.drawText(
                configuration.rightToLeftAction.text,
                textX,
                textY,
                textPaint
            )
        }
    }

    fun applyConfiguration(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        actionState: Int
    ) {
        try {
            if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) return

            if (dX > 0) {
                leftToRightSwipe(canvas, recyclerView, viewHolder, dX)
            } else if (dX < 0) {
                rightToLeftSwipe(canvas, recyclerView, viewHolder, dX)
            }
        } catch (e: Exception) {
            Log.e("[RecyclerView Swipe Utils] $e")
        }
    }

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Prevent swipe actions for a specific ViewHolder class if needed
        // Used to allow swipe actions on chat messages but not events
        var dirFlags = direction
        if (direction and ItemTouchHelper.RIGHT != 0) {
            val classToPrevent = configuration.leftToRightAction.preventFor
            if (classToPrevent != null) {
                if (classToPrevent.isInstance(viewHolder)) {
                    dirFlags = dirFlags and ItemTouchHelper.RIGHT.inv()
                }
            }
        }
        if (direction or ItemTouchHelper.LEFT != 0) {
            val classToPrevent = configuration.rightToLeftAction.preventFor
            if (classToPrevent != null) {
                if (classToPrevent.isInstance(viewHolder)) {
                    dirFlags = dirFlags and ItemTouchHelper.LEFT.inv()
                }
            }
        }
        return dirFlags
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
        if (direction == ItemTouchHelper.LEFT) {
            listener.onRightToLeftSwipe(viewHolder)
        } else if (direction == ItemTouchHelper.RIGHT) {
            listener.onLeftToRightSwipe(viewHolder)
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return .33f // A third of the screen is required to validate swipe move (default is .5f)
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        applyConfiguration(canvas, recyclerView, viewHolder, dX, actionState)
        super.onChildDraw(
            canvas,
            recyclerView,
            viewHolder,
            dX,
            dY,
            actionState,
            isCurrentlyActive
        )
    }
}

interface RecyclerViewSwipeListener {
    fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder)
    fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder)
}

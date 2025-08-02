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
package com.naminfo.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.withSave

class RecyclerViewHeaderDecoration(
    private val context: Context,
    private val adapter: HeaderAdapter,
    private val sticky: Boolean = true
) : RecyclerView.ItemDecoration() {
    private val headers: SparseArray<View> = SparseArray()

    fun getDecorationHeight(position: Int): Int {
        return headers.get(position, null)?.height ?: 0
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = (view.layoutParams as RecyclerView.LayoutParams).bindingAdapterPosition

        if (position != RecyclerView.NO_POSITION && adapter.displayHeaderForPosition(position)) {
            val headerView: View = adapter.getHeaderViewForPosition(view.context, position)
            headers.put(position, headerView)
            measureHeaderView(headerView, parent)
            outRect.top = headerView.height
        } else {
            headers.remove(position)
        }
    }

    private fun measureHeaderView(view: View, parent: ViewGroup) {
        if (view.layoutParams == null) {
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.EXACTLY)
        val childWidth = ViewGroup.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight,
            view.layoutParams.width
        )
        val childHeight = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            view.layoutParams.height
        )

        view.measure(childWidth, childHeight)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (sticky) return

        // Used to display the moving item decoration
        for (i in 0 until parent.childCount) { // Only returns visible children
            val child = parent.getChildAt(i)
            // Maps the visible view position to the item index in the adapter
            val position = parent.getChildAdapterPosition(child)
            if (position != RecyclerView.NO_POSITION && adapter.displayHeaderForPosition(position)) {
                canvas.withSave {
                    val headerView: View =
                        headers.get(position) ?: adapter.getHeaderViewForPosition(
                            context,
                            position
                        )
                    if (position != 0 || child.y < headerView.height) {
                        translate(0f, child.y - headerView.height)
                    }
                    headerView.draw(this)
                }
            }
        }
    }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!sticky) return

        var latestPositionHeaderFound = -1
        var nextHeaderTopPosition = -1f

        for (index in parent.childCount downTo 1) { // Only returns visible children, ignores top/first item
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position != RecyclerView.NO_POSITION && adapter.displayHeaderForPosition(position)) {
                canvas.withSave {
                    val headerView: View =
                        headers.get(position) ?: adapter.getHeaderViewForPosition(
                            context,
                            position
                        )

                    val top = child.y - headerView.height
                    if (top >= 0) { // don't move the first header
                        translate(0f, top)
                    }

                    headerView.draw(this)
                }

                latestPositionHeaderFound = position
                nextHeaderTopPosition = child.y
            }
        }

        // Makes sure at least one header is displayed
        if (latestPositionHeaderFound > 0 || latestPositionHeaderFound == -1) {
            // Display first item header at top
            val topVisibleChild = parent.getChildAt(0)
            val topVisibleChildPosition = parent.getChildAdapterPosition(topVisibleChild)
            for (position in topVisibleChildPosition downTo 0) {
                if (adapter.displayHeaderForPosition(position)) {
                    canvas.withSave {
                        val headerView: View =
                            headers.get(position) ?: adapter.getHeaderViewForPosition(
                                context,
                                position
                            )

                        // Do not translate it as we want it sticky to the top unless in contact with next header
                        if (nextHeaderTopPosition > 0 && nextHeaderTopPosition <= (headerView.height * 2)) {
                            val top = nextHeaderTopPosition - (headerView.height * 2)
                            translate(0f, top)
                        }

                        headerView.draw(this)
                    }
                    break
                }
            }
        }
    }
}

interface HeaderAdapter {
    fun displayHeaderForPosition(position: Int): Boolean

    fun getHeaderViewForPosition(context: Context, position: Int): View
}

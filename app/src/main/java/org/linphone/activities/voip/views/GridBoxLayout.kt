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
package org.linphone.activities.voip.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.GridLayout
import androidx.core.view.children
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log

class GridBoxLayout : GridLayout {
    companion object {
        private val placementMatrix = arrayOf(
            intArrayOf(1, 2, 3, 4, 5, 6), intArrayOf(1, 1, 2, 2, 3, 3),
            intArrayOf(1, 1, 1, 2, 2, 2), intArrayOf(1, 1, 1, 1, 2, 2),
            intArrayOf(1, 1, 1, 1, 1, 2), intArrayOf(1, 1, 1, 1, 1, 1)
        )
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    var centerContent: Boolean = false
    private var previousChildCount = 0
    private var previousCellSize = 0

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (childCount == 0 || (!changed && previousChildCount == childCount)) {
            super.onLayout(changed, left, top, right, bottom)
            // To prevent display issue the first time conference is locally paused
            children.forEach { child ->
                child.post {
                    child.layoutParams.width = previousCellSize
                    child.layoutParams.height = previousCellSize
                    child.requestLayout()
                }
            }
            return
        }

        // To prevent java.lang.IllegalArgumentException: columnCount must be greater than or equal
        // to the maximum of all grid indices (and spans) defined in the LayoutParams of each child.
        children.forEach { child ->
            child.layoutParams = LayoutParams()
        }

        val maxChild = placementMatrix[0].size
        if (childCount > maxChild) {
            val maxMosaicParticipants = corePreferences.maxConferenceParticipantsForMosaicLayout
            Log.e("[GridBoxLayout] $childCount children but placementMatrix only knows how to display $maxChild (max allowed participants for grid layout in settings is $maxMosaicParticipants)")
            return
        }

        val availableSize = Pair(right - left, bottom - top)
        var cellSize = 0
        for (index in 1..childCount) {
            val neededColumns = placementMatrix[index - 1][childCount - 1]
            val candidateWidth = 1 * availableSize.first / neededColumns
            val candidateHeight = 1 * availableSize.second / index
            val candidateSize = if (candidateWidth < candidateHeight) candidateWidth else candidateHeight
            if (candidateSize > cellSize) {
                columnCount = neededColumns
                rowCount = index
                cellSize = candidateSize
            }
        }
        previousCellSize = cellSize
        previousChildCount = childCount

        super.onLayout(changed, left, top, right, bottom)
        children.forEach { child ->
            child.layoutParams.width = cellSize
            child.layoutParams.height = cellSize
            child.post {
                child.requestLayout()
            }
        }

        if (centerContent) {
            setPadding((availableSize.first - (columnCount * cellSize)) / 2, (availableSize.second - (rowCount * cellSize)) / 2, (availableSize.first - (columnCount * cellSize)) / 2, (availableSize.second - (rowCount * cellSize)) / 2)
        }
        Log.d("[GridBoxLayout] cellsize=$cellSize columns=$columnCount rows=$rowCount availablesize=$availableSize")
    }
}

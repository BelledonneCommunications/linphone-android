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
package org.linphone.ui.main.chat

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.core.tools.Log

internal abstract class RecyclerViewScrollListener(private val layoutManager: LinearLayoutManager, private val visibleThreshold: Int, private val scrollingTopToBottom: Boolean) :
    RecyclerView.OnScrollListener() {
    companion object {
        private const val TAG = "[RecyclerView Scroll Listener]"
    }

    // The total number of items in the data set after the last load
    private var previousTotalItemCount = 0

    // True if we are still waiting for the last set of data to load.
    private var loading = true

    // This happens many times a second during a scroll, so be wary of the code you place here.
    // We are given a few useful parameters to help us work out if we need to load some more data,
    // but first we check if we are waiting for the previous load to finish.
    override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
        val totalItemCount = layoutManager.itemCount
        val firstVisibleItemPosition: Int = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition: Int = layoutManager.findLastVisibleItemPosition()

        // If the total item count is zero and the previous isn't, assume the
        // list is invalidated and should be reset back to initial state
        if (totalItemCount < previousTotalItemCount) {
            previousTotalItemCount = totalItemCount
            if (totalItemCount == 0) {
                loading = true
            }
        }

        // If it’s still loading, we check to see if the data set count has
        // changed, if so we conclude it has finished loading and update the current page
        // number and total item count.
        if (loading && totalItemCount > previousTotalItemCount) {
            loading = false
            previousTotalItemCount = totalItemCount
        }

        val userHasScrolledUp = lastVisibleItemPosition != totalItemCount - 1
        if (userHasScrolledUp) {
            onScrolledUp()
            Log.d("$TAG Scrolled up")
        } else {
            onScrolledToEnd()
            Log.d("$TAG Scrolled to end")
        }

        // If it isn’t currently loading, we check to see if we have breached
        // the visibleThreshold and need to reload more data.
        // If we do need to reload some more data, we execute onLoadMore to fetch the data.
        // threshold should reflect how many total columns there are too
        if (!loading) {
            if (scrollingTopToBottom) {
                if (lastVisibleItemPosition >= totalItemCount - visibleThreshold) {
                    Log.d(
                        "$TAG Last visible item position [$lastVisibleItemPosition] reached [${totalItemCount - visibleThreshold}], loading more (current total items is [$totalItemCount])"
                    )
                    loading = true
                    onLoadMore(totalItemCount)
                }
            } else {
                if (firstVisibleItemPosition < visibleThreshold) {
                    Log.d(
                        "$TAG First visible item position [$firstVisibleItemPosition] < visibleThreshold [$visibleThreshold], loading more (current total items is [$totalItemCount])"
                    )
                    loading = true
                    onLoadMore(totalItemCount)
                }
            }
        }
    }

    // Defines the process for actually loading more data based on page
    protected abstract fun onLoadMore(totalItemsCount: Int)

    // Called when user has started to scroll up, opposed to onScrolledToEnd()
    protected abstract fun onScrolledUp()

    // Called when user has scrolled and reached the end of the items
    protected abstract fun onScrolledToEnd()
}

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
package org.linphone.activities.main.chat

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal abstract class ChatScrollListener(private val mLayoutManager: LinearLayoutManager) :
    RecyclerView.OnScrollListener() {
    // The total number of items in the data set after the last load
    private var previousTotalItemCount = 0
    // True if we are still waiting for the last set of data to load.
    private var loading = true

    private var userHasScrolledUp: Boolean = false

    // This happens many times a second during a scroll, so be wary of the code you place here.
    // We are given a few useful parameters to help us work out if we need to load some more data,
    // but first we check if we are waiting for the previous load to finish.
    override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
        val totalItemCount = mLayoutManager.itemCount
        val firstVisibleItemPosition: Int = mLayoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition: Int = mLayoutManager.findLastVisibleItemPosition()

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

        userHasScrolledUp = lastVisibleItemPosition != totalItemCount - 1
        if (userHasScrolledUp) {
            onScrolledUp()
        } else {
            onScrolledToEnd()
        }

        // If it isn’t currently loading, we check to see if we have breached
        // the mVisibleThreshold and need to reload more data.
        // If we do need to reload some more data, we execute onLoadMore to fetch the data.
        // threshold should reflect how many total columns there are too
        if (!loading &&
            firstVisibleItemPosition < mVisibleThreshold &&
            firstVisibleItemPosition >= 0 &&
            lastVisibleItemPosition < totalItemCount - mVisibleThreshold
        ) {
            onLoadMore(totalItemCount)
            loading = true
        }
    }

    // Defines the process for actually loading more data based on page
    protected abstract fun onLoadMore(totalItemsCount: Int)

    // Called when user has started to scroll up, opposed to onScrolledToEnd()
    protected abstract fun onScrolledUp()

    // Called when user has scrolled and reached the end of the items
    protected abstract fun onScrolledToEnd()

    companion object {
        // The minimum amount of items to have below your current scroll position
        // before loading more.
        private const val mVisibleThreshold = 5
    }
}

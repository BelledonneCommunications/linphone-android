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
package org.linphone.ui.fileviewer.adapter

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.linphone.core.tools.Log
import org.linphone.ui.fileviewer.fragment.MediaViewerFragment
import org.linphone.ui.fileviewer.viewmodel.MediaListViewModel

class MediaListAdapter(
    fragmentActivity: FragmentActivity,
    private val viewModel: MediaListViewModel
) :
    FragmentStateAdapter(fragmentActivity) {
    companion object {
        private const val TAG = "[Media List Adapter]"
    }

    override fun getItemCount(): Int {
        return viewModel.mediaList.value.orEmpty().size
    }

    override fun getItemId(position: Int): Long {
        return viewModel.mediaList.value.orEmpty().getOrNull(position)?.originalPath.hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return viewModel.mediaList.value.orEmpty().any { it.originalPath.hashCode().toLong() == itemId }
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = MediaViewerFragment()
        fragment.arguments = Bundle().apply {
            val path = viewModel.mediaList.value.orEmpty().getOrNull(position)?.path
            Log.d("$TAG Path is [$path] for position [$position]")
            putString("path", path)
        }
        return fragment
    }
}

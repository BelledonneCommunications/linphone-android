/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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
package org.linphone.ui

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

abstract class GenericListAdapter<T, VH : RecyclerView.ViewHolder>(callback: DiffUtil.ItemCallback<T>) : ListAdapter<T, VH>(callback) {
    protected var selectedAdapterPosition = -1

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    fun isSelected(position: Int): Boolean {
        return position == selectedAdapterPosition
    }

    fun selectBindingRoot(binding: ViewDataBinding) {
        binding.root.isSelected = true
    }

    fun setBindingRootSelectedIfNeeded(binding: ViewDataBinding, position: Int) {
        binding.root.isSelected = isSelected(position)
    }
}

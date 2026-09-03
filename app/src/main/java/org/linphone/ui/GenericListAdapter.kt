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
    private var activatedAdapterPosition = -1
    private var activatedViewHolder: ViewDataBinding? = null

    private var selectedAdapterPosition = -1
    private var selectedViewHolder: ViewDataBinding? = null

    override fun submitList(list: List<T?>?) {
        activatedViewHolder = null
        selectedViewHolder = null

        super.submitList(list)
    }

    fun resetActivated() {
        activatedAdapterPosition = -1
        activatedViewHolder?.root?.isActivated = false
        activatedViewHolder = null
    }

    fun resetSelection() {
        if (selectedViewHolder != null) {
            selectedViewHolder?.root?.isSelected = false
        } else {
            notifyItemChanged(selectedAdapterPosition)
        }
        selectedAdapterPosition = -1
        selectedViewHolder = null
    }

    fun activateBindingRoot(binding: ViewDataBinding, position: Int) {
        activatedViewHolder = binding
        activatedAdapterPosition = position
        binding.root.isActivated = true
    }

    fun selectBindingRoot(binding: ViewDataBinding, position: Int) {
        resetSelection()

        selectedViewHolder = binding
        selectedAdapterPosition = position
        binding.root.isSelected = true
    }

    fun setBindingRootSelectedAndActivatedIfNeeded(binding: ViewDataBinding, position: Int) {
        binding.root.isActivated = isActivated(position)
        binding.root.isSelected = isSelected(position)
    }

    fun notifyItemHasBeenSelected(position: Int) {
        val previouslySelectedPosition = selectedAdapterPosition
        if (previouslySelectedPosition != position) {
            if (previouslySelectedPosition > -1) {
                selectedAdapterPosition = -1
                if (selectedViewHolder != null) {
                    selectedViewHolder?.root?.isSelected = false
                } else {
                    notifyItemChanged(previouslySelectedPosition)
                }
            }

            selectedAdapterPosition = position
            notifyItemChanged(selectedAdapterPosition)
        }
    }

    private fun isActivated(position: Int): Boolean {
        return position == activatedAdapterPosition
    }

    private fun isSelected(position: Int): Boolean {
        return position == selectedAdapterPosition
    }
}

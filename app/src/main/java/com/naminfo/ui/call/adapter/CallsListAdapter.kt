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
package com.naminfo.ui.call.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naminfo.R
import com.naminfo.databinding.CallListCellBinding
import com.naminfo.ui.call.model.CallModel
import com.naminfo.utils.Event

class CallsListAdapter :
    ListAdapter<CallModel, RecyclerView.ViewHolder>(CallDiffCallback()) {
    var selectedAdapterPosition = -1

    val callClickedEvent: MutableLiveData<Event<CallModel>> by lazy {
        MutableLiveData<Event<CallModel>>()
    }

    val callLongClickedEvent: MutableLiveData<Event<CallModel>> by lazy {
        MutableLiveData<Event<CallModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: CallListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.call_list_cell,
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                callClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                callLongClickedEvent.value = Event(model!!)
                true
            }
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: CallListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(callModel: CallModel) {
            with(binding) {
                model = callModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    private class CallDiffCallback : DiffUtil.ItemCallback<CallModel>() {
        override fun areItemsTheSame(oldItem: CallModel, newItem: CallModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallModel, newItem: CallModel): Boolean {
            return false
        }
    }
}

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
package com.naminfo.ui.main.history.adapter

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
import com.naminfo.databinding.HistoryListCellBinding
import com.naminfo.ui.main.history.model.CallLogModel
import com.naminfo.utils.Event

class HistoryListAdapter : ListAdapter<CallLogModel, RecyclerView.ViewHolder>(CallLogDiffCallback()) {
    var selectedAdapterPosition = -1

    val callLogClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData<Event<CallLogModel>>()
    }

    val callLogLongClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData<Event<CallLogModel>>()
    }

    val callLogCallBackClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData<Event<CallLogModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: HistoryListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.history_list_cell,
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                callLogClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                callLogLongClickedEvent.value = Event(model!!)
                true
            }

            setOnCallClickListener {
                callLogCallBackClickedEvent.value = Event(model!!)
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
        val binding: HistoryListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(callLogModel: CallLogModel) {
            with(binding) {
                model = callLogModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    private class CallLogDiffCallback : DiffUtil.ItemCallback<CallLogModel>() {
        override fun areItemsTheSame(oldItem: CallLogModel, newItem: CallLogModel): Boolean {
            return oldItem.id == newItem.id && oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: CallLogModel, newItem: CallLogModel): Boolean {
            return false // ContactAvatarModel will be the same object but with an updated content
        }
    }
}

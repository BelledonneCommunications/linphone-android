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
package com.naminfo.ui.main.recordings.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naminfo.R
import com.naminfo.databinding.RecordingListCellBinding
import com.naminfo.databinding.RecordingsListDecorationBinding
import com.naminfo.ui.main.recordings.model.RecordingModel
import com.naminfo.utils.Event
import com.naminfo.utils.HeaderAdapter

class RecordingsListAdapter :
    ListAdapter<RecordingModel, RecyclerView.ViewHolder>(
        RecordingDiffCallback()
    ),
    HeaderAdapter {
    var selectedAdapterPosition = -1

    val recordingClickedEvent: MutableLiveData<Event<RecordingModel>> by lazy {
        MutableLiveData<Event<RecordingModel>>()
    }

    val recordingLongClickedEvent: MutableLiveData<Event<RecordingModel>> by lazy {
        MutableLiveData<Event<RecordingModel>>()
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position == 0) return true

        val previous = getItem(position - 1)
        val item = getItem(position)
        return previous.month != item.month
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = RecordingsListDecorationBinding.inflate(LayoutInflater.from(context))
        val item = getItem(position)
        binding.header.text = item.month
        return binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: RecordingListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.recording_list_cell,
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                recordingClickedEvent.value = Event(model!!)
                resetSelection()
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                recordingLongClickedEvent.value = Event(model!!)
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
        val binding: RecordingListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(recordingModel: RecordingModel) {
            with(binding) {
                model = recordingModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    private class RecordingDiffCallback : DiffUtil.ItemCallback<RecordingModel>() {
        override fun areItemsTheSame(oldItem: RecordingModel, newItem: RecordingModel): Boolean {
            return false
        }

        override fun areContentsTheSame(oldItem: RecordingModel, newItem: RecordingModel): Boolean {
            return false
        }
    }
}

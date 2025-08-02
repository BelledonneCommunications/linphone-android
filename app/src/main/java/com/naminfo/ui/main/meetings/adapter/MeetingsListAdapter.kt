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
package com.naminfo.ui.main.meetings.adapter

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
import com.naminfo.databinding.MeetingListCellBinding
import com.naminfo.databinding.MeetingListTodayIndicatorBinding
import com.naminfo.databinding.MeetingsListDecorationBinding
import com.naminfo.ui.main.meetings.model.MeetingListItemModel
import com.naminfo.ui.main.meetings.model.MeetingModel
import com.naminfo.utils.Event
import com.naminfo.utils.HeaderAdapter

class MeetingsListAdapter :
    ListAdapter<MeetingListItemModel, RecyclerView.ViewHolder>(
        MeetingDiffCallback()
    ),
    HeaderAdapter {
    companion object {
        const val MEETING = 1
        const val TODAY_INDICATOR = 2
    }

    var selectedAdapterPosition = -1

    val meetingClickedEvent: MutableLiveData<Event<MeetingModel>> by lazy {
        MutableLiveData<Event<MeetingModel>>()
    }

    val meetingLongClickedEvent: MutableLiveData<Event<MeetingModel>> by lazy {
        MutableLiveData<Event<MeetingModel>>()
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position == 0) return true

        val previous = getItem(position - 1)
        val item = getItem(position)
        return previous.month != item.month
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = MeetingsListDecorationBinding.inflate(LayoutInflater.from(context))
        val item = getItem(position)
        binding.header.text = item.month
        return binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TODAY_INDICATOR -> createTodayIndicatorViewHolder(parent)
            else -> createMeetingViewHolder(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)
        if (data.isTodayIndicator) {
            return TODAY_INDICATOR
        }
        return MEETING
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MeetingViewHolder) {
            holder.bind(getItem(position).model as MeetingModel)
        } else if (holder is TodayIndicatorViewHolder) {
            holder.bind(getItem(position))
        }
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    private fun createMeetingViewHolder(parent: ViewGroup): MeetingViewHolder {
        val binding: MeetingListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.meeting_list_cell,
            parent,
            false
        )
        val viewHolder = MeetingViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                meetingClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                meetingLongClickedEvent.value = Event(model!!)
                true
            }
        }
        return viewHolder
    }

    private fun createTodayIndicatorViewHolder(parent: ViewGroup): TodayIndicatorViewHolder {
        val binding: MeetingListTodayIndicatorBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.meeting_list_today_indicator,
            parent,
            false
        )
        val viewHolder = TodayIndicatorViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
        }
        return viewHolder
    }

    inner class MeetingViewHolder(
        val binding: MeetingListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(meetingModel: MeetingModel) {
            with(binding) {
                model = meetingModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    inner class TodayIndicatorViewHolder(
        val binding: MeetingListTodayIndicatorBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(meetingListModel: MeetingListItemModel) {
            with(binding) {
                model = meetingListModel
                executePendingBindings()
            }
        }
    }

    private class MeetingDiffCallback : DiffUtil.ItemCallback<MeetingListItemModel>() {
        override fun areItemsTheSame(oldItem: MeetingListItemModel, newItem: MeetingListItemModel): Boolean {
            if (oldItem.isTodayIndicator && newItem.isTodayIndicator) return true
            if (oldItem.model is MeetingModel && newItem.model is MeetingModel) {
                return oldItem.model.id.isNotEmpty() && oldItem.model.id == newItem.model.id
            }
            return false
        }

        override fun areContentsTheSame(
            oldItem: MeetingListItemModel,
            newItem: MeetingListItemModel
        ): Boolean {
            if (oldItem.isTodayIndicator && newItem.isTodayIndicator) return true
            if (oldItem.model is MeetingModel && newItem.model is MeetingModel) {
                return oldItem.model.subject.value.orEmpty().isNotEmpty() &&
                    oldItem.model.subject.value == newItem.model.subject.value &&
                    oldItem.model.time == newItem.model.time &&
                    oldItem.model.isCancelled == newItem.model.isCancelled &&
                    oldItem.model.isToday == newItem.model.isToday &&
                    oldItem.model.isAfterToday == newItem.model.isAfterToday &&
                    oldItem.firstMeetingOfTheWeek == newItem.firstMeetingOfTheWeek &&
                    oldItem.model.firstMeetingOfTheDay.value == newItem.model.firstMeetingOfTheDay.value
            }
            return false
        }
    }
}

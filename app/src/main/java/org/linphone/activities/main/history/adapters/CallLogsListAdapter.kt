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
package org.linphone.activities.main.history.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import java.text.SimpleDateFormat
import java.util.*
import org.linphone.R
import org.linphone.activities.main.history.viewmodels.CallLogViewModel
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.Address
import org.linphone.core.CallLog
import org.linphone.databinding.GenericListHeaderBinding
import org.linphone.databinding.HistoryListCellBinding
import org.linphone.utils.*

class CallLogsListAdapter(val selectionViewModel: ListTopBarViewModel) : LifecycleListAdapter<CallLog, CallLogsListAdapter.ViewHolder>(CallLogDiffCallback()), HeaderAdapter {
    val selectedCallLogEvent: MutableLiveData<Event<CallLog>> by lazy {
        MutableLiveData<Event<CallLog>>()
    }

    val startCallToEvent: MutableLiveData<Event<Address>> by lazy {
        MutableLiveData<Event<Address>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: HistoryListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.history_list_cell, parent, false
        )
        val viewHolder = ViewHolder(binding)
        binding.lifecycleOwner = viewHolder
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: HistoryListCellBinding
    ) : LifecycleViewHolder(binding) {
        fun bind(callLog: CallLog) {
            with(binding) {
                val callLogViewModel = CallLogViewModel(callLog)
                viewModel = callLogViewModel

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(this@ViewHolder, Observer {
                    position = adapterPosition
                })

                setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(adapterPosition)
                    } else {
                        startCallToEvent.value = Event(callLog.remoteAddress)
                    }
                }

                // This listener is disabled when in edition mode
                setDetailsClickListener {
                    selectedCallLogEvent.value = Event(callLog)
                }

                executePendingBindings()
            }
        }
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false
        val callLog = getItem(position)
        val date = callLog.startDate
        val previousPosition = position - 1
        return if (previousPosition >= 0) {
            val previousItemDate = getItem(previousPosition).startDate
            !TimestampUtils.isSameDay(date, previousItemDate)
        } else true
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val callLog = getItem(position)
        val date = formatDate(context, callLog.startDate)
        val binding: GenericListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.generic_list_header, null, false
        )
        binding.title = date
        binding.executePendingBindings()
        return binding.root
    }

    private fun formatDate(context: Context, date: Long): String {
        if (TimestampUtils.isToday(date)) {
            return context.getString(R.string.today)
        } else if (TimestampUtils.isYesterday(date)) {
            return context.getString(R.string.yesterday)
        }
        return SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(date * 1000))
    }
}

private class CallLogDiffCallback : DiffUtil.ItemCallback<CallLog>() {
    override fun areItemsTheSame(
        oldItem: CallLog,
        newItem: CallLog
    ): Boolean {
        return oldItem.callId == newItem.callId
    }

    override fun areContentsTheSame(
        oldItem: CallLog,
        newItem: CallLog
    ): Boolean {
        return false // For headers
    }
}

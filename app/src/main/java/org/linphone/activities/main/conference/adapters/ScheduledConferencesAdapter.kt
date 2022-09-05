/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.conference.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.adapters.SelectionListAdapter
import org.linphone.activities.main.conference.data.ScheduledConferenceData
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.databinding.ConferenceScheduleCellBinding
import org.linphone.databinding.ConferenceScheduleListHeaderBinding
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter
import org.linphone.utils.TimestampUtils

class ScheduledConferencesAdapter(
    selectionVM: ListTopBarViewModel,
    private val viewLifecycleOwner: LifecycleOwner
) : SelectionListAdapter<ScheduledConferenceData, RecyclerView.ViewHolder>(selectionVM, ConferenceInfoDiffCallback()),
    HeaderAdapter {
    val copyAddressToClipboardEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val joinConferenceEvent: MutableLiveData<Event<Pair<String, String?>>> by lazy {
        MutableLiveData<Event<Pair<String, String?>>>()
    }

    val editConferenceEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val deleteConferenceInfoEvent: MutableLiveData<Event<ScheduledConferenceData>> by lazy {
        MutableLiveData<Event<ScheduledConferenceData>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduledConferencesAdapter.ViewHolder {
        val binding: ConferenceScheduleCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.conference_schedule_cell, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ScheduledConferencesAdapter.ViewHolder).bind(getItem(position))
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false
        val conferenceInfo = getItem(position)
        val previousPosition = position - 1
        return if (previousPosition >= 0) {
            val previousItem = getItem(previousPosition)
            !TimestampUtils.isSameDay(previousItem.conferenceInfo.dateTime, conferenceInfo.conferenceInfo.dateTime)
        } else true
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val data = getItem(position)
        val binding: ConferenceScheduleListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.conference_schedule_list_header, null, false
        )
        binding.title = formatDate(context, data.conferenceInfo.dateTime)
        binding.executePendingBindings()
        return binding.root
    }

    private fun formatDate(context: Context, date: Long): String {
        if (TimestampUtils.isToday(date)) {
            return context.getString(R.string.today)
        }
        return TimestampUtils.toString(date, onlyDate = true, shortDate = false, hideYear = false)
    }

    inner class ViewHolder(
        val binding: ConferenceScheduleCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(conferenceData: ScheduledConferenceData) {
            with(binding) {
                data = conferenceData

                lifecycleOwner = viewLifecycleOwner

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(
                    viewLifecycleOwner
                ) {
                    position = bindingAdapterPosition
                }

                setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(bindingAdapterPosition)
                    } else {
                        conferenceData.toggleExpand()
                    }
                }

                setLongClickListener {
                    if (selectionViewModel.isEditionEnabled.value == false) {
                        selectionViewModel.isEditionEnabled.value = true
                        // Selection will be handled by click listener
                        true
                    }
                    false
                }

                setCopyAddressClickListener {
                    val address = conferenceData.getAddressAsString()
                    if (address.isNotEmpty()) {
                        copyAddressToClipboardEvent.value = Event(address)
                    }
                }

                setJoinConferenceClickListener {
                    val address = conferenceData.conferenceInfo.uri
                    if (address != null) {
                        joinConferenceEvent.value = Event(Pair(address.asStringUriOnly(), conferenceData.conferenceInfo.subject))
                    }
                }

                setEditConferenceClickListener {
                    val address = conferenceData.conferenceInfo.uri
                    if (address != null) {
                        editConferenceEvent.value = Event(address.asStringUriOnly())
                    }
                }

                setDeleteConferenceClickListener {
                    deleteConferenceInfoEvent.value = Event(conferenceData)
                }

                executePendingBindings()
            }
        }
    }
}

private class ConferenceInfoDiffCallback : DiffUtil.ItemCallback<ScheduledConferenceData>() {
    override fun areItemsTheSame(
        oldItem: ScheduledConferenceData,
        newItem: ScheduledConferenceData
    ): Boolean {
        return oldItem.conferenceInfo == newItem.conferenceInfo
    }

    override fun areContentsTheSame(
        oldItem: ScheduledConferenceData,
        newItem: ScheduledConferenceData
    ): Boolean {
        return false
    }
}

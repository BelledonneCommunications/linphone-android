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
package org.linphone.activities.main.recordings.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.adapters.SelectionListAdapter
import org.linphone.activities.main.recordings.data.RecordingData
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.databinding.GenericListHeaderBinding
import org.linphone.databinding.RecordingListCellBinding
import org.linphone.utils.*

class RecordingsListAdapter(
    selectionVM: ListTopBarViewModel,
    private val viewLifecycleOwner: LifecycleOwner
) : SelectionListAdapter<RecordingData, RecyclerView.ViewHolder>(selectionVM, RecordingDiffCallback()), HeaderAdapter {

    private lateinit var videoSurface: TextureView

    fun setVideoTextureView(textureView: TextureView) {
        videoSurface = textureView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: RecordingListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.recording_list_cell, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: RecordingListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recording: RecordingData) {
            with(binding) {
                data = recording

                lifecycleOwner = viewLifecycleOwner

                // This is for item selection through ListTopBarFragment
                position = bindingAdapterPosition
                selectionListViewModel = selectionViewModel

                setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(bindingAdapterPosition)
                    }
                }

                setPlayListener {
                    if (recording.isPlaying.value == true) {
                        recording.pause()
                    } else {
                        recording.play()
                        if (recording.isVideoAvailable()) {
                            recording.setTextureView(videoSurface)
                        }
                    }
                }

                executePendingBindings()
            }
        }
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false

        val recording = getItem(position)
        val date = recording.date
        val previousPosition = position - 1

        return if (previousPosition >= 0) {
            val previousItemDate = getItem(previousPosition).date
            !TimestampUtils.isSameDay(date, previousItemDate)
        } else true
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val recording = getItem(position)
        val date = formatDate(context, recording.date.time)
        val binding: GenericListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.generic_list_header, null, false
        )
        binding.title = date
        binding.executePendingBindings()
        return binding.root
    }

    private fun formatDate(context: Context, date: Long): String {
        // Recordings is one of the few items in Linphone that is already in milliseconds
        if (TimestampUtils.isToday(date, false)) {
            return context.getString(R.string.today)
        } else if (TimestampUtils.isYesterday(date, false)) {
            return context.getString(R.string.yesterday)
        }
        return TimestampUtils.toString(date, onlyDate = true, timestampInSecs = false, shortDate = false, hideYear = false)
    }
}

private class RecordingDiffCallback : DiffUtil.ItemCallback<RecordingData>() {
    override fun areItemsTheSame(
        oldItem: RecordingData,
        newItem: RecordingData
    ): Boolean {
        return oldItem.compareTo(newItem) == 0
    }

    override fun areContentsTheSame(
        oldItem: RecordingData,
        newItem: RecordingData
    ): Boolean {
        return false // for headers
    }
}

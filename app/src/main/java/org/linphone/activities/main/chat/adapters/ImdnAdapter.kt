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
package org.linphone.activities.main.chat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.chat.data.ImdnParticipantData
import org.linphone.core.ChatMessage
import org.linphone.databinding.ChatRoomImdnParticipantCellBinding
import org.linphone.databinding.ImdnListHeaderBinding
import org.linphone.utils.HeaderAdapter

class ImdnAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ImdnParticipantData, RecyclerView.ViewHolder>(ParticipantImdnStateDiffCallback()), HeaderAdapter {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: ChatRoomImdnParticipantCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_room_imdn_participant_cell, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: ChatRoomImdnParticipantCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(participantImdnData: ImdnParticipantData) {
            with(binding) {
                data = participantImdnData

                lifecycleOwner = viewLifecycleOwner

                executePendingBindings()
            }
        }
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false
        val participantImdnState = getItem(position)
        val previousPosition = position - 1
        return if (previousPosition >= 0) {
            getItem(previousPosition).imdnState.state != participantImdnState.imdnState.state
        } else true
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val participantImdnState = getItem(position).imdnState
        val binding: ImdnListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.imdn_list_header, null, false
        )
        when (participantImdnState.state) {
            ChatMessage.State.Displayed -> {
                binding.title = R.string.chat_message_imdn_displayed
                binding.textColor = R.color.imdn_read_color
                binding.icon = R.drawable.chat_read
            }
            ChatMessage.State.DeliveredToUser -> {
                binding.title = R.string.chat_message_imdn_delivered
                binding.textColor = R.color.grey_color
                binding.icon = R.drawable.chat_delivered
            }
            ChatMessage.State.Delivered -> {
                binding.title = R.string.chat_message_imdn_sent
                binding.textColor = R.color.grey_color
                binding.icon = R.drawable.chat_delivered
            }
            ChatMessage.State.NotDelivered -> {
                binding.title = R.string.chat_message_imdn_undelivered
                binding.textColor = R.color.red_color
                binding.icon = R.drawable.chat_error
            }
            else -> {}
        }
        binding.executePendingBindings()
        return binding.root
    }
}

private class ParticipantImdnStateDiffCallback : DiffUtil.ItemCallback<ImdnParticipantData>() {
    override fun areItemsTheSame(
        oldItem: ImdnParticipantData,
        newItem: ImdnParticipantData
    ): Boolean {
        return oldItem.sipUri == newItem.sipUri
    }

    override fun areContentsTheSame(
        oldItem: ImdnParticipantData,
        newItem: ImdnParticipantData
    ): Boolean {
        return false
    }
}

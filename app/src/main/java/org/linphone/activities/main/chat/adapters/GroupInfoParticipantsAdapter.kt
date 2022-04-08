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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.chat.GroupChatRoomMember
import org.linphone.activities.main.chat.data.GroupInfoParticipantData
import org.linphone.databinding.ChatRoomGroupInfoParticipantCellBinding
import org.linphone.utils.Event

class GroupInfoParticipantsAdapter(
    private val viewLifecycleOwner: LifecycleOwner,
    private val isEncryptionEnabled: Boolean
) : ListAdapter<GroupInfoParticipantData, RecyclerView.ViewHolder>(ParticipantDiffCallback()) {
    private var showAdmin: Boolean = false

    val participantRemovedEvent: MutableLiveData<Event<GroupChatRoomMember>> by lazy {
        MutableLiveData<Event<GroupChatRoomMember>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: ChatRoomGroupInfoParticipantCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_room_group_info_participant_cell, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    fun showAdminControls(show: Boolean) {
        showAdmin = show
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(
        val binding: ChatRoomGroupInfoParticipantCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(participantViewModel: GroupInfoParticipantData) {
            with(binding) {
                participantViewModel.showAdminControls.value = showAdmin
                data = participantViewModel

                lifecycleOwner = viewLifecycleOwner

                setRemoveClickListener {
                    participantRemovedEvent.value = Event(participantViewModel.participant)
                }
                isEncrypted = isEncryptionEnabled

                executePendingBindings()
            }
        }
    }
}

private class ParticipantDiffCallback : DiffUtil.ItemCallback<GroupInfoParticipantData>() {
    override fun areItemsTheSame(
        oldItem: GroupInfoParticipantData,
        newItem: GroupInfoParticipantData
    ): Boolean {
        return oldItem.sipUri == newItem.sipUri
    }

    override fun areContentsTheSame(
        oldItem: GroupInfoParticipantData,
        newItem: GroupInfoParticipantData
    ): Boolean {
        return false
    }
}

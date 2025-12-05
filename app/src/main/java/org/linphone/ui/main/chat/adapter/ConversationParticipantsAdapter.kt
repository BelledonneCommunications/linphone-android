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
package org.linphone.ui.main.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ChatParticipantListCellBinding
import org.linphone.ui.main.chat.model.ParticipantModel

class ConversationParticipantsAdapter : ListAdapter<ParticipantModel, RecyclerView.ViewHolder>(
    ChatRoomParticipantDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatParticipantListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_participant_list_cell,
            parent,
            false
        )
        binding.lifecycleOwner = parent.findViewTreeLifecycleOwner()
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    class ViewHolder(
        val binding: ChatParticipantListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(participantModel: ParticipantModel) {
            with(binding) {
                model = participantModel
                executePendingBindings()
            }
        }
    }

    private class ChatRoomParticipantDiffCallback : DiffUtil.ItemCallback<ParticipantModel>() {
        override fun areItemsTheSame(oldItem: ParticipantModel, newItem: ParticipantModel): Boolean {
            return oldItem.sipUri == newItem.sipUri
        }

        override fun areContentsTheSame(oldItem: ParticipantModel, newItem: ParticipantModel): Boolean {
            return oldItem.avatarModel.id == newItem.avatarModel.id && oldItem.isParticipantAdmin == newItem.isParticipantAdmin
        }
    }
}

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
package org.linphone.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ChatRoomListCellBinding
import org.linphone.utils.Event

class ConversationsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ChatRoomData, RecyclerView.ViewHolder>(ConversationDiffCallback()) {
    val chatRoomClickedEvent: MutableLiveData<Event<ChatRoomData>> by lazy {
        MutableLiveData<Event<ChatRoomData>>()
    }

    val chatRoomLongClickedEvent: MutableLiveData<Event<ChatRoomData>> by lazy {
        MutableLiveData<Event<ChatRoomData>>()
    }

    var selectedAdapterPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatRoomListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_room_list_cell,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: ChatRoomListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatRoomData: ChatRoomData) {
            with(binding) {
                chatRoomData.update()
                data = chatRoomData

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                chatRoomData.chatRoomDataListener = object : ChatRoomDataListener() {
                    override fun onClicked() {
                        chatRoomClickedEvent.value = Event(chatRoomData)
                    }

                    override fun onLongClicked() {
                        selectedAdapterPosition = bindingAdapterPosition
                        binding.root.isSelected = true
                        chatRoomLongClickedEvent.value = Event(chatRoomData)
                    }
                }
            }
        }
    }
}

private class ConversationDiffCallback : DiffUtil.ItemCallback<ChatRoomData>() {
    override fun areItemsTheSame(oldItem: ChatRoomData, newItem: ChatRoomData): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatRoomData, newItem: ChatRoomData): Boolean {
        return false
    }
}

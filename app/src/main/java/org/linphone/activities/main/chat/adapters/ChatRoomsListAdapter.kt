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
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.adapters.SelectionListAdapter
import org.linphone.activities.main.chat.data.ChatRoomData
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomListCellBinding
import org.linphone.utils.Event

class ChatRoomsListAdapter(
    selectionVM: ListTopBarViewModel,
    private val viewLifecycleOwner: LifecycleOwner
) : SelectionListAdapter<ChatRoomData, RecyclerView.ViewHolder>(selectionVM, ChatRoomDiffCallback()) {
    val selectedChatRoomEvent: MutableLiveData<Event<ChatRoom>> by lazy {
        MutableLiveData<Event<ChatRoom>>()
    }

    private var isForwardPending = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
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

    fun forwardPending(pending: Boolean) {
        isForwardPending = pending
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(
        private val binding: ChatRoomListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatRoomData: ChatRoomData) {
            with(binding) {
                chatRoomData.update()
                data = chatRoomData

                chatRoomData.contactNewlyFoundEvent.observe(viewLifecycleOwner) {
                    it.consume {
                        // Post to prevent IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
                        binding.root.post {
                            try {
                                notifyItemChanged(bindingAdapterPosition)
                            } catch (e: Exception) {
                                Log.e(
                                    "[Chat Rooms Adapter] Can't notify item [$bindingAdapterPosition] has changed: $e"
                                )
                            }
                        }
                    }
                }

                lifecycleOwner = viewLifecycleOwner

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(
                    viewLifecycleOwner
                ) {
                    position = bindingAdapterPosition
                }

                forwardPending = isForwardPending

                setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(bindingAdapterPosition)
                    } else {
                        selectedChatRoomEvent.value = Event(chatRoomData.chatRoom)
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

                executePendingBindings()
            }
        }
    }
}

private class ChatRoomDiffCallback : DiffUtil.ItemCallback<ChatRoomData>() {
    override fun areItemsTheSame(
        oldItem: ChatRoomData,
        newItem: ChatRoomData
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: ChatRoomData,
        newItem: ChatRoomData
    ): Boolean {
        return false // To force redraw
    }
}

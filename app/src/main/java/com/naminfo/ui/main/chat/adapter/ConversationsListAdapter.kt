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
package com.naminfo.ui.main.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naminfo.R
import com.naminfo.databinding.ChatListCellBinding
import com.naminfo.ui.main.chat.model.ConversationModel
import com.naminfo.utils.Event
import com.naminfo.utils.startAnimatedDrawable

class ConversationsListAdapter : ListAdapter<ConversationModel, RecyclerView.ViewHolder>(
    ChatRoomDiffCallback()
) {
    var selectedAdapterPosition = -1

    val conversationClickedEvent: MutableLiveData<Event<ConversationModel>> by lazy {
        MutableLiveData<Event<ConversationModel>>()
    }

    val conversationLongClickedEvent: MutableLiveData<Event<ConversationModel>> by lazy {
        MutableLiveData<Event<ConversationModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_list_cell,
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                conversationClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                conversationLongClickedEvent.value = Event(model!!)
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
        val binding: ChatListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationModel: ConversationModel) {
            with(binding) {
                model = conversationModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()

                binding.root.doOnPreDraw {
                    binding.lastSentMessageStatus.startAnimatedDrawable()
                }
            }
        }
    }

    private class ChatRoomDiffCallback : DiffUtil.ItemCallback<ConversationModel>() {
        override fun areItemsTheSame(oldItem: ConversationModel, newItem: ConversationModel): Boolean {
            return oldItem.id == newItem.id && oldItem.lastUpdateTime == newItem.lastUpdateTime
        }

        override fun areContentsTheSame(oldItem: ConversationModel, newItem: ConversationModel): Boolean {
            return oldItem.avatarModel.value?.id == newItem.avatarModel.value?.id
        }
    }
}

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
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.lang.Math.abs
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.databinding.ChatBubbleIncomingBinding
import org.linphone.databinding.ChatBubbleOutgoingBinding
import org.linphone.databinding.ChatEventBinding
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.chat.model.EventModel

class ConversationEventAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<EventLogModel, RecyclerView.ViewHolder>(EventLogDiffCallback()) {
    companion object {
        const val INCOMING_CHAT_MESSAGE = 1
        const val OUTGOING_CHAT_MESSAGE = 2
        const val EVENT = 3

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
    }

    var selectedAdapterPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            INCOMING_CHAT_MESSAGE -> createIncomingChatBubble(parent)
            OUTGOING_CHAT_MESSAGE -> createOutgoingChatBubble(parent)
            else -> createEvent(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)
        if (data.data is ChatMessageModel) {
            if (data.data.isOutgoing) {
                return OUTGOING_CHAT_MESSAGE
            }
            return INCOMING_CHAT_MESSAGE
        }
        return EVENT
    }

    private fun createIncomingChatBubble(parent: ViewGroup): IncomingBubbleViewHolder {
        val binding: ChatBubbleIncomingBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_incoming,
            parent,
            false
        )
        return IncomingBubbleViewHolder(binding)
    }

    private fun createOutgoingChatBubble(parent: ViewGroup): OutgoingBubbleViewHolder {
        val binding: ChatBubbleOutgoingBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_outgoing,
            parent,
            false
        )
        return OutgoingBubbleViewHolder(binding)
    }

    private fun createEvent(parent: ViewGroup): EventViewHolder {
        val binding: ChatEventBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_event,
            parent,
            false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val eventLog = getItem(position)
        when (holder) {
            is IncomingBubbleViewHolder -> holder.bind(eventLog.data as ChatMessageModel)
            is OutgoingBubbleViewHolder -> holder.bind(eventLog.data as ChatMessageModel)
            is EventViewHolder -> holder.bind(eventLog.data as EventModel)
        }
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class IncomingBubbleViewHolder(
        val binding: ChatBubbleIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatMessageData: ChatMessageModel) {
            with(binding) {
                model = chatMessageData

                val position = bindingAdapterPosition
                isGroupedWithPreviousOne = if (position == 0) {
                    false
                } else {
                    val previous = position - 1
                    if (getItemViewType(previous) == INCOMING_CHAT_MESSAGE) {
                        val previousItem = getItem(previous).data as ChatMessageModel
                        if (kotlin.math.abs(chatMessageData.timestamp - previousItem.timestamp) < MAX_TIME_TO_GROUP_MESSAGES) {
                            previousItem.fromSipUri == chatMessageData.fromSipUri
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                isLastOneOfGroup = if (position == itemCount - 1) {
                    true
                } else {
                    val next = position + 1
                    if (getItemViewType(next) == INCOMING_CHAT_MESSAGE) {
                        val nextItem = getItem(next).data as ChatMessageModel
                        if (kotlin.math.abs(chatMessageData.timestamp - nextItem.timestamp) < MAX_TIME_TO_GROUP_MESSAGES) {
                            nextItem.fromSipUri != chatMessageData.fromSipUri
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                }

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()
            }
        }
    }

    inner class OutgoingBubbleViewHolder(
        val binding: ChatBubbleOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatMessageData: ChatMessageModel) {
            with(binding) {
                model = chatMessageData

                val position = bindingAdapterPosition
                isGroupedWithPreviousOne = if (position == 0) {
                    false
                } else {
                    val previous = position - 1
                    if (getItemViewType(previous) == OUTGOING_CHAT_MESSAGE) {
                        val previousItem = getItem(previous).data as ChatMessageModel
                        if (kotlin.math.abs(chatMessageData.timestamp - previousItem.timestamp) < MAX_TIME_TO_GROUP_MESSAGES) {
                            previousItem.fromSipUri == chatMessageData.fromSipUri
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                isLastOneOfGroup = if (position == itemCount - 1) {
                    true
                } else {
                    val next = position + 1
                    if (getItemViewType(next) == INCOMING_CHAT_MESSAGE) {
                        val nextItem = getItem(next).data as ChatMessageModel
                        if (kotlin.math.abs(chatMessageData.timestamp - nextItem.timestamp) < MAX_TIME_TO_GROUP_MESSAGES) {
                            nextItem.fromSipUri != chatMessageData.fromSipUri
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                }

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()
            }
        }
    }
    inner class EventViewHolder(
        val binding: ChatEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(eventData: EventModel) {
            with(binding) {
                model = eventData

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()
            }
        }
    }

    private class EventLogDiffCallback : DiffUtil.ItemCallback<EventLogModel>() {
        override fun areItemsTheSame(oldItem: EventLogModel, newItem: EventLogModel): Boolean {
            return if (oldItem.isEvent && newItem.isEvent) {
                oldItem.notifyId == newItem.notifyId
            } else if (!oldItem.isEvent && !newItem.isEvent) {
                val oldData = (oldItem.data as ChatMessageModel)
                val newData = (newItem.data as ChatMessageModel)
                oldData.id.isNotEmpty() && oldData.id == newData.id
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: EventLogModel, newItem: EventLogModel): Boolean {
            return if (oldItem.isEvent && newItem.isEvent) {
                true
            } else {
                val newData = (newItem.data as ChatMessageModel)
                newData.state.value == ChatMessage.State.Displayed
            }
        }
    }
}

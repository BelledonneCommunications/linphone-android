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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatBubbleIncomingBinding
import org.linphone.databinding.ChatBubbleOutgoingBinding
import org.linphone.databinding.ChatConversationEventBinding
import org.linphone.databinding.ChatConversationE2eEncryptedFirstEventBinding
import org.linphone.databinding.ChatConversationUnsafeFirstEventBinding
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.chat.model.EventModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter
import org.linphone.utils.startAnimatedDrawable

class ConversationEventAdapter :
    ListAdapter<EventLogModel, RecyclerView.ViewHolder>(
        EventLogDiffCallback()
    ),
    HeaderAdapter {
    companion object {
        private const val TAG = "[Conversation Event Adapter]"

        const val INCOMING_CHAT_MESSAGE = 1
        const val OUTGOING_CHAT_MESSAGE = 2
        const val EVENT = 3
    }

    val chatMessageLongPressEvent = MutableLiveData<Event<MessageModel>>()

    val showDeliveryForChatMessageModelEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    val showReactionForChatMessageModelEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    val scrollToRepliedMessageEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    private var isConversationSecured: Boolean = false

    fun setIsConversationSecured(secured: Boolean) {
        isConversationSecured = secured
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        // We only want to display it at top
        return position == 0
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = if (isConversationSecured) {
            ChatConversationE2eEncryptedFirstEventBinding.inflate(LayoutInflater.from(context))
        } else {
            ChatConversationUnsafeFirstEventBinding.inflate(LayoutInflater.from(context))
        }
        return binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            INCOMING_CHAT_MESSAGE -> createIncomingChatBubble(parent)
            OUTGOING_CHAT_MESSAGE -> createOutgoingChatBubble(parent)
            else -> createEvent(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)
        if (data.isEvent) return EVENT

        if ((data.model as MessageModel).isOutgoing) {
            return OUTGOING_CHAT_MESSAGE
        }
        return INCOMING_CHAT_MESSAGE
    }

    fun getFirstUnreadMessagePosition(): Int {
        var index = 0
        for (eventLog in currentList) {
            if (eventLog.model is MessageModel) {
                if (!eventLog.model.isRead) {
                    Log.i("$TAG First unread message is [${eventLog.model.id}] at index [$index]")
                    return index
                }
            }
            index += 1
        }
        Log.i("$TAG No unread message found in list of [${currentList.size}] events")
        return -1
    }

    private fun createIncomingChatBubble(parent: ViewGroup): IncomingBubbleViewHolder {
        val binding: ChatBubbleIncomingBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_incoming,
            parent,
            false
        )
        val viewHolder = IncomingBubbleViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnLongClickListener {
                chatMessageLongPressEvent.value = Event(model!!)
                true
            }

            setShowDeliveryInfoClickListener {
                showDeliveryForChatMessageModelEvent.value = Event(model!!)
            }
            setShowReactionInfoClickListener {
                showReactionForChatMessageModelEvent.value = Event(model!!)
            }
            setScrollToRepliedMessageClickListener {
                scrollToRepliedMessageEvent.value = Event(model!!)
            }
        }
        return viewHolder
    }

    private fun createOutgoingChatBubble(parent: ViewGroup): OutgoingBubbleViewHolder {
        val binding: ChatBubbleOutgoingBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_outgoing,
            parent,
            false
        )
        val viewHolder = OutgoingBubbleViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnLongClickListener {
                chatMessageLongPressEvent.value = Event(model!!)
                true
            }

            setShowDeliveryInfoClickListener {
                showDeliveryForChatMessageModelEvent.value = Event(model!!)
            }
            setShowReactionInfoClickListener {
                showReactionForChatMessageModelEvent.value = Event(model!!)
            }
            setScrollToRepliedMessageClickListener {
                scrollToRepliedMessageEvent.value = Event(model!!)
            }
        }
        return viewHolder
    }

    private fun createEvent(parent: ViewGroup): EventViewHolder {
        val binding: ChatConversationEventBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_conversation_event,
            parent,
            false
        )
        binding.lifecycleOwner = parent.findViewTreeLifecycleOwner()
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val eventLog = getItem(position)
        when (holder) {
            is IncomingBubbleViewHolder -> holder.bind(eventLog.model as MessageModel)
            is OutgoingBubbleViewHolder -> holder.bind(eventLog.model as MessageModel)
            is EventViewHolder -> holder.bind(eventLog.model as EventModel)
        }
    }

    inner class IncomingBubbleViewHolder(
        val binding: ChatBubbleIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageModel) {
            with(binding) {
                model = message
                executePendingBindings()

                root.doOnPreDraw {
                    binding.deliveryStatus.startAnimatedDrawable()
                }
            }
        }
    }

    inner class OutgoingBubbleViewHolder(
        val binding: ChatBubbleOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageModel) {
            with(binding) {
                model = message
                executePendingBindings()

                root.doOnPreDraw {
                    binding.deliveryStatus.startAnimatedDrawable()
                }
            }
        }
    }

    inner class EventViewHolder(
        val binding: ChatConversationEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: EventModel) {
            with(binding) {
                model = event
                executePendingBindings()
            }
        }
    }

    private class EventLogDiffCallback : DiffUtil.ItemCallback<EventLogModel>() {
        override fun areItemsTheSame(oldItem: EventLogModel, newItem: EventLogModel): Boolean {
            return if (!oldItem.isEvent && !newItem.isEvent) {
                val oldData = (oldItem.model as MessageModel)
                val newData = (newItem.model as MessageModel)
                oldData.id == newData.id &&
                    oldData.timestamp == newData.timestamp &&
                    oldData.isOutgoing == newData.isOutgoing
            } else {
                oldItem.notifyId == newItem.notifyId
            }
        }

        override fun areContentsTheSame(oldItem: EventLogModel, newItem: EventLogModel): Boolean {
            return if (!oldItem.isEvent && !newItem.isEvent) {
                val oldModel = (oldItem.model as MessageModel)
                val newModel = (newItem.model as MessageModel)
                newModel.isRead &&
                    oldModel.groupedWithNextMessage.value == newModel.groupedWithNextMessage.value &&
                    oldModel.groupedWithPreviousMessage.value == newModel.groupedWithPreviousMessage.value
            } else {
                true
            }
        }
    }
}

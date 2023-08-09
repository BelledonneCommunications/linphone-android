package org.linphone.ui.main.conversations.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.databinding.ChatBubbleIncomingBinding
import org.linphone.databinding.ChatBubbleOutgoingBinding
import org.linphone.databinding.ChatEventBinding
import org.linphone.ui.main.conversations.data.ChatMessageData
import org.linphone.ui.main.conversations.data.EventData
import org.linphone.ui.main.conversations.data.EventLogData

class ChatEventLogsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<EventLogData, RecyclerView.ViewHolder>(EventLogDiffCallback()) {
    companion object {
        const val INCOMING_CHAT_MESSAGE = 1
        const val OUTGOING_CHAT_MESSAGE = 2
        const val EVENT = 3
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
        if (data.data is ChatMessageData) {
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
            is IncomingBubbleViewHolder -> holder.bind(eventLog.data as ChatMessageData)
            is OutgoingBubbleViewHolder -> holder.bind(eventLog.data as ChatMessageData)
            is EventViewHolder -> holder.bind(eventLog.data as EventData)
        }
    }

    inner class IncomingBubbleViewHolder(
        val binding: ChatBubbleIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatMessageData: ChatMessageData) {
            with(binding) {
                data = chatMessageData

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()

                // To ensure the measure is right since we do some computation for proper multi-line wrap_content
                text.forceLayout()
            }
        }
    }

    inner class OutgoingBubbleViewHolder(
        val binding: ChatBubbleOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatMessageData: ChatMessageData) {
            with(binding) {
                data = chatMessageData

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()

                // To ensure the measure is right since we do some computation for proper multi-line wrap_content
                text.forceLayout()
            }
        }
    }
    inner class EventViewHolder(
        val binding: ChatEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(eventData: EventData) {
            with(binding) {
                data = eventData

                lifecycleOwner = viewLifecycleOwner
                executePendingBindings()
            }
        }
    }
}

private class EventLogDiffCallback : DiffUtil.ItemCallback<EventLogData>() {
    override fun areItemsTheSame(oldItem: EventLogData, newItem: EventLogData): Boolean {
        return if (oldItem.isEvent && newItem.isEvent) {
            oldItem.notifyId == newItem.notifyId
        } else if (!oldItem.isEvent && !newItem.isEvent) {
            val oldData = (oldItem.data as ChatMessageData)
            val newData = (newItem.data as ChatMessageData)
            oldData.id.isNotEmpty() && oldData.id == newData.id
        } else {
            false
        }
    }

    override fun areContentsTheSame(oldItem: EventLogData, newItem: EventLogData): Boolean {
        return if (oldItem.isEvent && newItem.isEvent) {
            true
        } else {
            val newData = (newItem.data as ChatMessageData)
            newData.state.value == ChatMessage.State.Displayed
        }
    }
}

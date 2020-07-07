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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.chat.viewmodels.ChatMessageViewModel
import org.linphone.activities.main.chat.viewmodels.EventViewModel
import org.linphone.activities.main.chat.viewmodels.OnContentClickedListener
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoomCapabilities
import org.linphone.core.EventLog
import org.linphone.databinding.ChatEventListCellBinding
import org.linphone.databinding.ChatMessageListCellBinding
import org.linphone.utils.Event
import org.linphone.utils.LifecycleListAdapter
import org.linphone.utils.LifecycleViewHolder

class ChatMessagesListAdapter(val selectionViewModel: ListTopBarViewModel) : LifecycleListAdapter<EventLog, LifecycleViewHolder>(ChatMessageDiffCallback()) {
    companion object {
        const val MAX_TIME_TO_GROUP_MESSAGES = 300 // 5 minutes
    }

    val resendMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val deleteMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val forwardMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val showImdnForMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val addSipUriToContactEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val openContentEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val contentClickedListener = object : OnContentClickedListener {
        override fun onContentClicked(path: String) {
            openContentEvent.value = Event(path)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LifecycleViewHolder {
        return when (viewType) {
            EventLog.Type.ConferenceChatMessage.toInt() -> createChatMessageViewHolder(parent)
            else -> createEventViewHolder(parent)
        }
    }

    private fun createChatMessageViewHolder(parent: ViewGroup): ChatMessageViewHolder {
        val binding: ChatMessageListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_message_list_cell, parent, false
        )
        val viewHolder = ChatMessageViewHolder(binding)
        binding.lifecycleOwner = viewHolder
        return viewHolder
    }

    private fun createEventViewHolder(parent: ViewGroup): EventViewHolder {
        val binding: ChatEventListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_event_list_cell, parent, false
        )
        val viewHolder = EventViewHolder(binding)
        binding.lifecycleOwner = viewHolder
        return viewHolder
    }

    override fun onBindViewHolder(holder: LifecycleViewHolder, position: Int) {
        val eventLog = getItem(position)
        when (holder) {
            is ChatMessageViewHolder -> holder.bind(eventLog)
            is EventViewHolder -> holder.bind(eventLog)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val eventLog = getItem(position)
        return eventLog.type.toInt()
    }

    inner class ChatMessageViewHolder(
        private val binding: ChatMessageListCellBinding
    ) : LifecycleViewHolder(binding), PopupMenu.OnMenuItemClickListener {
        fun bind(eventLog: EventLog) {
            with(binding) {
                if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                    val chatMessage = eventLog.chatMessage
                    chatMessage ?: return
                    val chatMessageViewModel = ChatMessageViewModel(chatMessage, contentClickedListener)
                    viewModel = chatMessageViewModel

                    // This is for item selection through ListTopBarFragment
                    selectionListViewModel = selectionViewModel
                    selectionViewModel.isEditionEnabled.observe(this@ChatMessageViewHolder, Observer {
                        position = adapterPosition
                    })

                    binding.setClickListener {
                        if (selectionViewModel.isEditionEnabled.value == true) {
                            selectionViewModel.onToggleSelect(adapterPosition)
                        }
                    }

                    // Grouping
                    var hasPrevious = false
                    var hasNext = false

                    if (adapterPosition > 0) {
                        val previousItem = getItem(adapterPosition - 1)
                        if (previousItem.type == EventLog.Type.ConferenceChatMessage) {
                            val previousMessage = previousItem.chatMessage
                            if (previousMessage != null && previousMessage.fromAddress.weakEqual(chatMessage.fromAddress)) {
                                if (chatMessage.time - previousMessage.time < MAX_TIME_TO_GROUP_MESSAGES) {
                                    hasPrevious = true
                                }
                            }
                        }
                    }

                    if (adapterPosition >= 0 && adapterPosition < itemCount - 1) {
                        val nextItem = getItem(adapterPosition + 1)
                        if (nextItem.type == EventLog.Type.ConferenceChatMessage) {
                            val nextMessage = nextItem.chatMessage
                            if (nextMessage != null && nextMessage.fromAddress.weakEqual(chatMessage.fromAddress)) {
                                if (nextMessage.time - chatMessage.time < MAX_TIME_TO_GROUP_MESSAGES) {
                                    hasNext = true
                                }
                            }
                        }
                    }

                    chatMessageViewModel.updateBubbleBackground(hasPrevious, hasNext)

                    executePendingBindings()

                    setContextMenuClickListener {
                        val popup = PopupMenu(root.context, background)
                        popup.setOnMenuItemClickListener(this@ChatMessageViewHolder)
                        popup.inflate(R.menu.chat_message_menu)

                        if (!chatMessage.isOutgoing ||
                            chatMessage.chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt()) ||
                            chatMessage.state == ChatMessage.State.NotDelivered) { // No message id
                            popup.menu.removeItem(R.id.chat_message_menu_imdn_infos)
                        }
                        if (chatMessage.state != ChatMessage.State.NotDelivered) {
                            popup.menu.removeItem(R.id.chat_message_menu_resend)
                        }
                        if (!chatMessage.hasTextContent()) {
                            popup.menu.removeItem(R.id.chat_message_menu_copy_text)
                        }
                        if (chatMessageViewModel.contact.value != null) {
                            popup.menu.removeItem(R.id.chat_message_menu_add_to_contacts)
                        }

                        popup.show()
                        true
                    }
                }
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.chat_message_menu_imdn_infos -> {
                    showImdnDeliveryFragment()
                    true
                }
                R.id.chat_message_menu_resend -> {
                    resendMessage()
                    true
                }
                R.id.chat_message_menu_copy_text -> {
                    copyTextToClipboard()
                    true
                }
                R.id.chat_message_forward_message -> {
                    forwardMessage()
                    true
                }
                R.id.chat_message_menu_delete_message -> {
                    deleteMessage()
                    true
                }
                R.id.chat_message_menu_add_to_contacts -> {
                    addSenderToContacts()
                    true
                }
                else -> false
            }
        }

        private fun resendMessage() {
            val chatMessage = binding.viewModel?.chatMessage
            if (chatMessage != null) {
                val viewHolder = binding.lifecycleOwner as ChatMessageViewHolder
                chatMessage.userData = viewHolder.adapterPosition
                resendMessageEvent.value = Event(chatMessage)
            }
        }

        private fun copyTextToClipboard() {
            val chatMessage = binding.viewModel?.chatMessage
            if (chatMessage != null && chatMessage.hasTextContent()) {
                val clipboard: ClipboardManager = coreContext.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Message", chatMessage.textContent)
                clipboard.setPrimaryClip(clip)
            }
        }

        private fun forwardMessage() {
            val chatMessage = binding.viewModel?.chatMessage
            if (chatMessage != null) {
                forwardMessageEvent.value = Event(chatMessage)
            }
        }

        private fun showImdnDeliveryFragment() {
            val chatMessage = binding.viewModel?.chatMessage
            if (chatMessage != null) {
                showImdnForMessageEvent.value = Event(chatMessage)
            }
        }

        private fun deleteMessage() {
            val chatMessage = binding.viewModel?.chatMessage
            if (chatMessage != null) {
                val viewHolder = binding.lifecycleOwner as ChatMessageViewHolder
                chatMessage.userData = viewHolder.adapterPosition
                deleteMessageEvent.value = Event(chatMessage)
            }
        }

        private fun addSenderToContacts() {
            val chatMessage = binding.viewModel?.chatMessage
            if (chatMessage != null) {
                chatMessage.fromAddress.clean() // To remove gruu if any
                addSipUriToContactEvent.value = Event(chatMessage.fromAddress.asStringUriOnly())
            }
        }
    }

    inner class EventViewHolder(
        private val binding: ChatEventListCellBinding
    ) : LifecycleViewHolder(binding) {
        fun bind(eventLog: EventLog) {
            with(binding) {
                val eventViewModel = EventViewModel(eventLog)
                viewModel = eventViewModel

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(this@EventViewHolder, Observer {
                    position = adapterPosition
                })

                binding.setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(adapterPosition)
                    }
                }

                executePendingBindings()
            }
        }
    }
}

private class ChatMessageDiffCallback : DiffUtil.ItemCallback<EventLog>() {
    override fun areItemsTheSame(
        oldItem: EventLog,
        newItem: EventLog
    ): Boolean {
        return if (oldItem.type == EventLog.Type.ConferenceChatMessage &&
            newItem.type == EventLog.Type.ConferenceChatMessage) {
            oldItem.chatMessage?.time == newItem.chatMessage?.time &&
                    oldItem.chatMessage?.isOutgoing == newItem.chatMessage?.isOutgoing
        } else oldItem.notifyId == newItem.notifyId
    }

    override fun areContentsTheSame(
        oldItem: EventLog,
        newItem: EventLog
    ): Boolean {
        return if (newItem.type == EventLog.Type.ConferenceChatMessage) {
            newItem.chatMessage?.state == ChatMessage.State.Displayed
        } else false
    }
}

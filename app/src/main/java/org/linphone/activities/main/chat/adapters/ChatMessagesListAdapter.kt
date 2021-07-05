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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.adapters.SelectionListAdapter
import org.linphone.activities.main.chat.data.ChatMessageData
import org.linphone.activities.main.chat.data.EventData
import org.linphone.activities.main.chat.data.EventLogData
import org.linphone.activities.main.chat.data.OnContentClickedListener
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoomCapabilities
import org.linphone.core.Content
import org.linphone.core.EventLog
import org.linphone.databinding.ChatEventListCellBinding
import org.linphone.databinding.ChatMessageListCellBinding
import org.linphone.databinding.ChatMessageLongPressMenuBindingImpl
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class ChatMessagesListAdapter(
    selectionVM: ListTopBarViewModel,
    private val viewLifecycleOwner: LifecycleOwner
) : SelectionListAdapter<EventLogData, RecyclerView.ViewHolder>(selectionVM, ChatMessageDiffCallback()) {
    companion object {
        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
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

    val replyMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val showImdnForMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val addSipUriToContactEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val openContentEvent: MutableLiveData<Event<Content>> by lazy {
        MutableLiveData<Event<Content>>()
    }

    val scrollToChatMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    private val contentClickedListener = object : OnContentClickedListener {
        override fun onContentClicked(content: Content) {
            openContentEvent.value = Event(content)
        }
    }

    private var contextMenuDisabled: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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
        return ChatMessageViewHolder(binding)
    }

    private fun createEventViewHolder(parent: ViewGroup): EventViewHolder {
        val binding: ChatEventListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_event_list_cell, parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val eventLog = getItem(position)
        when (holder) {
            is ChatMessageViewHolder -> holder.bind(eventLog)
            is EventViewHolder -> holder.bind(eventLog)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val eventLog = getItem(position)
        return eventLog.eventLog.type.toInt()
    }

    fun disableContextMenu() {
        contextMenuDisabled = true
    }

    inner class ChatMessageViewHolder(
        val binding: ChatMessageListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(eventLog: EventLogData) {
            with(binding) {
                if (eventLog.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                    val chatMessageViewModel = eventLog.data as ChatMessageData
                    chatMessageViewModel.setContentClickListener(contentClickedListener)

                    val chatMessage = chatMessageViewModel.chatMessage
                    data = chatMessageViewModel

                    lifecycleOwner = viewLifecycleOwner

                    // This is for item selection through ListTopBarFragment
                    selectionListViewModel = selectionViewModel
                    selectionViewModel.isEditionEnabled.observe(viewLifecycleOwner, {
                        position = adapterPosition
                    })

                    setClickListener {
                        if (selectionViewModel.isEditionEnabled.value == true) {
                            selectionViewModel.onToggleSelect(adapterPosition)
                        }
                    }

                    setReplyClickListener {
                        val reply = chatMessageViewModel.replyData.value?.chatMessage
                        if (reply != null) {
                            scrollToChatMessageEvent.value = Event(reply)
                        }
                    }

                    // Grouping
                    var hasPrevious = false
                    var hasNext = false

                    if (adapterPosition > 0) {
                        val previousItem = getItem(adapterPosition - 1)
                        if (previousItem.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                            val previousMessage = previousItem.eventLog.chatMessage
                            if (previousMessage != null && previousMessage.fromAddress.weakEqual(chatMessage.fromAddress)) {
                                if (chatMessage.time - previousMessage.time < MAX_TIME_TO_GROUP_MESSAGES) {
                                    hasPrevious = true
                                }
                            }
                        }
                    }

                    if (adapterPosition >= 0 && adapterPosition < itemCount - 1) {
                        val nextItem = getItem(adapterPosition + 1)
                        if (nextItem.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                            val nextMessage = nextItem.eventLog.chatMessage
                            if (nextMessage != null && nextMessage.fromAddress.weakEqual(chatMessage.fromAddress)) {
                                if (nextMessage.time - chatMessage.time < MAX_TIME_TO_GROUP_MESSAGES) {
                                    hasNext = true
                                }
                            }
                        }
                    }

                    chatMessageViewModel.updateBubbleBackground(hasPrevious, hasNext)

                    executePendingBindings()

                    if (contextMenuDisabled) return

                    setContextMenuClickListener {
                        val popupView: ChatMessageLongPressMenuBindingImpl = DataBindingUtil.inflate(
                            LayoutInflater.from(root.context),
                            R.layout.chat_message_long_press_menu, null, false
                        )

                        val itemSize = AppUtils.getDimension(R.dimen.chat_message_popup_item_height).toInt()
                        var totalSize = itemSize * 7
                        if (chatMessage.chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) ||
                                chatMessage.state == ChatMessage.State.NotDelivered) { // No message id
                            popupView.imdnHidden = true
                            totalSize -= itemSize
                        }
                        if (chatMessage.state != ChatMessage.State.NotDelivered) {
                            popupView.resendHidden = true
                            totalSize -= itemSize
                        }
                        if (chatMessage.contents.find { content -> content.isText } == null) {
                            popupView.copyTextHidden = true
                            totalSize -= itemSize
                        }
                        if (chatMessage.isOutgoing || chatMessageViewModel.contact.value != null) {
                            popupView.addToContactsHidden = true
                            totalSize -= itemSize
                        }

                        // When using WRAP_CONTENT instead of real size, fails to place the
                        // popup window above if not enough space is available below
                        val popupWindow = PopupWindow(popupView.root,
                            AppUtils.getDimension(R.dimen.chat_message_popup_width).toInt(),
                            totalSize,
                            true
                        )
                        // Elevation is for showing a shadow around the popup
                        popupWindow.elevation = 20f

                        popupView.setResendClickListener {
                            resendMessage()
                            popupWindow.dismiss()
                        }
                        popupView.setCopyTextClickListener {
                            copyTextToClipboard()
                            popupWindow.dismiss()
                        }
                        popupView.setForwardClickListener {
                            forwardMessage()
                            popupWindow.dismiss()
                        }
                        popupView.setReplyClickListener {
                            replyMessage()
                            popupWindow.dismiss()
                        }
                        popupView.setImdnClickListener {
                            showImdnDeliveryFragment()
                            popupWindow.dismiss()
                        }
                        popupView.setAddToContactsClickListener {
                            addSenderToContacts()
                            popupWindow.dismiss()
                        }
                        popupView.setDeleteClickListener {
                            deleteMessage()
                            popupWindow.dismiss()
                        }

                        val gravity = if (chatMessage.isOutgoing) Gravity.END else Gravity.START
                        popupWindow.showAsDropDown(background, 0, 0, gravity or Gravity.TOP)

                        true
                    }
                }
            }
        }

        private fun resendMessage() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                chatMessage.userData = adapterPosition
                resendMessageEvent.value = Event(chatMessage)
            }
        }

        private fun copyTextToClipboard() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                val content = chatMessage.contents.find { content -> content.isText }
                if (content != null) {
                    val clipboard: ClipboardManager =
                        coreContext.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Message", content.utf8Text)
                    clipboard.setPrimaryClip(clip)
                }
            }
        }

        private fun forwardMessage() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                forwardMessageEvent.value = Event(chatMessage)
            }
        }

        private fun replyMessage() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                replyMessageEvent.value = Event(chatMessage)
            }
        }

        private fun showImdnDeliveryFragment() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                showImdnForMessageEvent.value = Event(chatMessage)
            }
        }

        private fun deleteMessage() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                chatMessage.userData = adapterPosition
                deleteMessageEvent.value = Event(chatMessage)
            }
        }

        private fun addSenderToContacts() {
            val chatMessage = binding.data?.chatMessage
            if (chatMessage != null) {
                val copy = chatMessage.fromAddress.clone()
                copy.clean() // To remove gruu if any
                addSipUriToContactEvent.value = Event(copy.asStringUriOnly())
            }
        }
    }

    inner class EventViewHolder(
        private val binding: ChatEventListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(eventLog: EventLogData) {
            with(binding) {
                val eventViewModel = eventLog.data as EventData
                data = eventViewModel

                binding.lifecycleOwner = viewLifecycleOwner

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(viewLifecycleOwner, {
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

private class ChatMessageDiffCallback : DiffUtil.ItemCallback<EventLogData>() {
    override fun areItemsTheSame(
        oldItem: EventLogData,
        newItem: EventLogData
    ): Boolean {
        return if (oldItem.eventLog.type == EventLog.Type.ConferenceChatMessage &&
            newItem.eventLog.type == EventLog.Type.ConferenceChatMessage) {
            oldItem.eventLog.chatMessage?.time == newItem.eventLog.chatMessage?.time &&
                    oldItem.eventLog.chatMessage?.isOutgoing == newItem.eventLog.chatMessage?.isOutgoing
        } else oldItem.eventLog.notifyId == newItem.eventLog.notifyId
    }

    override fun areContentsTheSame(
        oldItem: EventLogData,
        newItem: EventLogData
    ): Boolean {
        return if (newItem.eventLog.type == EventLog.Type.ConferenceChatMessage) {
            newItem.eventLog.chatMessage?.state == ChatMessage.State.Displayed
        } else true
    }
}

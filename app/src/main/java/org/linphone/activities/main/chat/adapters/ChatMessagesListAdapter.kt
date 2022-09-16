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
import android.view.View
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
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatEventListCellBinding
import org.linphone.databinding.ChatMessageListCellBinding
import org.linphone.databinding.ChatMessageLongPressMenuBindingImpl
import org.linphone.databinding.ChatUnreadMessagesListHeaderBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class ChatMessagesListAdapter(
    selectionVM: ListTopBarViewModel,
    private val viewLifecycleOwner: LifecycleOwner
) : SelectionListAdapter<EventLogData, RecyclerView.ViewHolder>(selectionVM, ChatMessageDiffCallback()),
    HeaderAdapter {
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

    val urlClickEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val sipUriClickedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val callConferenceEvent: MutableLiveData<Event<Pair<String, String?>>> by lazy {
        MutableLiveData<Event<Pair<String, String?>>>()
    }

    val scrollToChatMessageEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val errorEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    private val contentClickedListener = object : OnContentClickedListener {
        override fun onContentClicked(content: Content) {
            openContentEvent.value = Event(content)
        }

        override fun onWebUrlClicked(url: String) {
            if (popup?.isShowing == true) {
                Log.w("[Chat Message Data] Long press that displayed context menu detected, aborting click on URL [$url]")
                return
            }
            urlClickEvent.value = Event(url)
        }

        override fun onSipAddressClicked(sipUri: String) {
            if (popup?.isShowing == true) {
                Log.w("[Chat Message Data] Long press that displayed context menu detected, aborting click on SIP URI [$sipUri]")
                return
            }
            sipUriClickedEvent.value = Event(sipUri)
        }

        override fun onCallConference(address: String, subject: String?) {
            callConferenceEvent.value = Event(Pair(address, subject))
        }

        override fun onError(messageId: Int) {
            errorEvent.value = Event(messageId)
        }
    }

    private var advancedContextMenuOptionsDisabled: Boolean = false
    private var popup: PopupWindow? = null

    private var unreadMessagesCount: Int = 0
    private var firstUnreadMessagePosition: Int = -1

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

    override fun onCurrentListChanged(
        previousList: MutableList<EventLogData>,
        currentList: MutableList<EventLogData>
    ) {
        // Need to wait for messages to be added before computing new first unread message position
        firstUnreadMessagePosition = -1
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (unreadMessagesCount > 0 && firstUnreadMessagePosition == -1) {
            computeFirstUnreadMessagePosition()
        }
        return position == firstUnreadMessagePosition
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding: ChatUnreadMessagesListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.chat_unread_messages_list_header, null, false
        )
        binding.title = AppUtils.getStringWithPlural(R.plurals.chat_room_unread_messages_event, unreadMessagesCount)
        binding.executePendingBindings()
        return binding.root
    }

    fun disableAdvancedContextMenuOptions() {
        advancedContextMenuOptionsDisabled = true
    }

    fun setUnreadMessageCount(count: Int, forceUpdate: Boolean) {
        // Once list has been filled once, don't show the unread message header
        // when new messages are added to the history whilst it is visible
        unreadMessagesCount = if (itemCount == 0 || forceUpdate) count else 0
        firstUnreadMessagePosition = -1
    }

    fun getFirstUnreadMessagePosition(): Int {
        return firstUnreadMessagePosition
    }

    private fun computeFirstUnreadMessagePosition() {
        if (unreadMessagesCount > 0) {
            var messageCount = 0
            for (position in itemCount - 1 downTo 0) {
                val eventLog = getItem(position)
                val data = eventLog.data
                if (data is ChatMessageData) {
                    messageCount += 1
                    if (messageCount == unreadMessagesCount) {
                        firstUnreadMessagePosition = position
                        break
                    }
                }
            }
        }
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
                    selectionViewModel.isEditionEnabled.observe(
                        viewLifecycleOwner
                    ) {
                        position = bindingAdapterPosition
                    }

                    setClickListener {
                        if (selectionViewModel.isEditionEnabled.value == true) {
                            selectionViewModel.onToggleSelect(bindingAdapterPosition)
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

                    if (bindingAdapterPosition > 0) {
                        val previousItem = getItem(bindingAdapterPosition - 1)
                        if (previousItem.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                            val previousMessage = previousItem.eventLog.chatMessage
                            if (previousMessage != null && previousMessage.fromAddress.weakEqual(chatMessage.fromAddress)) {
                                if (chatMessage.time - previousMessage.time < MAX_TIME_TO_GROUP_MESSAGES) {
                                    hasPrevious = true
                                }
                            }
                        }
                    }

                    if (bindingAdapterPosition >= 0 && bindingAdapterPosition < itemCount - 1) {
                        val nextItem = getItem(bindingAdapterPosition + 1)
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

                    setContextMenuClickListener {
                        val popupView: ChatMessageLongPressMenuBindingImpl = DataBindingUtil.inflate(
                            LayoutInflater.from(root.context),
                            R.layout.chat_message_long_press_menu, null, false
                        )

                        val itemSize = AppUtils.getDimension(R.dimen.chat_message_popup_item_height).toInt()
                        var totalSize = itemSize * 7
                        if (chatMessage.chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                            // No message id
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
                        if (chatMessage.isOutgoing ||
                            chatMessageViewModel.contact.value != null ||
                            advancedContextMenuOptionsDisabled
                        ) {
                            popupView.addToContactsHidden = true
                            totalSize -= itemSize
                        }
                        if (chatMessage.chatRoom.isReadOnly) {
                            popupView.replyHidden = true
                            totalSize -= itemSize
                        }
                        if (advancedContextMenuOptionsDisabled) {
                            popupView.forwardHidden = true
                            totalSize -= itemSize
                        }

                        // When using WRAP_CONTENT instead of real size, fails to place the
                        // popup window above if not enough space is available below
                        val popupWindow = PopupWindow(
                            popupView.root,
                            AppUtils.getDimension(R.dimen.chat_message_popup_width).toInt(),
                            totalSize,
                            true
                        )
                        popup = popupWindow

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
                chatMessage.userData = bindingAdapterPosition
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
                chatMessage.userData = bindingAdapterPosition
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
                selectionViewModel.isEditionEnabled.observe(
                    viewLifecycleOwner
                ) {
                    position = bindingAdapterPosition
                }

                binding.setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(bindingAdapterPosition)
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
        return if (oldItem.type == EventLog.Type.ConferenceChatMessage &&
            newItem.type == EventLog.Type.ConferenceChatMessage
        ) {
            val oldData = (oldItem.data as ChatMessageData)
            val newData = (newItem.data as ChatMessageData)

            oldData.time.value == newData.time.value &&
                oldData.isOutgoing == newData.isOutgoing
        } else oldItem.notifyId == newItem.notifyId
    }

    override fun areContentsTheSame(
        oldItem: EventLogData,
        newItem: EventLogData
    ): Boolean {
        return if (oldItem.type == EventLog.Type.ConferenceChatMessage &&
            newItem.type == EventLog.Type.ConferenceChatMessage
        ) {
            val oldData = (oldItem.data as ChatMessageData)
            val newData = (newItem.data as ChatMessageData)

            val previous = oldData.hasPreviousMessage == newData.hasPreviousMessage
            val next = oldData.hasNextMessage == newData.hasNextMessage
            val isDisplayed = newData.isDisplayed.value == true
            isDisplayed && previous && next
        } else {
            oldItem.type != EventLog.Type.ConferenceChatMessage &&
                newItem.type != EventLog.Type.ConferenceChatMessage
        }
    }
}

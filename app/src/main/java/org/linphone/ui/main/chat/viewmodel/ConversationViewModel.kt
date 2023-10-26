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
package org.linphone.ui.main.chat.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
    }

    val showBackButton = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val events = MutableLiveData<ArrayList<EventLogModel>>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val composingLabel = MutableLiveData<String>()

    val textToSend = MutableLiveData<String>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val isEmojiPickerOpen = MutableLiveData<Boolean>()

    val isReplying = MutableLiveData<Boolean>()

    val isReplyingTo = MutableLiveData<String>()

    val isReplyingToMessage = MutableLiveData<String>()

    val requestKeyboardHidingEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var chatRoom: ChatRoom

    private var chatMessageToReplyTo: ChatMessage? = null

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Chat message [$message] is being sent")

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())

            val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                message?.localAddress
            )
            val lastEvent = events.value.orEmpty().lastOrNull()
            val group = if (lastEvent != null) {
                shouldWeGroupTwoEvents(eventLog, lastEvent.eventLog)
            } else {
                false
            }
            list.add(
                EventLogModel(
                    eventLog,
                    avatarModel,
                    LinphoneUtils.isChatRoomAGroup(chatRoom),
                    group,
                    true
                )
            )

            events.postValue(list)
        }

        @WorkerThread
        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Chat message [$message] has been sent")
        }

        @WorkerThread
        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            isComposing: Boolean
        ) {
            Log.i(
                "$TAG Remote [${remoteAddress.asStringUriOnly()}] is ${if (isComposing) "composing" else "no longer composing"}"
            )
            computeComposingLabel()
        }

        @WorkerThread
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<EventLog>) {
            Log.i("$TAG Received [${eventLogs.size}] new message(s)")
            chatRoom.markAsRead()
            computeComposingLabel()

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())

            val newList = getEventsListFromHistory(
                eventLogs,
                searchFilter.value.orEmpty().trim()
            )
            list.addAll(newList)

            // TODO: handle case when first one of the newly received messages should be grouped with last one of the current list

            events.postValue(list)
        }
    }

    init {
        searchBarVisible.value = false
        isEmojiPickerOpen.value = false
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                chatRoom.removeListener(chatRoomListener)
            }
            events.value.orEmpty().forEach(EventLogModel::destroy)
        }
    }

    @UiThread
    fun openSearchBar() {
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    @UiThread
    fun closeSearchBar() {
        clearFilter()
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    @UiThread
    fun clearFilter() {
        searchFilter.value = ""
    }

    @UiThread
    fun findChatRoom(localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for chat room with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)
            if (localAddress != null && remoteAddress != null) {
                val found = core.searchChatRoom(
                    null,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls(
                        0
                    )
                )
                if (found != null) {
                    chatRoom = found
                    chatRoom.addListener(chatRoomListener)

                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                } else {
                    Log.e("$TAG Failed to find chat room given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            computeEvents(filter)
        }
    }

    @UiThread
    fun toggleEmojiPickerVisibility() {
        isEmojiPickerOpen.value = isEmojiPickerOpen.value == false
        if (isEmojiPickerOpen.value == true) {
            requestKeyboardHidingEvent.value = Event(true)
        }
    }

    @UiThread
    fun insertEmoji(emoji: String) {
        textToSend.value = "${textToSend.value.orEmpty()}$emoji"
    }

    @UiThread
    fun replyToMessage(model: ChatMessageModel) {
        coreContext.postOnCoreThread {
            val message = model.chatMessage
            Log.i("$TAG Pending reply to chat message [${message.messageId}]")
            chatMessageToReplyTo = message
            isReplyingTo.postValue(model.avatarModel.friend.name)
            isReplyingToMessage.postValue(LinphoneUtils.getTextDescribingMessage(message))
            isReplying.postValue(true)
        }
    }

    @UiThread
    fun cancelReply() {
        Log.i("$TAG Cancelling reply")
        isReplying.value = false
        chatMessageToReplyTo = null
    }

    @UiThread
    fun sendMessage() {
        coreContext.postOnCoreThread {
            val messageToReplyTo = chatMessageToReplyTo
            val message = if (messageToReplyTo != null) {
                Log.i("$TAG Sending message as reply to [${messageToReplyTo.messageId}]")
                chatRoom.createReplyMessage(messageToReplyTo)
            } else {
                chatRoom.createEmptyMessage()
            }

            val toSend = textToSend.value.orEmpty().trim()
            if (toSend.isNotEmpty()) {
                message.addUtf8TextContent(toSend)
            }

            if (message.contents.isNotEmpty()) {
                Log.i("$TAG Sending message")
                message.send()
            }

            Log.i("$TAG Message sent, re-setting defaults")
            textToSend.postValue("")
            cancelReply()
        }
    }

    @UiThread
    fun deleteChatMessage(chatMessageModel: ChatMessageModel) {
        coreContext.postOnCoreThread {
            val eventsLogs = events.value.orEmpty()
            val found = eventsLogs.find {
                it.model == chatMessageModel
            }
            if (found != null) {
                val list = arrayListOf<EventLogModel>()
                list.addAll(eventsLogs)
                list.remove(found)
                events.postValue(list)
            }

            Log.i("$TAG Deleting message id [${chatMessageModel.id}]")
            chatRoom.deleteMessage(chatMessageModel.chatMessage)
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        computeComposingLabel()

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Chat room with subject [${chatRoom.subject}] is read only!")
        }

        val group = LinphoneUtils.isChatRoomAGroup(chatRoom)
        isGroup.postValue(group)

        subject.postValue(chatRoom.subject)

        val friends = arrayListOf<Friend>()
        val address = if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            chatRoom.peerAddress
        } else {
            for (participant in chatRoom.participants) {
                val friend = coreContext.contactsManager.findContactByAddress(participant.address)
                if (friend != null && !friends.contains(friend)) {
                    friends.add(friend)
                }
            }

            val firstParticipant = chatRoom.participants.firstOrNull()
            firstParticipant?.address ?: chatRoom.peerAddress
        }

        val avatar = if (group) {
            val fakeFriend = coreContext.core.createFriend()
            val model = ContactAvatarModel(fakeFriend)
            model.setPicturesFromFriends(friends)
            model
        } else {
            coreContext.contactsManager.getContactAvatarModelForAddress(address)
        }
        avatarModel.postValue(avatar)

        computeEvents()
        chatRoom.markAsRead()
    }

    @WorkerThread
    private fun computeEvents(filter: String = "") {
        events.value.orEmpty().forEach(EventLogModel::destroy)

        val history = chatRoom.getHistoryEvents(0)
        val eventsList = getEventsListFromHistory(history, filter)
        events.postValue(eventsList)
    }

    @WorkerThread
    private fun processGroupedEvents(
        groupedEventLogs: ArrayList<EventLog>
    ): ArrayList<EventLogModel> {
        val groupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        val eventsList = arrayListOf<EventLogModel>()

        // Handle all events in group, then re-start a new group with current item
        var index = 0
        for (groupedEvent in groupedEventLogs) {
            val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(
                groupedEvent.chatMessage?.fromAddress
            )
            val model = EventLogModel(
                groupedEvent,
                avatar,
                groupChatRoom,
                index > 0,
                index == groupedEventLogs.size - 1
            )
            eventsList.add(model)

            index += 1
        }

        return eventsList
    }

    @WorkerThread
    private fun getEventsListFromHistory(history: Array<EventLog>, filter: String = ""): ArrayList<EventLogModel> {
        val eventsList = arrayListOf<EventLogModel>()
        val groupedEventLogs = arrayListOf<EventLog>()
        for (event in history) {
            if (filter.isNotEmpty()) {
                // TODO: let the SDK do it
                if (event.type == EventLog.Type.ConferenceChatMessage) {
                    val message = event.chatMessage ?: continue
                    val fromAddress = message.fromAddress
                    val model = coreContext.contactsManager.getContactAvatarModelForAddress(
                        fromAddress
                    )
                    if (
                        !model.name.value.orEmpty().contains(filter, ignoreCase = true) &&
                        !fromAddress.asStringUriOnly().contains(filter, ignoreCase = true) &&
                        !message.utf8Text.orEmpty().contains(filter, ignoreCase = true)
                    ) {
                        continue
                    }
                } else {
                    continue
                }
            }

            if (groupedEventLogs.isEmpty()) {
                groupedEventLogs.add(event)
                continue
            }

            val previousGroupEvent = groupedEventLogs.last()
            val groupEvents = shouldWeGroupTwoEvents(event, previousGroupEvent)

            if (!groupEvents) {
                eventsList.addAll(processGroupedEvents(groupedEventLogs))
                groupedEventLogs.clear()
            }

            groupedEventLogs.add(event)
        }

        if (groupedEventLogs.isNotEmpty()) {
            eventsList.addAll(processGroupedEvents(groupedEventLogs))
            groupedEventLogs.clear()
        }

        return eventsList
    }

    @WorkerThread
    private fun shouldWeGroupTwoEvents(event: EventLog, previousGroupEvent: EventLog): Boolean {
        return if (previousGroupEvent.type == EventLog.Type.ConferenceChatMessage && event.type == EventLog.Type.ConferenceChatMessage) {
            val previousChatMessage = previousGroupEvent.chatMessage!!
            val chatMessage = event.chatMessage!!

            // If they have the same direction, the same from address and were sent in a short timelapse, group them
            chatMessage.isOutgoing == previousChatMessage.isOutgoing &&
                chatMessage.fromAddress.weakEqual(previousChatMessage.fromAddress) &&
                kotlin.math.abs(chatMessage.time - previousChatMessage.time) < MAX_TIME_TO_GROUP_MESSAGES
        } else {
            false
        }
    }

    @WorkerThread
    private fun computeComposingLabel() {
        val composingFriends = arrayListOf<String>()
        var label = ""
        for (address in chatRoom.composingAddresses) {
            val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val name = avatar.name.value ?: LinphoneUtils.getDisplayName(address)
            composingFriends.add(name)
            label += "$name, "
        }
        if (composingFriends.size > 0) {
            label = label.dropLast(2)

            val format = AppUtils.getStringWithPlural(
                R.plurals.conversation_composing_label,
                composingFriends.size,
                label
            )
            composingLabel.postValue(format)
        } else {
            composingLabel.postValue("")
        }
    }
}

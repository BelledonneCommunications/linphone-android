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
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
        const val SCROLLING_POSITION_NOT_SET = -1
    }

    val showBackButton = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val events = MutableLiveData<ArrayList<EventLogModel>>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val composingLabel = MutableLiveData<String>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    var scrollingPosition: Int = SCROLLING_POSITION_NOT_SET

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val fileToDisplayEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val conferenceToJoinEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val openWebBrowserEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    lateinit var chatRoom: ChatRoom

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            Log.i("$TAG Conversation state changed [${chatRoom.state}]")
            if (chatRoom.state == ChatRoom.State.Created) {
                computeConversationInfo()
            }
        }

        @WorkerThread
        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Message [$message] is being sent")

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())
            val lastEvent = events.value.orEmpty().lastOrNull()
            if (lastEvent != null && shouldWeGroupTwoEvents(eventLog, lastEvent.eventLog)) {
                list.remove(lastEvent)

                val eventsLogsArray = arrayOf<EventLog>()
                eventsLogsArray[0] = lastEvent.eventLog
                eventsLogsArray[1] = eventLog

                val newList = getEventsListFromHistory(
                    eventsLogsArray,
                    searchFilter.value.orEmpty().trim()
                )
                list.addAll(newList)
            } else {
                val newList = getEventsListFromHistory(
                    arrayOf(eventLog),
                    searchFilter.value.orEmpty().trim()
                )
                list.addAll(newList)
            }

            events.postValue(list)
        }

        @WorkerThread
        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Message [$message] has been sent")
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
            computeComposingLabel()

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())
            val lastEvent = list.lastOrNull()

            if (lastEvent != null && shouldWeGroupTwoEvents(eventLogs.first(), lastEvent.eventLog)) {
                list.remove(lastEvent)

                val eventsLogsArray = arrayOf<EventLog>()
                eventsLogsArray[0] = lastEvent.eventLog
                var index = 1
                for (eventLog in eventLogs) {
                    eventsLogsArray[index] = eventLog
                    index += 1
                }

                val newList = getEventsListFromHistory(
                    eventsLogsArray,
                    searchFilter.value.orEmpty().trim()
                )
                list.addAll(newList)
            } else {
                val newList = getEventsListFromHistory(
                    eventLogs,
                    searchFilter.value.orEmpty().trim()
                )
                list.addAll(newList)
            }

            events.postValue(list)
            chatRoom.markAsRead()
        }

        @WorkerThread
        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Adding new ephemeral event [${eventLog.type}]")
            // Warning: when 2 ephemeral events are dispatched quickly one after the other,
            // one will be missing because events.postValue() hasn't completed yet !
            // TODO FIXME: Missing event !!!
            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())
            val fakeFriend = chatRoom.core.createFriend()
            val avatarModel = ContactAvatarModel(fakeFriend)
            list.add(EventLogModel(eventLog, avatarModel))
            events.postValue(list)
        }

        @WorkerThread
        override fun onEphemeralMessageDeleted(chatRoom: ChatRoom, eventLog: EventLog) {
            val eventsLogs = events.value.orEmpty()
            val message = eventLog.chatMessage
            Log.i("$TAG Message [${message?.messageId}] ephemeral lifetime has expired")

            val found = eventsLogs.find {
                (it.model as? MessageModel)?.chatMessage == message
            }
            if (found != null) {
                val list = arrayListOf<EventLogModel>()
                list.addAll(eventsLogs)
                list.remove(found)
                events.postValue(list)
                Log.i("$TAG Message was removed from events list")
            } else {
                Log.w("$TAG Failed to find matching message in events list")
            }
        }
    }

    init {
        searchBarVisible.value = false
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
    fun findChatRoom(room: ChatRoom?, localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )
            if (room != null && ::chatRoom.isInitialized && chatRoom == room) {
                Log.i("$TAG Conversation object already in memory, skipping")
                chatRoomFoundEvent.postValue(Event(true))
                return@postOnCoreThread
            }

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)

            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (localAddress?.weakEqual(room.localAddress) == true && remoteAddress?.weakEqual(
                        room.peerAddress
                    ) == true
                ) {
                    Log.i("$TAG Conversation object available in sharedViewModel, using it")
                    chatRoom = room
                    chatRoom.addListener(chatRoomListener)
                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                    return@postOnCoreThread
                }
            }

            if (localAddress != null && remoteAddress != null) {
                Log.i("$TAG Searching for conversation in Core using local & peer SIP addresses")
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
                    Log.e("$TAG Failed to find conversation given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun refresh() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Refreshing conversation info (subject, participants, etc...)")
            computeConversationInfo()
        }
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            computeEvents(filter)
        }
    }

    @UiThread
    fun deleteChatMessage(chatMessageModel: MessageModel) {
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
        scrollingPosition = SCROLLING_POSITION_NOT_SET
        computeComposingLabel()

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Conversation with subject [${chatRoom.subject}] is read only!")
        }

        computeConversationInfo()

        computeEvents()
        chatRoom.markAsRead()
    }

    @WorkerThread
    private fun computeConversationInfo() {
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
                index == groupedEventLogs.size - 1,
                { file ->
                    fileToDisplayEvent.postValue(Event(file))
                },
                { conferenceUri ->
                    conferenceToJoinEvent.postValue(Event(conferenceUri))
                },
                { url ->
                    openWebBrowserEvent.postValue(Event(url))
                }
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

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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageReaction
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.ConferenceScheduler
import org.linphone.core.ConferenceSchedulerListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.Participant
import org.linphone.core.ParticipantInfo
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

class ConversationViewModel @UiThread constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"
        private const val MESSAGES_PER_PAGE = 30

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
        const val SCROLLING_POSITION_NOT_SET = -1
    }

    val showBackButton = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val events = MutableLiveData<ArrayList<EventLogModel>>()

    val isMuted = MutableLiveData<Boolean>()

    val isEndToEndEncrypted = MutableLiveData<Boolean>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val isDisabledBecauseNotSecured = MutableLiveData<Boolean>()

    val ephemeralLifetime = MutableLiveData<Long>()

    val composingLabel = MutableLiveData<String>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val isUserScrollingUp = MutableLiveData<Boolean>()

    val noMatchingResultForFilter = MutableLiveData<Boolean>()

    val unreadMessagesCount = MutableLiveData<Int>()

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

    val contactToDisplayEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val showRedToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    val messageDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var eventsList = arrayListOf<EventLogModel>()

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            Log.i("$TAG Conversation state changed [${chatRoom.state}]")
            if (chatRoom.state == ChatRoom.State.Created) {
                computeConversationInfo()
            }
        }

        @WorkerThread
        override fun onChatRoomRead(chatRoom: ChatRoom) {
            unreadMessagesCount.postValue(0)

            // Make sure message models are aware that they were read,
            // required for scroll to bottom or first unread message behaves as expected
            for (eventLog in eventsList.reversed()) {
                if (eventLog.model is MessageModel) {
                    if (!eventLog.model.isRead) {
                        eventLog.model.isRead = true
                    } else {
                        break
                    }
                }
            }
            Log.i("$TAG Conversation was marked as read")
        }

        @WorkerThread
        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Message [$message] is being sent, marking conversation as read")

            // Prevents auto scroll to go to latest received message
            chatRoom.markAsRead()

            addEvents(arrayOf(eventLog))
        }

        @WorkerThread
        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Message [$message] has been sent")
        }

        @WorkerThread
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<EventLog>) {
            Log.i("$TAG Received [${eventLogs.size}] new message(s)")
            computeComposingLabel()

            addEvents(eventLogs)

            unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
        }

        @WorkerThread
        override fun onNewMessageReaction(
            chatRoom: ChatRoom,
            message: ChatMessage,
            reaction: ChatMessageReaction
        ) {
            Log.i(
                "$TAG A reaction [${reaction.body}] was received from [${reaction.fromAddress.asStringUriOnly()}]"
            )
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
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant was added to the conversation")
            addEvents(arrayOf(eventLog))
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant was removed from the conversation or has left")
            addEvents(arrayOf(eventLog))
        }

        @WorkerThread
        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been granted/removed admin rights")
            addEvents(arrayOf(eventLog))
        }

        @WorkerThread
        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Conversation subject changed [${chatRoom.subject}]")
            addEvents(arrayOf(eventLog))
        }

        @WorkerThread
        override fun onSecurityEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A security event was triggered")
            addEvents(arrayOf(eventLog))
        }

        @WorkerThread
        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Adding new ephemeral event [${eventLog.type}]")
            addEvents(arrayOf(eventLog))

            ephemeralLifetime.postValue(
                if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
            )
        }

        @WorkerThread
        override fun onEphemeralMessageDeleted(chatRoom: ChatRoom, eventLog: EventLog) {
            val eventsLogs = eventsList
            val message = eventLog.chatMessage
            Log.i("$TAG Message [${message?.messageId}] ephemeral lifetime has expired")

            val found = eventsLogs.find {
                (it.model as? MessageModel)?.chatMessage == message
            }
            if (found != null) {
                val list = arrayListOf<EventLogModel>()
                list.addAll(eventsLogs)

                // Update previous & next messages if needed
                updatePreviousAndNextMessages(list, found)

                Log.i("$TAG Removing message from conversation events list")
                list.remove(found)
                eventsList = list
                events.postValue(eventsList)
            } else {
                Log.e("$TAG Failed to find matching message in conversation events list")
            }
        }
    }

    private val conferenceSchedulerListener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State
        ) {
            Log.i("$TAG Conference scheduler state is $state")
            if (state == ConferenceScheduler.State.Ready) {
                conferenceScheduler.removeListener(this)

                val conferenceAddress = conferenceScheduler.info?.uri
                if (conferenceAddress != null) {
                    Log.i(
                        "$TAG Conference info created, address is ${conferenceAddress.asStringUriOnly()}"
                    )
                    coreContext.startCall(conferenceAddress)
                } else {
                    Log.e("$TAG Conference info URI is null!")
                    // TODO: notify error to user
                }
            } else if (state == ConferenceScheduler.State.Error) {
                conferenceScheduler.removeListener(this)
                Log.e("$TAG Failed to create group call!")
                // TODO: notify error to user
            }
        }
    }

    init {
        searchBarVisible.value = false
        isUserScrollingUp.value = false
        isDisabledBecauseNotSecured.value = false
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                chatRoom.removeListener(chatRoomListener)
            }
            eventsList.forEach(EventLogModel::destroy)
        }
    }

    override fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
        if (!sameOne) {
            chatRoom.addListener(chatRoomListener)
            configureChatRoom()
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
            val eventsLogs = eventsList
            val found = eventsLogs.find {
                it.model == chatMessageModel
            }
            if (found != null) {
                val list = arrayListOf<EventLogModel>()
                list.addAll(eventsLogs)

                // Update previous & next messages if needed
                updatePreviousAndNextMessages(list, found)

                Log.i("$TAG Removing chat message id [${chatMessageModel.id}] from events list")
                list.remove(found)
                eventsList = list
                events.postValue(eventsList)
            } else {
                Log.e(
                    "$TAG Failed to find chat message id [${chatMessageModel.id}] in events list!"
                )
            }

            Log.i("$TAG Deleting message id [${chatMessageModel.id}] from database")
            chatRoom.deleteMessage(chatMessageModel.chatMessage)
            messageDeletedEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun startCall() {
        coreContext.postOnCoreThread { core ->
            if (LinphoneUtils.isChatRoomAGroup(chatRoom) && chatRoom.participants.size >= 2) {
                createGroupCall()
            } else {
                val firstParticipant = chatRoom.participants.firstOrNull()
                val address = firstParticipant?.address
                if (address != null) {
                    Log.i("$TAG Audio calling SIP address [${address.asStringUriOnly()}]")
                    val params = core.createCallParams(null)
                    params?.isVideoEnabled = false
                    coreContext.startCall(address, params)
                }
            }
        }
    }

    @UiThread
    fun markAsRead() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Marking chat room as read")
            chatRoom.markAsRead()
        }
    }

    @UiThread
    fun mute() {
        coreContext.postOnCoreThread {
            chatRoom.muted = true
            isMuted.postValue(chatRoom.muted)
        }
    }

    @UiThread
    fun unmute() {
        coreContext.postOnCoreThread {
            chatRoom.muted = false
            isMuted.postValue(chatRoom.muted)
        }
    }

    @UiThread
    fun updateCurrentlyDisplayedConversation() {
        coreContext.postOnCoreThread {
            val id = LinphoneUtils.getChatRoomId(chatRoom)
            Log.i("$TAG Asking notifications manager not to notify messages for conversation [$id]")
            coreContext.notificationsManager.setCurrentlyDisplayedChatRoomId(id)
        }
    }

    @UiThread
    fun updateEphemeralLifetime(lifetime: Long) {
        coreContext.postOnCoreThread {
            LinphoneUtils.chatRoomConfigureEphemeralMessagesLifetime(chatRoom, lifetime)
            ephemeralLifetime.postValue(
                if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
            )
        }
    }

    @UiThread
    fun loadMoreData(totalItemsCount: Int) {
        coreContext.postOnCoreThread {
            val maxSize: Int = chatRoom.historyEventsSize
            Log.i("$TAG Loading more data, current total is $totalItemsCount, max size is $maxSize")

            if (totalItemsCount < maxSize) {
                var upperBound: Int = totalItemsCount + MESSAGES_PER_PAGE
                if (upperBound > maxSize) {
                    upperBound = maxSize
                }

                val history = chatRoom.getHistoryRangeEvents(totalItemsCount, upperBound)
                val list = getEventsListFromHistory(history, searchFilter.value.orEmpty())

                val lastEvent = list.lastOrNull()
                val newEvent = eventsList.firstOrNull()
                if (lastEvent != null && newEvent != null && shouldWeGroupTwoEvents(
                        newEvent.eventLog,
                        lastEvent.eventLog
                    )
                ) {
                    if (lastEvent.model is MessageModel) {
                        lastEvent.model.groupedWithNextMessage.postValue(true)
                    }
                    if (newEvent.model is MessageModel) {
                        newEvent.model.groupedWithPreviousMessage.postValue(true)
                    }
                }

                Log.i("$TAG More data loaded, adding it to conversation events list")
                list.addAll(eventsList)
                eventsList = list
                events.postValue(eventsList)
            }
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        scrollingPosition = SCROLLING_POSITION_NOT_SET
        computeComposingLabel()

        isEndToEndEncrypted.postValue(
            chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt())
        )
        isMuted.postValue(chatRoom.muted)

        computeConversationInfo()

        computeEvents()

        Log.i("$TAG Marking chat room as read")
        chatRoom.markAsRead()
    }

    @WorkerThread
    private fun computeConversationInfo() {
        val group = LinphoneUtils.isChatRoomAGroup(chatRoom)
        isGroup.postValue(group)

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Conversation with subject [${chatRoom.subject}] is read only!")
        }

        if (!chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt())) {
            val account = LinphoneUtils.getDefaultAccount()
            if (account?.isInSecureMode() == true) {
                Log.w(
                    "$TAG Conversation with subject [${chatRoom.subject}] has been disabled because it isn't encrypted and default account is in secure mode"
                )
                isDisabledBecauseNotSecured.postValue(true)
            }
        }

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
            fakeFriend.name = chatRoom.subject
            fakeFriend.photo = ImageUtils.generateBitmapForChatRoom(chatRoom)
            val model = ContactAvatarModel(fakeFriend)
            model
        } else {
            coreContext.contactsManager.getContactAvatarModelForAddress(address)
        }
        avatarModel.postValue(avatar)

        ephemeralLifetime.postValue(
            if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
        )
    }

    @WorkerThread
    private fun computeEvents(filter: String = "") {
        eventsList.forEach(EventLogModel::destroy)

        val history = chatRoom.getHistoryEvents(MESSAGES_PER_PAGE)
        val list = getEventsListFromHistory(history, filter)
        Log.i("$TAG Extracted [${list.size}] events from conversation history in database")
        eventsList = list
        events.postValue(eventsList)

        if (filter.isNotEmpty() && eventsList.isEmpty()) {
            noMatchingResultForFilter.postValue(true)
        } else {
            noMatchingResultForFilter.postValue(false)
        }
    }

    @WorkerThread
    private fun addEvents(eventLogs: Array<EventLog>) {
        // TODO FIXME: remove later, for debug purposes
        Log.e("$TAG Adding ${eventLogs.size} events to conversation")
        for (event in eventLogs) {
            if (event.type == EventLog.Type.ConferenceChatMessage) {
                val message = event.chatMessage
                val describe = if (message != null) {
                    LinphoneUtils.getTextDescribingMessage(message)
                } else {
                    "Failed to get message for event log [$event]"
                }
                Log.e("$TAG Adding chat message event: [$describe]")
            } else {
                Log.e("$TAG Adding [${event.type}] event: [$event]")
            }
        }
        // End of TODO FIXME

        val list = arrayListOf<EventLogModel>()
        list.addAll(eventsList)
        val lastEvent = list.lastOrNull()

        val newList = getEventsListFromHistory(
            eventLogs,
            searchFilter.value.orEmpty().trim()
        )
        val newEvent = newList.firstOrNull()
        if (lastEvent != null && newEvent != null && shouldWeGroupTwoEvents(
                newEvent.eventLog,
                lastEvent.eventLog
            )
        ) {
            if (lastEvent.model is MessageModel) {
                lastEvent.model.groupedWithNextMessage.postValue(true)
            }
            if (newEvent.model is MessageModel) {
                newEvent.model.groupedWithPreviousMessage.postValue(true)
            }
        }

        list.addAll(newList)
        eventsList = list
        events.postValue(eventsList)
    }

    @WorkerThread
    private fun processGroupedEvents(
        groupedEventLogs: ArrayList<EventLog>
    ): ArrayList<EventLogModel> {
        val groupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        val eventsList = arrayListOf<EventLogModel>()

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
                index != groupedEventLogs.size - 1,
                { file ->
                    fileToDisplayEvent.postValue(Event(file))
                },
                { conferenceUri ->
                    conferenceToJoinEvent.postValue(Event(conferenceUri))
                },
                { url ->
                    openWebBrowserEvent.postValue(Event(url))
                },
                { friendRefKey ->
                    contactToDisplayEvent.postValue(Event(friendRefKey))
                },
                { redToast ->
                    showRedToastEvent.postValue(Event(redToast))
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

        if (history.size == 1) {
            // TODO FIXME: remove later, for debug purposes
            Log.e("$TAG Adding a single event to conversation")

            // If there is a single event, improve processing speed by skipping grouping tasks
            val event = history[0]
            eventsList.addAll(processGroupedEvents(arrayListOf(event)))
        } else {
            // TODO FIXME: remove later, for debug purposes
            Log.e("$TAG Processing list of events (${history.size}) to add to conversation")

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

                // Handle all events in group, then re-start a new group with current item
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
    private fun updatePreviousAndNextMessages(list: ArrayList<EventLogModel>, found: EventLogModel) {
        val index = list.indexOf(found)
        if (found.model is MessageModel) {
            val messageModel = found.model
            if (messageModel.groupedWithNextMessage.value == true && messageModel.groupedWithPreviousMessage.value == true) {
                Log.i(
                    "$TAG Deleted message was grouped with both next and previous one; nothing to do"
                )
                // Nothing to do
            } else if (messageModel.groupedWithPreviousMessage.value == true) {
                Log.i("$TAG Deleted message was grouped with previous one")
                if (index > 0) {
                    val previous = list[index - 1]
                    if (previous.model is MessageModel) {
                        previous.model.groupedWithNextMessage.postValue(false)
                        Log.i("$TAG Previous message at [${index - 1}] was updated")
                    }
                }
            } else if (messageModel.groupedWithNextMessage.value == true) {
                Log.i("$TAG Deleted message was grouped with next one")
                if (index < list.size - 1) {
                    val next = list[index + 1]
                    if (next.model is MessageModel) {
                        next.model.groupedWithPreviousMessage.postValue(false)
                        Log.i("$TAG Next message at [${index + 1}] was updated")
                    }
                }
            }
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

    @WorkerThread
    private fun createGroupCall() {
        val core = coreContext.core
        val account = core.defaultAccount
        if (account == null) {
            Log.e(
                "$TAG No default account found, can't create group call!"
            )
            return
        }

        val conferenceInfo = Factory.instance().createConferenceInfo()
        conferenceInfo.organizer = account.params.identityAddress
        conferenceInfo.subject = subject.value

        val participants = arrayOfNulls<ParticipantInfo>(chatRoom.participants.size)
        var index = 0
        for (participant in chatRoom.participants) {
            val info = Factory.instance().createParticipantInfo(participant.address)
            // For meetings, all participants must have Speaker role
            info?.role = Participant.Role.Speaker
            participants[index] = info
            index += 1
        }
        conferenceInfo.setParticipantInfos(participants)

        Log.i(
            "$TAG Creating group call with subject ${subject.value} and ${participants.size} participant(s)"
        )
        val conferenceScheduler = core.createConferenceScheduler()
        conferenceScheduler.addListener(conferenceSchedulerListener)
        conferenceScheduler.account = account
        // Will trigger the conference creation/update automatically
        conferenceScheduler.info = conferenceInfo
    }
}

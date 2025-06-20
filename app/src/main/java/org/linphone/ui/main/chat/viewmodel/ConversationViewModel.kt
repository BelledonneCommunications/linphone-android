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

import android.net.Uri
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.ContactsManager
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageReaction
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoom.HistoryFilter
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Friend
import org.linphone.core.SearchDirection
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import androidx.core.net.toUri

class ConversationViewModel
    @UiThread
    constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"
        private const val MESSAGES_PER_PAGE = 30

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
        const val ITEMS_TO_LOAD_BEFORE_SEARCH_RESULT = 6
    }

    val showBackButton = MutableLiveData<Boolean>()

    val isCallConversation = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val isEmpty = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val isEndToEndEncrypted = MutableLiveData<Boolean>()

    val isEndToEndEncryptionAvailable = MutableLiveData<Boolean>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val isDisabledBecauseNotSecured = MutableLiveData<Boolean>()

    val ephemeralLifetime = MutableLiveData<Long>()

    val ephemeralLifeTimeLabel = MutableLiveData<String>()

    val composingLabel = MutableLiveData<String>()

    val composingIcon = MutableLiveData<Int>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val searchInProgress = MutableLiveData<Boolean>()

    val canSearchDown = MutableLiveData<Boolean>()

    val itemToScrollTo = MutableLiveData<Int>()

    val isUserScrollingUp = MutableLiveData<Boolean>()

    val unreadMessagesCount = MutableLiveData<Int>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val fileToDisplayEvent: MutableLiveData<Event<FileModel>> by lazy {
        MutableLiveData<Event<FileModel>>()
    }

    val sipUriToCallEvent: MutableLiveData<Event<String>> by lazy {
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

    val messageDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val updateEvents: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val forwardMessageEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    val voiceRecordPlaybackEndedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    var eventsList = arrayListOf<EventLogModel>()

    var pendingForwardMessage: MessageModel? = null

    private var latestMatch: EventLog? = null

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onConferenceJoined(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Conversation was joined")
            if (LinphoneUtils.isChatRoomAGroup(chatRoom)) {
                addEvents(arrayOf(eventLog))
            }
            computeConversationInfo()

            val messageToForward = pendingForwardMessage
            if (messageToForward != null) {
                Log.i("$TAG Found pending forward message, doing it now")
                forwardMessageEvent.postValue(Event(messageToForward))
                pendingForwardMessage = null
            }
        }

        @WorkerThread
        override fun onConferenceLeft(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.w("$TAG Conversation was left")
            if (LinphoneUtils.isChatRoomAGroup(chatRoom)) {
                addEvents(arrayOf(eventLog))
            }
            isReadOnly.postValue(chatRoom.isReadOnly)
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
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<EventLog>) {
            Log.i("$TAG Received [${eventLogs.size}] new message(s)")
            computeComposingLabel()

            unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
            addEvents(eventLogs)
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
            ephemeralLifeTimeLabel.postValue(
                LinphoneUtils.formatEphemeralExpiration(chatRoom.ephemeralLifetime)
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
                updateEvents.postValue(Event(true))
                isEmpty.postValue(eventsList.isEmpty())
            } else {
                Log.e("$TAG Failed to find matching message in conversation events list")
            }
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            computeParticipantsInfo()

            if (isGroup.value == true) {
                for (event in eventsList) {
                    (event.model as? MessageModel)?.updateAvatarModel()
                }
            }
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        coreContext.postOnCoreThread { core ->
            isEndToEndEncryptionAvailable.postValue(LinphoneUtils.isEndToEndEncryptedChatAvailable(core))
            coreContext.contactsManager.addListener(contactsListener)
        }

        searchBarVisible.value = false
        isUserScrollingUp.value = false
        isDisabledBecauseNotSecured.value = false
        searchInProgress.value = false
        canSearchDown.value = false
        itemToScrollTo.value = -1
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            coreContext.contactsManager.removeListener(contactsListener)
            if (isChatRoomInitialized()) {
                chatRoom.removeListener(chatRoomListener)
            }
            eventsList.forEach(EventLogModel::destroy)
        }
    }

    @WorkerThread
    override fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
        if (!sameOne) {
            Log.i("$TAG Conversation found and not the same as before, configuring it...")
            chatRoom.addListener(chatRoomListener)
            configureChatRoom()
        } else {
            // This is required to have events displayed when fragment is recreated
            // due to a rotation or theme switching between light & dark for example
            updateEvents.postValue(Event(true))
        }
    }

    @UiThread
    fun openSearchBar() {
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    @UiThread
    fun closeSearchBar() {
        searchFilter.value = ""
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
        latestMatch = null
        canSearchDown.value = false

        coreContext.postOnCoreThread {
            for (eventLog in eventsList) {
                if ((eventLog.model as? MessageModel)?.isTextHighlighted == true) {
                    eventLog.model.highlightText("")
                }
            }
        }
    }

    @UiThread
    fun searchUp() {
        coreContext.postOnCoreThread {
            searchChatMessage(SearchDirection.Up)
        }
    }

    @UiThread
    fun searchDown() {
        coreContext.postOnCoreThread {
            searchChatMessage(SearchDirection.Down)
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
    fun updateUnreadMessageCount() {
        if (!isChatRoomInitialized()) return
        coreContext.postOnCoreThread {
            unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
        }
    }

    @UiThread
    fun applyFilter() {
        coreContext.postOnCoreThread {
            computeEvents()
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
                updateEvents.postValue(Event(true))
                isEmpty.postValue(eventsList.isEmpty())
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
    fun markAsRead() {
        if (!isChatRoomInitialized()) return
        coreContext.postOnCoreThread {
            if (chatRoom.unreadMessagesCount == 0) return@postOnCoreThread
            Log.i("$TAG Marking chat room as read")
            chatRoom.markAsRead()
        }
    }

    @UiThread
    fun mute() {
        if (!isChatRoomInitialized()) return
        coreContext.postOnCoreThread {
            chatRoom.muted = true
            isMuted.postValue(chatRoom.muted)
        }
    }

    @UiThread
    fun unMute() {
        if (!isChatRoomInitialized()) return
        coreContext.postOnCoreThread {
            chatRoom.muted = false
            isMuted.postValue(chatRoom.muted)
        }
    }

    @UiThread
    fun updateCurrentlyDisplayedConversation() {
        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                val id = LinphoneUtils.getConversationId(chatRoom)
                Log.i(
                    "$TAG Asking notifications manager not to notify messages for conversation [$id]"
                )
                coreContext.notificationsManager.setCurrentlyDisplayedChatRoomId(id)

                checkIfConversationShouldBeDisabledForSecurityReasons()
            }
        }
    }

    @UiThread
    fun updateEphemeralLifetime(lifetime: Long) {
        if (!isChatRoomInitialized()) return
        coreContext.postOnCoreThread {
            LinphoneUtils.chatRoomConfigureEphemeralMessagesLifetime(chatRoom, lifetime)
            ephemeralLifetime.postValue(
                if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
            )
            ephemeralLifeTimeLabel.postValue(
                LinphoneUtils.formatEphemeralExpiration(chatRoom.ephemeralLifetime)
            )
        }
    }

    @UiThread
    fun loadMoreData(totalItemsCount: Int) {
        if (!isChatRoomInitialized()) return
        coreContext.postOnCoreThread {
            val maxSize: Int = chatRoom.historyEventsSize
            Log.i("$TAG Loading more data, current total is $totalItemsCount, max size is $maxSize")

            if (totalItemsCount < maxSize) {
                var upperBound: Int = totalItemsCount + MESSAGES_PER_PAGE
                if (upperBound > maxSize) {
                    upperBound = maxSize
                }

                val history = chatRoom.getHistoryRangeEvents(totalItemsCount, upperBound)
                val list = getEventsListFromHistory(history)

                val lastEvent = list.lastOrNull()
                val newEvent = eventsList.firstOrNull()
                if (lastEvent != null && lastEvent.model is MessageModel && newEvent != null && newEvent.model is MessageModel && shouldWeGroupTwoEvents(
                        newEvent.eventLog,
                        lastEvent.eventLog
                    )
                ) {
                    lastEvent.model.groupedWithNextMessage.postValue(true)
                    newEvent.model.groupedWithPreviousMessage.postValue(true)
                }

                Log.i("$TAG More data loaded, adding it to conversation events list")
                list.addAll(eventsList)
                eventsList = list
                updateEvents.postValue(Event(true))
                isEmpty.postValue(eventsList.isEmpty())
            }
        }
    }

    @WorkerThread
    fun checkIfConversationShouldBeDisabledForSecurityReasons() {
        if (!isChatRoomInitialized()) return
        if (!chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt())) {
            if (LinphoneUtils.getAccountForAddress(chatRoom.localAddress)?.params?.instantMessagingEncryptionMandatory == true) {
                Log.w(
                    "$TAG Conversation with subject [${chatRoom.subject}] is considered as read-only because it isn't encrypted and default account is in secure mode"
                )
                isDisabledBecauseNotSecured.postValue(true)
            } else {
                isDisabledBecauseNotSecured.postValue(false)
            }
        } else {
            isDisabledBecauseNotSecured.postValue(false)
        }
    }

    @UiThread
    fun loadDataUpUntilToMessageId(messageId: String?) {
        messageId ?: return

        coreContext.postOnCoreThread {
            searchInProgress.postValue(true)
            val eventLog = chatRoom.findEventLog(messageId)
            if (eventLog != null) {
                Log.i("$TAG Found event log [$eventLog] with message ID [$messageId]")
                loadMessagesUpTo(eventLog)
            } else {
                Log.e(
                    "$TAG Failed to find event log with message ID [$messageId] in chat room history!"
                )
                searchInProgress.postValue(false)
            }
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        if (!isChatRoomInitialized()) return

        computeComposingLabel()

        isEndToEndEncrypted.postValue(
            chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt())
        )
        isMuted.postValue(chatRoom.muted)

        computeConversationInfo()

        unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
        computeEvents()
    }

    @WorkerThread
    private fun computeConversationInfo() {
        if (!isChatRoomInitialized()) return

        val group = LinphoneUtils.isChatRoomAGroup(chatRoom)
        isGroup.postValue(group)

        val empty =
            chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        if (empty) {
            Log.w("$TAG Conversation has conference capability but has no participants!")
        }
        val readOnly = chatRoom.isReadOnly
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Conversation with subject [${chatRoom.subject}] is read only!")
        }

        checkIfConversationShouldBeDisabledForSecurityReasons()

        subject.postValue(chatRoom.subject)

        computeParticipantsInfo()

        ephemeralLifetime.postValue(
            if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
        )
        ephemeralLifeTimeLabel.postValue(
            LinphoneUtils.formatEphemeralExpiration(chatRoom.ephemeralLifetime)
        )
    }

    @WorkerThread
    private fun computeParticipantsInfo() {
        if (!isChatRoomInitialized()) return

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

        val avatar = if (LinphoneUtils.isChatRoomAGroup(chatRoom)) {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = chatRoom.subject
            val model = ContactAvatarModel(fakeFriend)
            model.updateSecurityLevelUsingConversation(chatRoom)
            model
        } else {
            coreContext.contactsManager.getContactAvatarModelForAddress(address)
        }
        avatarModel.postValue(avatar)
    }

    @WorkerThread
    private fun computeEvents() {
        if (!isChatRoomInitialized()) return

        eventsList.forEach(EventLogModel::destroy)

        val history = chatRoom.getHistoryEvents(MESSAGES_PER_PAGE)
        val list = getEventsListFromHistory(history)
        Log.i("$TAG Extracted [${list.size}] events from conversation history in database")
        eventsList = list
        updateEvents.postValue(Event(true))
        isEmpty.postValue(eventsList.isEmpty())
    }

    @WorkerThread
    private fun addEvents(eventLogs: Array<EventLog>) {
        Log.i("$TAG Adding [${eventLogs.size}] events")
        // Need to use a new list, otherwise ConversationFragment's dataObserver isn't triggered...
        val list = arrayListOf<EventLogModel>()
        list.addAll(eventsList)
        val lastEvent = list.lastOrNull()

        // Prevents message duplicates
        val eventsToAdd = arrayListOf<EventLog>()
        for (event in eventLogs) {
            if (event.chatMessage != null && event.chatMessage?.messageId.orEmpty().isNotEmpty()) {
                val found = list.find {
                    it.model is MessageModel && it.model.chatMessage.messageId == event.chatMessage?.messageId
                }
                if (found == null) {
                    eventsToAdd.add(event)
                } else {
                    Log.w(
                        "$TAG Received message with ID [${event.chatMessage?.messageId}] is already displayed, do not add it again"
                    )
                }
            } else {
                eventsToAdd.add(event)
            }
        }

        val newList = getEventsListFromHistory(
            eventsToAdd.toTypedArray()
        )
        val newEvent = newList.firstOrNull()

        if (lastEvent != null && lastEvent.model is MessageModel && newEvent != null && newEvent.model is MessageModel && shouldWeGroupTwoEvents(
                newEvent.eventLog,
                lastEvent.eventLog
            )
        ) {
            lastEvent.model.groupedWithNextMessage.postValue(true)
            newEvent.model.groupedWithPreviousMessage.postValue(true)
        }

        list.addAll(newList)
        eventsList = list
        updateEvents.postValue(Event(true))
        isEmpty.postValue(eventsList.isEmpty())
    }

    @WorkerThread
    private fun prependEvents(eventLogs: Array<EventLog>) {
        Log.i("$TAG Prepending [${eventLogs.size}] events")
        // Need to use a new list, otherwise ConversationFragment's dataObserver isn't triggered...
        val list = arrayListOf<EventLogModel>()
        val firstEvent = eventsList.firstOrNull()

        // Prevents message duplicates
        val eventsToAdd = arrayListOf<EventLog>()
        eventsToAdd.addAll(eventLogs)

        val newList = getEventsListFromHistory(
            eventsToAdd.toTypedArray()
        )
        val lastEvent = newList.lastOrNull()

        if (lastEvent != null && lastEvent.model is MessageModel && firstEvent != null && firstEvent.model is MessageModel && shouldWeGroupTwoEvents(
                firstEvent.eventLog,
                lastEvent.eventLog
            )
        ) {
            lastEvent.model.groupedWithNextMessage.postValue(true)
            firstEvent.model.groupedWithPreviousMessage.postValue(true)
        }

        list.addAll(newList)
        list.addAll(eventsList)
        eventsList = list
        updateEvents.postValue(Event(true))
        isEmpty.postValue(eventsList.isEmpty())
    }

    @WorkerThread
    private fun processGroupedEvents(
        groupedEventLogs: ArrayList<EventLog>
    ): ArrayList<EventLogModel> {
        val groupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        val eventsList = arrayListOf<EventLogModel>()

        var index = 0
        for (groupedEvent in groupedEventLogs) {
            val model = EventLogModel(
                groupedEvent,
                groupChatRoom,
                index > 0,
                index != groupedEventLogs.size - 1,
                searchFilter.value.orEmpty(),
                { fileModel -> // onContentClicked
                    fileToDisplayEvent.postValue(Event(fileModel))
                },
                { sipUri -> // onSipUriClicked
                    sipUriToCallEvent.postValue(Event(sipUri))
                },
                { conferenceUri -> // onJoinConferenceClicked
                    conferenceToJoinEvent.postValue(Event(conferenceUri))
                },
                { url -> // onWebUrlClicked
                    openWebBrowserEvent.postValue(Event(url))
                },
                { friendRefKey -> // onContactClicked
                    contactToDisplayEvent.postValue(Event(friendRefKey))
                },
                { redToast -> // onRedToastToShow
                    showRedToastEvent.postValue(Event(redToast))
                },
                { id -> // onVoiceRecordingPlaybackEnded
                    voiceRecordPlaybackEndedEvent.postValue(Event(id))
                },
                { filePath -> // onFileToExportToNativeGallery
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            Log.i("$TAG Export file [$filePath] to Android's MediaStore")
                            val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                            if (mediaStorePath.isNotEmpty()) {
                                Log.i("$TAG File [$filePath] has been successfully exported to MediaStore")
                            } else {
                                Log.e("$TAG Failed to export file [$filePath] to MediaStore!")
                            }
                        }
                    }
                }
            )
            eventsList.add(model)

            index += 1
        }

        return eventsList
    }

    @WorkerThread
    private fun getEventsListFromHistory(
        history: Array<EventLog>
    ): ArrayList<EventLogModel> {
        val eventsList = arrayListOf<EventLogModel>()
        val groupedEventLogs = arrayListOf<EventLog>()

        if (history.size == 1) {
            // If there is a single event, improve processing speed by skipping grouping tasks
            val event = history[0]
            eventsList.addAll(processGroupedEvents(arrayListOf(event)))
        } else {
            for (event in history) {
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
    private fun updatePreviousAndNextMessages(
        list: ArrayList<EventLogModel>,
        found: EventLogModel
    ) {
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
        if (!isChatRoomInitialized()) return

        val pair = LinphoneUtils.getComposingIconAndText(chatRoom)
        val icon = pair.first
        composingIcon.postValue(icon)
        val label = pair.second
        composingLabel.postValue(label)
    }

    @WorkerThread
    private fun loadMessagesUpTo(targetEvent: EventLog) {
        val mask = HistoryFilter.ChatMessage.toInt() or HistoryFilter.InfoNoDevice.toInt()
        val historyToAdd = chatRoom.getHistoryRangeBetween(
            targetEvent,
            eventsList[0].eventLog,
            mask
        )
        Log.i(
            "$TAG Loaded [${historyToAdd.size}] items from history to go to event log [$targetEvent]"
        )

        Log.i("$TAG Loading [$ITEMS_TO_LOAD_BEFORE_SEARCH_RESULT] items before the target")
        val previousMessages = chatRoom.getHistoryRangeNear(
            ITEMS_TO_LOAD_BEFORE_SEARCH_RESULT,
            0,
            targetEvent,
            mask
        )

        itemToScrollTo.postValue(previousMessages.size - 2) // To go to the item before the target event
        val toAdd = previousMessages.plus(historyToAdd)
        prependEvents(toAdd)
    }

    @WorkerThread
    private fun searchChatMessage(direction: SearchDirection) {
        if (!isChatRoomInitialized()) return
        searchInProgress.postValue(true)

        val textToSearch = searchFilter.value.orEmpty().trim()
        val match = chatRoom.searchChatMessageByText(textToSearch, latestMatch, direction)
        if (match == null) {
            Log.i(
                "$TAG No match found while looking up for message with text [$textToSearch] in direction [$direction] starting from message [${latestMatch?.chatMessage?.messageId}]"
            )
            searchInProgress.postValue(false)
            val message = if (latestMatch == null) {
                R.string.conversation_search_no_match_found
            } else {
                R.string.conversation_search_no_more_match
            }
            showRedToast(message, R.drawable.magnifying_glass)
        } else {
            Log.i(
                "$TAG Found result [${match.chatMessage?.messageId}] while looking up for message with text [$textToSearch] in direction [$direction] starting from message [${latestMatch?.chatMessage?.messageId}]"
            )
            latestMatch = match

            val found = eventsList.find {
                it.eventLog == match
            }
            if (found == null) {
                Log.i("$TAG Found result isn't in currently loaded history, loading missing events")
                loadMessagesUpTo(match)
            } else {
                Log.i("$TAG Found result is already in history, no need to load more history")
                (found.model as? MessageModel)?.highlightText(textToSearch)
                val index = eventsList.indexOf(found)
                if (direction == SearchDirection.Down && index < eventsList.size - 1) {
                    // Go to next message to prevent the message we are looking for to be behind the scroll to bottom button
                    itemToScrollTo.postValue(index + 1)
                } else {
                    // Go to previous message so target message won't be displayed stuck to the top
                    itemToScrollTo.postValue(index - 1)
                }
                searchInProgress.postValue(false)
            }

            canSearchDown.postValue(true)
        }
    }

    @UiThread
    fun copyFileToUri(filePath: String, dest: Uri) {
        val source = FileUtils.getProperFilePath(filePath).toUri()
        Log.i("$TAG Copying file URI [$source] to [$dest]")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = FileUtils.copyFile(source, dest)
                if (result) {
                    Log.i(
                        "$TAG File [$filePath] has been successfully exported to documents"
                    )
                    showGreenToast(R.string.file_successfully_exported_to_documents_toast, R.drawable.check)
                } else {
                    Log.e("$TAG Failed to export file [$filePath] to documents!")
                    showRedToast(R.string.export_file_to_documents_error_toast, R.drawable.warning_circle)
                }
            }
        }
    }
}

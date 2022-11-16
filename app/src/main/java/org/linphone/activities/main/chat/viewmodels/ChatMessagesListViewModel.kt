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
package org.linphone.activities.main.chat.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.*
import kotlin.math.max
import org.linphone.activities.main.chat.data.EventLogData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PermissionHelper

class ChatMessagesListViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatMessagesListViewModel(chatRoom) as T
    }
}

class ChatMessagesListViewModel(private val chatRoom: ChatRoom) : ViewModel() {
    companion object {
        private const val MESSAGES_PER_PAGE = 20
    }

    val events = MutableLiveData<ArrayList<EventLogData>>()

    val messageUpdatedEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    val requestWriteExternalStoragePermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val chatRoomListener: ChatRoomListenerStub = object : ChatRoomListenerStub() {
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<out EventLog>) {
            for (eventLog in eventLogs) {
                addChatMessageEventLog(eventLog)
            }
        }

        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            val position = events.value.orEmpty().size

            if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                val chatMessage = eventLog.chatMessage
                chatMessage ?: return
                chatMessage.userData = position
            }

            addEvent(eventLog)
        }

        override fun onSecurityEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            addEvent(eventLog)
        }

        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            addEvent(eventLog)
        }

        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            addEvent(eventLog)
        }

        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            addEvent(eventLog)
        }

        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            addEvent(eventLog)
        }

        override fun onConferenceJoined(chatRoom: ChatRoom, eventLog: EventLog) {
            if (!chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                addEvent(eventLog)
            }
        }

        override fun onConferenceLeft(chatRoom: ChatRoom, eventLog: EventLog) {
            if (!chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
                addEvent(eventLog)
            }
        }

        override fun onEphemeralMessageDeleted(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("[Chat Messages] An ephemeral chat message has expired, removing it from event list")
            deleteEvent(eventLog)
        }

        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            addEvent(eventLog)
        }
    }

    init {
        chatRoom.addListener(chatRoomListener)

        events.value = getEvents()
    }

    override fun onCleared() {
        events.value.orEmpty().forEach(EventLogData::destroy)
        chatRoom.removeListener(chatRoomListener)

        super.onCleared()
    }

    fun resendMessage(chatMessage: ChatMessage) {
        val position: Int = chatMessage.userData as Int
        chatMessage.send()
        messageUpdatedEvent.value = Event(position)
    }

    fun deleteMessage(chatMessage: ChatMessage) {
        LinphoneUtils.deleteFilesAttachedToChatMessage(chatMessage)
        chatRoom.deleteMessage(chatMessage)

        events.value.orEmpty().forEach(EventLogData::destroy)
        events.value = getEvents()
    }

    fun deleteEventLogs(listToDelete: ArrayList<EventLogData>) {
        for (eventLog in listToDelete) {
            LinphoneUtils.deleteFilesAttachedToEventLog(eventLog.eventLog)
            eventLog.eventLog.deleteFromDatabase()
        }

        events.value.orEmpty().forEach(EventLogData::destroy)
        events.value = getEvents()
    }

    fun loadMoreData(totalItemsCount: Int) {
        Log.i("[Chat Messages] Load more data, current total is $totalItemsCount")
        val maxSize: Int = chatRoom.historyEventsSize

        if (totalItemsCount < maxSize) {
            var upperBound: Int = totalItemsCount + MESSAGES_PER_PAGE
            if (upperBound > maxSize) {
                upperBound = maxSize
            }

            val history: Array<EventLog> = chatRoom.getHistoryRangeEvents(totalItemsCount, upperBound)
            val list = arrayListOf<EventLogData>()
            for (eventLog in history) {
                list.add(EventLogData(eventLog))
            }
            list.addAll(events.value.orEmpty())
            events.value = list
        }
    }

    private fun addEvent(eventLog: EventLog) {
        val list = arrayListOf<EventLogData>()
        list.addAll(events.value.orEmpty())
        val found = list.find { data -> data.eventLog == eventLog }
        if (found == null) {
            list.add(EventLogData(eventLog))
        }
        events.value = list
    }

    private fun getEvents(): ArrayList<EventLogData> {
        val list = arrayListOf<EventLogData>()
        val unreadCount = chatRoom.unreadMessagesCount
        var loadCount = max(MESSAGES_PER_PAGE, unreadCount)
        Log.i("[Chat Messages] $unreadCount unread messages in this chat room, loading $loadCount from history")

        val history = chatRoom.getHistoryEvents(loadCount)
        var messageCount = 0
        for (eventLog in history) {
            list.add(EventLogData(eventLog))
            if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                messageCount += 1
            }
        }

        // Load enough events to have at least all unread messages
        while (unreadCount > 0 && messageCount < unreadCount) {
            Log.w("[Chat Messages] There is only $messageCount messages in the last $loadCount events, loading $MESSAGES_PER_PAGE more")
            val moreHistory = chatRoom.getHistoryRangeEvents(loadCount, loadCount + MESSAGES_PER_PAGE)
            loadCount += MESSAGES_PER_PAGE
            for (eventLog in moreHistory) {
                list.add(EventLogData(eventLog))
                if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                    messageCount += 1
                }
            }
        }

        return list
    }

    private fun deleteEvent(eventLog: EventLog) {
        val chatMessage = eventLog.chatMessage
        if (chatMessage != null) {
            LinphoneUtils.deleteFilesAttachedToChatMessage(chatMessage)
            chatRoom.deleteMessage(chatMessage)
        }

        events.value.orEmpty().forEach(EventLogData::destroy)
        events.value = getEvents()
    }

    private fun addChatMessageEventLog(eventLog: EventLog) {
        if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
            val chatMessage = eventLog.chatMessage
            chatMessage ?: return
            chatMessage.userData = events.value.orEmpty().size

            val existingEvent = events.value.orEmpty().find { data ->
                data.eventLog.type == EventLog.Type.ConferenceChatMessage && data.eventLog.chatMessage?.messageId == chatMessage.messageId
            }
            if (existingEvent != null) {
                Log.w("[Chat Messages] Found already present chat message, don't add it it's probably the result of an auto download or an aggregated message received before but notified after the conversation was displayed")
                return
            }

            if (!PermissionHelper.get().hasWriteExternalStoragePermission()) {
                for (content in chatMessage.contents) {
                    if (content.isFileTransfer) {
                        Log.i("[Chat Messages] Android < 10 detected and WRITE_EXTERNAL_STORAGE permission isn't granted yet")
                        requestWriteExternalStoragePermissionEvent.value = Event(true)
                    }
                }
            }
        }

        addEvent(eventLog)
    }
}

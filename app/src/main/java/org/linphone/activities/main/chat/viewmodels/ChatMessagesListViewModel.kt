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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.chat.data.EventLogData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PermissionHelper

class ChatMessagesListViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
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
        override fun onChatMessageReceived(chatRoom: ChatRoom, eventLog: EventLog) {
            // Do not mark as read if DetailChatRoomFragment was opened before app was put in background
            if (coreContext.notificationsManager.currentlyDisplayedChatRoomAddress == chatRoom.peerAddress.asStringUriOnly()) {
                chatRoom.markAsRead()
            }

            if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                val chatMessage = eventLog.chatMessage
                chatMessage ?: return
                chatMessage.userData = events.value.orEmpty().size

                val existingEvent = events.value.orEmpty().find { data ->
                    data.eventLog == eventLog
                }
                if (existingEvent != null) {
                    Log.w("[Chat Messages] Found already present chat message, don't add it it's probably the result of an auto download")
                    return
                }

                if (Version.sdkStrictlyBelow(Version.API29_ANDROID_10) && !PermissionHelper.get().hasWriteExternalStorage()) {
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
        val position: Int = chatMessage.userData as Int
        LinphoneUtils.deleteFilesAttachedToChatMessage(chatMessage)
        chatRoom.deleteMessage(chatMessage)

        val list = arrayListOf<EventLogData>()
        list.addAll(events.value.orEmpty())
        list.removeAt(position)
        events.value = list
    }

    fun deleteEventLogs(listToDelete: ArrayList<EventLogData>) {
        val list = arrayListOf<EventLogData>()
        list.addAll(events.value.orEmpty())

        for (eventLog in listToDelete) {
            LinphoneUtils.deleteFilesAttachedToEventLog(eventLog.eventLog)
            eventLog.eventLog.deleteFromDatabase()
            list.remove(eventLog)
        }

        events.value = list
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
        val history = chatRoom.getHistoryEvents(MESSAGES_PER_PAGE)
        for (eventLog in history) {
            list.add(EventLogData(eventLog))
        }
        return list
    }

    private fun deleteEvent(eventLog: EventLog) {
        val chatMessage = eventLog.chatMessage
        if (chatMessage != null) {
            LinphoneUtils.deleteFilesAttachedToChatMessage(chatMessage)
            chatRoom.deleteMessage(chatMessage)
        }
        events.value = getEvents()
    }
}

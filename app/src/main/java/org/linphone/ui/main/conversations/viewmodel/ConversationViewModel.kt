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
package org.linphone.ui.main.conversations.viewmodel

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactData
import org.linphone.contacts.ContactsListener
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.conversations.data.EventLogData
import org.linphone.utils.LinphoneUtils

class ConversationViewModel @WorkerThread constructor(): ViewModel() {
    private lateinit var chatRoom: ChatRoom

    val events = MutableLiveData<ArrayList<EventLogData>>()

    val contactName = MutableLiveData<String>()

    val contactData = MutableLiveData<ContactData>()

    val subject = MutableLiveData<String>()

    val isComposing = MutableLiveData<Boolean>()

    val isOneToOne = MutableLiveData<Boolean>()

    private val contactsListener = object : ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            contactLookup()
            events.value.orEmpty().forEach(EventLogData::contactLookup)
        }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            composing: Boolean
        ) {
            isComposing.postValue(composing)
        }

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

            addChatMessageEventLog(eventLog)
        }
    }

    init {
        coreContext.contactsManager.addListener(contactsListener)
    }

    override fun onCleared() {
        coreContext.postOnCoreThread {
            coreContext.contactsManager.removeListener(contactsListener)
            if (::chatRoom.isInitialized) {
                chatRoom.removeListener(chatRoomListener)
            }
            events.value.orEmpty().forEach(EventLogData::destroy)
        }
    }

    fun loadChatRoom(localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)

            val found = core.searchChatRoom(
                null,
                localAddress,
                remoteSipAddress,
                arrayOfNulls(
                    0
                )
            )
            if (found != null) {
                chatRoom = found
                chatRoom.addListener(chatRoomListener)

                isOneToOne.postValue(chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt()))
                subject.postValue(chatRoom.subject)
                isComposing.postValue(chatRoom.isRemoteComposing)
                contactLookup()

                val list = arrayListOf<EventLogData>()
                list.addAll(events.value.orEmpty())

                for (eventLog in chatRoom.getHistoryEvents(0)) {
                    list.add(EventLogData(eventLog))
                }

                events.postValue(list)
            }
        }
    }

    private fun contactLookup() {
        if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            val remoteAddress = chatRoom.peerAddress
            val friend = chatRoom.core.findFriend(remoteAddress)
            if (friend != null) {
                contactData.postValue(ContactData(friend))
            }
            contactName.postValue(friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress))
        } else {
            if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
                val first = chatRoom.participants.firstOrNull()
                if (first != null) {
                    val remoteAddress = first.address
                    val friend = chatRoom.core.findFriend(remoteAddress)
                    if (friend != null) {
                        contactData.postValue(ContactData(friend))
                    }
                    contactName.postValue(
                        friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress)
                    )
                } else {
                    Log.e("[Conversation View Model] No participant in the chat room!")
                }
            }
        }
    }

    private fun addEvent(eventLog: EventLog) {
        val list = arrayListOf<EventLogData>()
        list.addAll(events.value.orEmpty())

        val found = list.find { data -> data.eventLog == eventLog }
        if (found == null) {
            list.add(EventLogData(eventLog))
        }

        events.postValue(list)
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
                Log.w(
                    "[Chat Messages] Found already present chat message, don't add it it's probably the result of an auto download or an aggregated message received before but notified after the conversation was displayed"
                )
                return
            }
        }

        addEvent(eventLog)
    }
}

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
import java.util.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactsListener
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.conversations.data.ChatRoomData
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationsListViewModel : ViewModel() {
    val chatRoomsList = MutableLiveData<ArrayList<ChatRoomData>>()

    val notifyItemChangedEvent = MutableLiveData<Event<Int>>()

    private val contactsListener = object : ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            for (chatRoomData in chatRoomsList.value.orEmpty()) {
                chatRoomData.contactLookup()
            }
        }

        @WorkerThread
        override fun onLocalContactsUpdated() { }
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onChatRoomStateChanged(
            core: Core,
            chatRoom: ChatRoom,
            state: ChatRoom.State?
        ) {
            Log.i(
                "[Conversations List] Chat room [${LinphoneUtils.getChatRoomId(chatRoom)}] state changed [$state]"
            )
            when (state) {
                ChatRoom.State.Created -> {
                    addChatRoomToList(chatRoom)
                }
                ChatRoom.State.Deleted -> {
                    removeChatRoomFromList(chatRoom)
                }
                else -> {}
            }
        }

        override fun onMessageSent(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            onChatRoomMessageEvent(chatRoom)
        }

        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            onChatRoomMessageEvent(chatRoom)
        }

        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            notifyChatRoomUpdate(chatRoom)
        }

        override fun onChatRoomEphemeralMessageDeleted(core: Core, chatRoom: ChatRoom) {
            notifyChatRoomUpdate(chatRoom)
        }

        override fun onChatRoomSubjectChanged(core: Core, chatRoom: ChatRoom) {
            notifyChatRoomUpdate(chatRoom)
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            coreContext.contactsManager.addListener(contactsListener)
        }

        coreContext.postOnCoreThread { core ->
            updateChatRoomsList()
        }
    }

    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.removeListener(contactsListener)
            core.removeListener(coreListener)
        }
        super.onCleared()
    }

    private fun addChatRoomToList(chatRoom: ChatRoom) {
        val index = findChatRoomIndex(chatRoom)
        if (index != -1) {
            Log.w("[Conversations List] Chat room already exists in list, do not add it again")
            return
        }

        val list = arrayListOf<ChatRoomData>()

        val data = ChatRoomData(chatRoom)
        list.add(data)
        list.addAll(chatRoomsList.value.orEmpty())
        list.sortByDescending { data -> data.chatRoom.lastUpdateTime }

        chatRoomsList.postValue(list)
    }

    private fun removeChatRoomFromList(chatRoom: ChatRoom) {
        val list = arrayListOf<ChatRoomData>()

        for (data in chatRoomsList.value.orEmpty()) {
            if (LinphoneUtils.getChatRoomId(chatRoom) != LinphoneUtils.getChatRoomId(
                    data.chatRoom
                )
            ) {
                list.add(data)
            }
        }

        chatRoomsList.postValue(list)
    }

    private fun findChatRoomIndex(chatRoom: ChatRoom): Int {
        val id = LinphoneUtils.getChatRoomId(chatRoom)
        for ((index, data) in chatRoomsList.value.orEmpty().withIndex()) {
            if (id == data.id) {
                return index
            }
        }
        return -1
    }

    private fun notifyChatRoomUpdate(chatRoom: ChatRoom) {
        when (val index = findChatRoomIndex(chatRoom)) {
            -1 -> updateChatRoomsList()
            else -> notifyItemChangedEvent.postValue(Event(index))
        }
    }

    private fun onChatRoomMessageEvent(chatRoom: ChatRoom) {
        when (findChatRoomIndex(chatRoom)) {
            -1 -> updateChatRoomsList()
            0 -> notifyItemChangedEvent.postValue(Event(0))
            else -> reorderChatRoomsList()
        }
    }

    private fun updateChatRoomsList() {
        Log.i("[Conversations List] Updating chat rooms list")
        chatRoomsList.value.orEmpty().forEach(ChatRoomData::onCleared)

        val list = arrayListOf<ChatRoomData>()
        val chatRooms = coreContext.core.chatRooms
        for (chatRoom in chatRooms) {
            list.add(ChatRoomData(chatRoom))
        }
        chatRoomsList.postValue(list)
    }

    private fun reorderChatRoomsList() {
        Log.i("[Conversations List] Re-ordering chat rooms list")
        val list = arrayListOf<ChatRoomData>()
        list.addAll(chatRoomsList.value.orEmpty())
        list.sortByDescending { data -> data.chatRoom.lastUpdateTime }
        chatRoomsList.postValue(list)
    }
}

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
import org.linphone.contacts.ContactsManager
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ConversationModel
import org.linphone.ui.main.viewmodel.AbstractMainViewModel
import org.linphone.utils.LinphoneUtils

class ConversationsListViewModel
    @UiThread
    constructor() : AbstractMainViewModel() {
    companion object {
        private const val TAG = "[Conversations List ViewModel]"
    }

    val conversations = MutableLiveData<ArrayList<ConversationModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onChatRoomStateChanged(
            core: Core,
            chatRoom: ChatRoom,
            state: ChatRoom.State?
        ) {
            Log.i(
                "$TAG Conversation [${LinphoneUtils.getChatRoomId(chatRoom)}] state changed [$state]"
            )

            when (state) {
                ChatRoom.State.Created -> addChatRoom(chatRoom)
                ChatRoom.State.Deleted -> removeChatRoom(chatRoom)
                else -> {}
            }
        }

        @WorkerThread
        override fun onMessageSent(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            reorderChatRooms()
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            val id = LinphoneUtils.getChatRoomId(chatRoom)
            val found = conversations.value.orEmpty().find {
                it.id == id
            }
            if (found == null) {
                Log.i("$TAG Message(s) received for a conversation not yet in the list (probably was empty), adding it")
                addChatRoom(chatRoom)
            } else {
                Log.i("$TAG Message(s) received for an existing conversation, re-order them")
                reorderChatRooms()
            }
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            for (model in conversations.value.orEmpty()) {
                model.computeParticipants()
                model.updateLastMessage()
            }
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        fetchInProgress.value = true

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.addListener(contactsListener)
            core.addListener(coreListener)

            computeChatRoomsList(currentFilter)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            conversations.value.orEmpty().forEach(ConversationModel::destroy)
            coreContext.contactsManager.removeListener(contactsListener)
            core.removeListener(coreListener)
        }
    }

    @UiThread
    override fun filter() {
        coreContext.postOnCoreThread {
            computeChatRoomsList(currentFilter)
        }
    }

    @WorkerThread
    private fun computeChatRoomsList(filter: String) {
        conversations.value.orEmpty().forEach(ConversationModel::destroy)

        if (conversations.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

        val list = arrayListOf<ConversationModel>()
        var count = 0

        val account = LinphoneUtils.getDefaultAccount()
        val chatRooms = if (filter.isEmpty()) {
            account?.chatRooms
        } else {
            account?.filterChatRooms(filter)
        }
        for (chatRoom in chatRooms.orEmpty()) {
            val model = ConversationModel(chatRoom)
            list.add(model)
            count += 1

            if (count == 15) {
                conversations.postValue(list)
            }
        }

        conversations.postValue(list)
    }

    @WorkerThread
    private fun addChatRoom(chatRoom: ChatRoom) {
        val localAddress = chatRoom.localAddress
        val peerAddress = chatRoom.peerAddress

        val defaultAccount = LinphoneUtils.getDefaultAccount()
        if (defaultAccount == null ||
            defaultAccount.params.identityAddress?.weakEqual(localAddress) == false
        )
        {
            Log.w(
                "$TAG Chat room with local address [${localAddress.asStringUriOnly()}] and peer address [${peerAddress.asStringUriOnly()}] was created but not displaying it because it doesn't belong to currently default account"
            )
            return
        }

        val hideEmptyChatRooms = coreContext.core.config.getBool("misc", "hide_empty_chat_rooms", true)
        // Hide empty chat rooms only applies to 1-1 conversations
        if (hideEmptyChatRooms && !LinphoneUtils.isChatRoomAGroup(chatRoom) && chatRoom.lastMessageInHistory == null) {
            Log.w("$TAG Chat room with local address [${localAddress.asStringUriOnly()}] and peer address [${peerAddress.asStringUriOnly()}] is empty, not adding it to match Core setting")
            return
        }

        val currentList = conversations.value.orEmpty()
        val found = currentList.find {
            it.chatRoom.peerAddress.weakEqual(peerAddress)
        }
        if (found != null) {
            Log.w("$TAG Created chat room with local address [${localAddress.asStringUriOnly()}] and peer address [${peerAddress.asStringUriOnly()}] is already in the list, skipping")
            return
        }

        if (currentFilter.isNotEmpty()) {
            val filteredRooms = defaultAccount.filterChatRooms(currentFilter)
            val found = filteredRooms.find {
                it == chatRoom
            }
            if (found == null) return
        }

        val newList = arrayListOf<ConversationModel>()
        val model = ConversationModel(chatRoom)
        newList.add(model)
        newList.addAll(currentList)
        Log.i("$TAG Adding chat room with local address [${localAddress.asStringUriOnly()}] and peer address [${peerAddress.asStringUriOnly()}] to list")
        conversations.postValue(newList)
    }

    @WorkerThread
    private fun removeChatRoom(chatRoom: ChatRoom) {
        val currentList = conversations.value.orEmpty()
        val peerAddress = chatRoom.peerAddress
        val found = currentList.find {
            it.chatRoom.peerAddress.weakEqual(peerAddress)
        }
        if (found != null) {
            val newList = arrayListOf<ConversationModel>()
            newList.addAll(currentList)
            newList.remove(found)
            found.destroy()
            Log.i("$TAG Removing chat room [${peerAddress.asStringUriOnly()}] from list")
            conversations.postValue(newList)
        } else {
            Log.w(
                "$TAG Failed to find item in list matching deleted chat room peer address [${peerAddress.asStringUriOnly()}]"
            )
        }

        showGreenToast(R.string.conversation_deleted_toast, R.drawable.chat_teardrop_text)
    }

    @WorkerThread
    private fun reorderChatRooms() {
        Log.i("$TAG Re-ordering conversations")
        val sortedList = arrayListOf<ConversationModel>()
        sortedList.addAll(conversations.value.orEmpty())
        sortedList.sortByDescending {
            it.chatRoom.lastUpdateTime
        }
        conversations.postValue(sortedList)
    }
}

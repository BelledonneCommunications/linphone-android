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
import org.linphone.contacts.ContactsManager
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoom.Capabilities
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ConversationModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel
import org.linphone.utils.LinphoneUtils

class ConversationsListViewModel @UiThread constructor() : AbstractTopBarViewModel() {
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
                "$TAG Chat room [${LinphoneUtils.getChatRoomId(chatRoom)}] state changed [$state]"
            )

            when (state) {
                ChatRoom.State.Created, ChatRoom.State.Instantiated, ChatRoom.State.Deleted -> {
                    computeChatRoomsList(currentFilter)
                    // TODO: display toast
                }
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
            reorderChatRooms()
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            computeChatRoomsList(currentFilter)
        }
    }

    init {
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
        val chatRooms = account?.chatRooms ?: coreContext.core.chatRooms
        for (chatRoom in chatRooms) {
            // TODO: remove when SDK will do it automatically
            if (account?.isInSecureMode() == true) {
                if (!chatRoom.hasCapability(Capabilities.Encrypted.toInt())) {
                    Log.w(
                        "$TAG Skipping chat room [${LinphoneUtils.getChatRoomId(chatRoom)}] as it is not E2E encrypted and default account requires it"
                    )
                    continue
                }
            }

            val participants = chatRoom.participants
            val found = participants.find {
                it.address.asStringUriOnly().contains(filter, ignoreCase = true)
            }
            if (
                found != null ||
                chatRoom.peerAddress.asStringUriOnly().contains(filter, ignoreCase = true) ||
                chatRoom.subject.orEmpty().contains(filter, ignoreCase = true)
            ) {
                val model = ConversationModel(chatRoom)
                list.add(model)
                count += 1
            }

            if (count == 20) {
                conversations.postValue(list)
                fetchInProgress.postValue(false)
            }
        }

        conversations.postValue(list)
        fetchInProgress.postValue(false)
    }

    @WorkerThread
    private fun reorderChatRooms() {
        Log.i("$TAG Re-ordering chat rooms")
        val sortedList = arrayListOf<ConversationModel>()
        sortedList.addAll(conversations.value.orEmpty())
        sortedList.sortByDescending {
            it.chatRoom.lastUpdateTime
        }
        conversations.postValue(sortedList)
    }
}

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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.viewmodels.ErrorReportingViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ChatRoomsListViewModel : ErrorReportingViewModel() {
    val chatRooms = MutableLiveData<ArrayList<ChatRoomViewModel>>()

    val contactsUpdatedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val fileSharingPending = MutableLiveData<Boolean>()

    val textSharingPending = MutableLiveData<Boolean>()

    val forwardPending = MutableLiveData<Boolean>()

    val groupChatAvailable: Boolean = LinphoneUtils.isGroupChatAvailable()

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Chat Rooms] Contacts have changed")
            contactsUpdatedEvent.value = Event(true)
        }
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onChatRoomStateChanged(core: Core, chatRoom: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                if (chatRoom in core.chatRooms) { // Don't add empty chat room if 1-1 depending on policy
                    addChatRoom(chatRoom)
                }
            } else if (state == ChatRoom.State.TerminationFailed) {
                Log.e("[Chat Rooms] Group chat room removal for address ${chatRoom.peerAddress.asStringUriOnly()} has failed !")
                onErrorEvent.value = Event(R.string.chat_room_removal_failed_snack)
            }
        }

        override fun onMessageSent(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            when (findChatRoomIndex(chatRoom)) {
                -1 -> addChatRoom(chatRoom)
                0 -> {}
                else -> reorderChatRooms()
            }
        }

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            when (findChatRoomIndex(chatRoom)) {
                -1 -> addChatRoom(chatRoom)
                0 -> {}
                else -> reorderChatRooms()
            }
        }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State) {
            if (newState == ChatRoom.State.Deleted) {
                val list = arrayListOf<ChatRoomViewModel>()
                for (chatRoomViewModel in chatRooms.value.orEmpty()) {
                    if (chatRoomViewModel.chatRoom != chatRoom) {
                        list.add(chatRoomViewModel)
                    } else {
                        chatRoomViewModel.destroy()
                    }
                }
                chatRooms.value = list
            }
        }
    }

    private var chatRoomsToDeleteCount = 0

    init {
        updateChatRooms()
        coreContext.core.addListener(listener)
        coreContext.contactsManager.addListener(contactsUpdatedListener)
    }

    override fun onCleared() {
        chatRooms.value.orEmpty().forEach(ChatRoomViewModel::destroy)
        coreContext.contactsManager.removeListener(contactsUpdatedListener)
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun deleteChatRoom(chatRoom: ChatRoom?) {
        for (eventLog in chatRoom?.getHistoryMessageEvents(0).orEmpty()) {
            LinphoneUtils.deleteFilesAttachedToEventLog(eventLog)
        }

        chatRoomsToDeleteCount = 1
        if (chatRoom != null) {
            coreContext.notificationsManager.dismissChatNotification(chatRoom)
            Compatibility.removeChatRoomShortcut(coreContext.context, chatRoom)
            chatRoom.addListener(chatRoomListener)
            coreContext.core.deleteChatRoom(chatRoom)
        }
    }

    fun deleteChatRooms(chatRooms: ArrayList<ChatRoom>) {
        chatRoomsToDeleteCount = chatRooms.size
        for (chatRoom in chatRooms) {
            for (eventLog in chatRoom.getHistoryMessageEvents(0)) {
                LinphoneUtils.deleteFilesAttachedToEventLog(eventLog)
            }

            coreContext.notificationsManager.dismissChatNotification(chatRoom)
            Compatibility.removeChatRoomShortcut(coreContext.context, chatRoom)
            chatRoom.addListener(chatRoomListener)
            chatRoom.core.deleteChatRoom(chatRoom)
        }
    }

    private fun updateChatRooms() {
        chatRooms.value.orEmpty().forEach(ChatRoomViewModel::destroy)

        val list = arrayListOf<ChatRoomViewModel>()
        for (chatRoom in coreContext.core.chatRooms) {
            val viewModel = ChatRoomViewModel(chatRoom)
            list.add(viewModel)
        }
        chatRooms.value = list
    }

    private fun addChatRoom(chatRoom: ChatRoom) {
        val list = arrayListOf<ChatRoomViewModel>()
        val viewModel = ChatRoomViewModel(chatRoom)
        list.add(viewModel)
        list.addAll(chatRooms.value.orEmpty())
        chatRooms.value = list
    }

    private fun reorderChatRooms() {
        val list = arrayListOf<ChatRoomViewModel>()
        list.addAll(chatRooms.value.orEmpty())
        list.sortByDescending { chatRoomViewModel -> chatRoomViewModel.chatRoom.lastUpdateTime }
        chatRooms.value = list
    }

    private fun findChatRoomIndex(chatRoom: ChatRoom): Int {
        for ((index, chatRoomViewModel) in chatRooms.value.orEmpty().withIndex()) {
            if (chatRoomViewModel.chatRoom == chatRoom) {
                return index
            }
        }
        return -1
    }
}

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
package org.linphone.ui.conversations

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.utils.Event

class ConversationsListViewModel : ViewModel() {
    val chatRoomsList = MutableLiveData<ArrayList<ChatRoomData>>()

    val notifyItemChangedEvent = MutableLiveData<Event<Int>>()

    private val coreListener = object : CoreListenerStub() {
        override fun onChatRoomStateChanged(
            core: Core,
            chatRoom: ChatRoom,
            state: ChatRoom.State?
        ) {
            if (state == ChatRoom.State.Created || state == ChatRoom.State.Instantiated || state == ChatRoom.State.Deleted) {
                updateChatRoomsList()
            }
        }

        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            updateChatRoomsList()
        }

        override fun onMessagesReceived(
            core: Core,
            room: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            reorderChatRoomsList()
        }

        override fun onMessageSent(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            reorderChatRoomsList()
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
        }
        updateChatRoomsList()
    }

    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
        super.onCleared()
    }

    private fun updateChatRoomsList() {
        coreContext.postOnCoreThread { core ->
            chatRoomsList.value.orEmpty().forEach(ChatRoomData::onCleared)

            val list = arrayListOf<ChatRoomData>()
            val chatRooms = core.chatRooms
            for (chatRoom in chatRooms) {
                list.add(ChatRoomData(chatRoom))
            }
            chatRoomsList.postValue(list)
        }
    }

    private fun reorderChatRoomsList() {
        coreContext.postOnCoreThread { core ->
            val list = arrayListOf<ChatRoomData>()
            list.addAll(chatRoomsList.value.orEmpty())
            list.sortByDescending { data -> data.chatRoom.lastUpdateTime }
            chatRoomsList.postValue(list)
        }
    }
}

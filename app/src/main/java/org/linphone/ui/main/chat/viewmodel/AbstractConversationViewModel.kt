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
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event

abstract class AbstractConversationViewModel : GenericViewModel() {
    companion object {
        private const val TAG = "[Abstract Conversation ViewModel]"
    }

    val chatRoomFoundEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    lateinit var chatRoom: ChatRoom

    lateinit var localSipUri: String

    lateinit var remoteSipUri: String

    fun isChatRoomInitialized(): Boolean {
        return ::chatRoom.isInitialized
    }

    @WorkerThread
    open fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
    }

    @WorkerThread
    open fun afterNotifyingChatRoomFound(sameOne: Boolean) {
    }

    @UiThread
    fun findChatRoom(room: ChatRoom?, localSipUri: String, remoteSipUri: String) {
        this.localSipUri = localSipUri
        this.remoteSipUri = remoteSipUri

        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )
            if (room != null && ::chatRoom.isInitialized && chatRoom == room) {
                Log.i("$TAG Conversation object already in memory, skipping")

                beforeNotifyingChatRoomFound(sameOne = true)
                chatRoomFoundEvent.postValue(Event(true))
                afterNotifyingChatRoomFound(sameOne = true)

                return@postOnCoreThread
            }

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)

            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (localAddress?.weakEqual(room.localAddress) == true && remoteAddress?.weakEqual(
                        room.peerAddress
                    ) == true
                ) {
                    Log.i("$TAG Conversation object available in sharedViewModel, using it")
                    chatRoom = room

                    beforeNotifyingChatRoomFound(sameOne = false)
                    chatRoomFoundEvent.postValue(Event(true))
                    afterNotifyingChatRoomFound(sameOne = false)

                    return@postOnCoreThread
                }
            }

            if (localAddress != null && remoteAddress != null) {
                Log.i("$TAG Searching for conversation in Core using local & peer SIP addresses")
                val found = core.searchChatRoom(
                    null,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls(
                        0
                    )
                )
                if (found != null) {
                    chatRoom = found

                    beforeNotifyingChatRoomFound(sameOne = false)
                    chatRoomFoundEvent.postValue(Event(true))
                    afterNotifyingChatRoomFound(sameOne = false)
                } else {
                    Log.e("$TAG Failed to find conversation given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }
}

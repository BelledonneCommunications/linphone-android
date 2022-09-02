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
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ChatRoomCreationViewModel : ContactsSelectionViewModel() {
    val chatRoomCreatedEvent: MutableLiveData<Event<ChatRoom>> by lazy {
        MutableLiveData<Event<ChatRoom>>()
    }

    val createGroupChat = MutableLiveData<Boolean>()

    val isEncrypted = MutableLiveData<Boolean>()

    val waitForChatRoomCreation = MutableLiveData<Boolean>()

    val secureChatAvailable: Boolean = LinphoneUtils.isEndToEndEncryptedChatAvailable()

    private val listener = object : ChatRoomListenerStub() {
        override fun onStateChanged(room: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                waitForChatRoomCreation.value = false
                Log.i("[Chat Room Creation] Chat room created")
                chatRoomCreatedEvent.value = Event(room)
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[Chat Room Creation] Group chat room creation has failed !")
                waitForChatRoomCreation.value = false
                onMessageToNotifyEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }
    }

    init {
        createGroupChat.value = false
        isEncrypted.value = false
        waitForChatRoomCreation.value = false
    }

    fun updateEncryption(encrypted: Boolean) {
        isEncrypted.value = encrypted
    }

    fun createOneToOneChat(searchResult: SearchResult) {
        waitForChatRoomCreation.value = true
        val defaultAccount = coreContext.core.defaultAccount
        var room: ChatRoom?

        val address = searchResult.address ?: coreContext.core.interpretUrl(searchResult.phoneNumber ?: "", LinphoneUtils.applyInternationalPrefix())
        if (address == null) {
            Log.e("[Chat Room Creation] Can't get a valid address from search result $searchResult")
            onMessageToNotifyEvent.value = Event(R.string.chat_room_creation_failed_snack)
            waitForChatRoomCreation.value = false
            return
        }

        val encrypted = isEncrypted.value == true
        val params: ChatRoomParams = coreContext.core.createDefaultChatRoomParams()
        params.backend = ChatRoomBackend.Basic
        params.isGroupEnabled = false
        if (encrypted) {
            params.isEncryptionEnabled = true
            params.backend = ChatRoomBackend.FlexisipChat
            params.ephemeralMode = if (corePreferences.useEphemeralPerDeviceMode)
                ChatRoomEphemeralMode.DeviceManaged
            else
                ChatRoomEphemeralMode.AdminManaged
            params.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default
            Log.i("[Chat Room Creation] Ephemeral mode is ${params.ephemeralMode}, lifetime is ${params.ephemeralLifetime}")
            params.subject = AppUtils.getString(R.string.chat_room_dummy_subject)
        }

        val participants = arrayOf(address)
        val localAddress: Address? = defaultAccount?.params?.identityAddress

        room = coreContext.core.searchChatRoom(params, localAddress, null, participants)
        if (room == null) {
            Log.w("[Chat Room Creation] Couldn't find existing 1-1 chat room with remote ${address.asStringUriOnly()}, encryption=$encrypted and local identity ${localAddress?.asStringUriOnly()}")
            room = coreContext.core.createChatRoom(params, localAddress, participants)

            if (room != null) {
                if (encrypted) {
                    val state = room.state
                    if (state == ChatRoom.State.Created) {
                        Log.i("[Chat Room Creation] Found already created chat room, using it")
                        chatRoomCreatedEvent.value = Event(room)
                        waitForChatRoomCreation.value = false
                    } else {
                        Log.i("[Chat Room Creation] Chat room creation is pending [$state], waiting for Created state")
                        room.addListener(listener)
                    }
                } else {
                    chatRoomCreatedEvent.value = Event(room)
                    waitForChatRoomCreation.value = false
                }
            } else {
                Log.e("[Chat Room Creation] Couldn't create chat room with remote ${address.asStringUriOnly()} and local identity ${localAddress?.asStringUriOnly()}")
                waitForChatRoomCreation.value = false
            }
        } else {
            Log.i("[Chat Room Creation] Found existing 1-1 chat room with remote ${address.asStringUriOnly()}, encryption=$encrypted and local identity ${localAddress?.asStringUriOnly()}")
            chatRoomCreatedEvent.value = Event(room)
            waitForChatRoomCreation.value = false
        }
    }
}

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
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ChatRoomCreationViewModel : ErrorReportingViewModel() {
    val chatRoomCreatedEvent: MutableLiveData<Event<ChatRoom>> by lazy {
        MutableLiveData<Event<ChatRoom>>()
    }

    val createGroupChat = MutableLiveData<Boolean>()

    val sipContactsSelected = MutableLiveData<Boolean>()

    val isEncrypted = MutableLiveData<Boolean>()

    val contactsList = MutableLiveData<ArrayList<SearchResult>>()

    val waitForChatRoomCreation = MutableLiveData<Boolean>()

    val selectedAddresses = MutableLiveData<ArrayList<Address>>()

    val filter = MutableLiveData<String>()
    var previousFilter = ""

    val limeAvailable: Boolean = LinphoneUtils.isLimeAvailable()

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Chat Room Creation] Contacts have changed")
            updateContactsList()
        }
    }

    private val listener = object : ChatRoomListenerStub() {
        override fun onStateChanged(room: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                waitForChatRoomCreation.value = false
                Log.i("[Chat Room Creation] Chat room created")
                chatRoomCreatedEvent.value = Event(room)
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[Chat Room Creation] Group chat room creation has failed !")
                waitForChatRoomCreation.value = false
                onErrorEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }
    }

    init {
        createGroupChat.value = false
        sipContactsSelected.value = true
        isEncrypted.value = false

        selectedAddresses.value = arrayListOf()

        updateContactsList()

        coreContext.contactsManager.addListener(contactsUpdatedListener)
        waitForChatRoomCreation.value = false
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    fun updateEncryption(encrypted: Boolean) {
        isEncrypted.value = encrypted
    }

    fun applyFilter() {
        val filterValue = filter.value.orEmpty()
        if (previousFilter.isNotEmpty() && previousFilter.length > filterValue.length) {
            coreContext.contactsManager.magicSearch.resetSearchCache()
        }
        previousFilter = filterValue

        updateContactsList()
    }

    fun updateContactsList() {
        val domain = if (sipContactsSelected.value == true) coreContext.core.defaultProxyConfig?.domain ?: "" else ""
        val results = coreContext.contactsManager.magicSearch.getContactListFromFilter(filter.value.orEmpty(), domain)

        val list = arrayListOf<SearchResult>()
        for (result in results) {
            list.add(result)
        }
        contactsList.value = list
    }

    fun toggleSelectionForSearchResult(searchResult: SearchResult) {
        if (searchResult.address != null) {
            toggleSelectionForAddress(searchResult.address)
        }
    }

    fun toggleSelectionForAddress(address: Address) {
        val list = arrayListOf<Address>()
        list.addAll(selectedAddresses.value.orEmpty())

        val found = list.find {
            if (address != null) it.weakEqual(address) else false
        }

        if (found != null) {
            list.remove(found)
        } else {
            val contact = coreContext.contactsManager.findContactByAddress(address)
            if (contact != null) address.displayName = contact.fullName
            list.add(address)
        }

        selectedAddresses.value = list
    }

    fun createOneToOneChat(searchResult: SearchResult) {
        waitForChatRoomCreation.value = true
        val defaultProxyConfig = coreContext.core.defaultProxyConfig
        var room: ChatRoom?

        if (defaultProxyConfig == null) {
            val address = searchResult.address ?: coreContext.core.interpretUrl(searchResult.phoneNumber)
            if (address == null) {
                Log.e("[Chat Room Creation] Can't get a valid address from search result $searchResult")
                onErrorEvent.value = Event(R.string.chat_room_creation_failed_snack)
                waitForChatRoomCreation.value = false
                return
            }

            Log.w("[Chat Room Creation] No default proxy config found, creating basic chat room without local identity with ${address.asStringUriOnly()}")
            room = coreContext.core.getChatRoom(address)
            if (room != null) {
                chatRoomCreatedEvent.value = Event(room)
            } else {
                Log.e("[Chat Room Creation] Couldn't create chat room with remote ${address.asStringUriOnly()}")
            }
            waitForChatRoomCreation.value = false
            return
        }

        val encrypted = isEncrypted.value == true
        room = coreContext.core.findOneToOneChatRoom(defaultProxyConfig.identityAddress, searchResult.address, encrypted)
        if (room == null) {
            Log.w("[Chat Room Creation] Couldn't find existing 1-1 chat room with remote ${searchResult.address.asStringUriOnly()}, encryption=$encrypted and local identity ${defaultProxyConfig.identityAddress.asStringUriOnly()}")
            if (encrypted) {
                val params: ChatRoomParams = coreContext.core.createDefaultChatRoomParams()
                // This will set the backend to FlexisipChat automatically
                params.enableEncryption(true)
                params.enableGroup(false)

                val participants = arrayOfNulls<Address>(1)
                participants[0] = searchResult.address

                room = coreContext.core.createChatRoom(
                    params,
                    AppUtils.getString(R.string.chat_room_dummy_subject),
                    participants
                )
                room?.addListener(listener)
            } else {
                room = coreContext.core.getChatRoom(searchResult.address, defaultProxyConfig.identityAddress)
                if (room != null) {
                    chatRoomCreatedEvent.value = Event(room)
                } else {
                    Log.e("[Chat Room Creation] Couldn't create chat room with remote ${searchResult.address.asStringUriOnly()} and local identity ${defaultProxyConfig.identityAddress.asStringUriOnly()}")
                }
                waitForChatRoomCreation.value = false
            }
        } else {
            Log.i("[Chat Room Creation] Found existing 1-1 chat room with remote ${searchResult.address.asStringUriOnly()}, encryption=$encrypted and local identity ${defaultProxyConfig.identityAddress.asStringUriOnly()}")
            chatRoomCreatedEvent.value = Event(room)
            waitForChatRoomCreation.value = false
        }
    }
}

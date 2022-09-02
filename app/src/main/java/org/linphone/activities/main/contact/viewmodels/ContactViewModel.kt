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
package org.linphone.activities.main.contact.viewmodels

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.contact.data.ContactNumberOrAddressClickListener
import org.linphone.activities.main.contact.data.ContactNumberOrAddressData
import org.linphone.activities.main.viewmodels.MessageNotifierViewModel
import org.linphone.contact.ContactDataInterface
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.contact.hasPresence
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PhoneNumberUtils

class ContactViewModelFactory(private val friend: Friend) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ContactViewModel(friend) as T
    }
}

class ContactViewModel(friend: Friend, async: Boolean = false) : MessageNotifierViewModel(), ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val coroutineScope: CoroutineScope = viewModelScope

    var fullName = ""

    val displayOrganization = corePreferences.displayOrganization

    val numbersAndAddresses = MutableLiveData<ArrayList<ContactNumberOrAddressData>>()

    val sendSmsToEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val startCallToEvent: MutableLiveData<Event<Address>> by lazy {
        MutableLiveData<Event<Address>>()
    }

    val chatRoomCreatedEvent: MutableLiveData<Event<ChatRoom>> by lazy {
        MutableLiveData<Event<ChatRoom>>()
    }

    val waitForChatRoomCreation = MutableLiveData<Boolean>()

    val isNativeContact = MutableLiveData<Boolean>()

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                chatRoom.removeListener(this)
                waitForChatRoomCreation.value = false
                chatRoomCreatedEvent.value = Event(chatRoom)
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[Contact Detail] Group chat room creation has failed !")
                chatRoom.removeListener(this)
                waitForChatRoomCreation.value = false
                onMessageToNotifyEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }
    }

    private val contactsListener = object : ContactsUpdatedListenerStub() {
        override fun onContactUpdated(friend: Friend) {
            if (friend.refKey == contact.value?.refKey) {
                Log.i("[Contact Detail] Friend has been updated!")
                contact.value = friend
                displayName.value = friend.name
                isNativeContact.value = friend.refKey != null
                updateNumbersAndAddresses()
            }
        }
    }

    private val listener = object : ContactNumberOrAddressClickListener {
        override fun onCall(address: Address) {
            startCallToEvent.value = Event(address)
        }

        override fun onChat(address: Address, isSecured: Boolean) {
            waitForChatRoomCreation.value = true
            val chatRoom = LinphoneUtils.createOneToOneChatRoom(address, isSecured)

            if (chatRoom != null) {
                val state = chatRoom.state
                Log.i("[Contact Detail] Found existing chat room in state $state")
                if (state == ChatRoom.State.Created || state == ChatRoom.State.Terminated) {
                    waitForChatRoomCreation.value = false
                    chatRoomCreatedEvent.value = Event(chatRoom)
                } else {
                    chatRoom.addListener(chatRoomListener)
                }
            } else {
                waitForChatRoomCreation.value = false
                Log.e("[Contact Detail] Couldn't create chat room with address $address")
                onMessageToNotifyEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }

        override fun onSmsInvite(number: String) {
            sendSmsToEvent.value = Event(number)
        }
    }

    init {
        fullName = friend.name ?: ""

        if (async) {
            contact.postValue(friend)
            displayName.postValue(friend.name)
            isNativeContact.postValue(friend.refKey != null)
        } else {
            contact.value = friend
            displayName.value = friend.name
            isNativeContact.value = friend.refKey != null
        }
    }

    override fun onCleared() {
        destroy()
        super.onCleared()
    }

    fun destroy() {
    }

    fun registerContactListener() {
        coreContext.contactsManager.addListener(contactsListener)
    }

    fun unregisterContactListener() {
        coreContext.contactsManager.removeListener(contactsListener)
    }

    fun deleteContact() {
        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = java.util.ArrayList<ContentProviderOperation>()

        val id = contact.value?.refKey
        if (id != null) {
            Log.i("[Contact] Setting Android contact id $id to batch removal")
            val args = arrayOf(id)
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection(select, args)
                    .build()
            )
        }

        contact.value?.remove()

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contact] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contact] $e")
            }
        }
    }

    fun updateNumbersAndAddresses() {
        val list = arrayListOf<ContactNumberOrAddressData>()
        val friend = contact.value ?: return

        for (address in friend.addresses) {
            val username = address.username
            if (username in friend.phoneNumbers) continue

            val value = address.asStringUriOnly()
            val presenceModel = friend.getPresenceModelForUriOrTel(value)
            val hasPresence = presenceModel?.basicStatus == PresenceBasicStatus.Open
            val isMe = coreContext.core.defaultAccount?.params?.identityAddress?.weakEqual(address) ?: false
            val secureChatAllowed = LinphoneUtils.isEndToEndEncryptedChatAvailable() && !isMe && friend.getPresenceModelForUriOrTel(value)?.hasCapability(FriendCapability.LimeX3Dh) ?: false
            val displayValue = if (coreContext.core.defaultAccount?.params?.domain == address.domain) (address.username ?: value) else value
            val noa = ContactNumberOrAddressData(address, hasPresence, displayValue, showSecureChat = secureChatAllowed, listener = listener)
            list.add(noa)
        }

        for (phoneNumber in friend.phoneNumbersWithLabel) {
            val number = phoneNumber.phoneNumber
            val presenceModel = friend.getPresenceModelForUriOrTel(number)
            val hasPresence = presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open
            val contactAddress = presenceModel?.contact ?: number
            val address = coreContext.core.interpretUrl(contactAddress, true)
            address?.displayName = displayName.value.orEmpty()
            val isMe = if (address != null) coreContext.core.defaultAccount?.params?.identityAddress?.weakEqual(address) ?: false else false
            val secureChatAllowed = LinphoneUtils.isEndToEndEncryptedChatAvailable() && !isMe && friend.getPresenceModelForUriOrTel(number)?.hasCapability(FriendCapability.LimeX3Dh) ?: false
            val label = PhoneNumberUtils.vcardParamStringToAddressBookLabel(coreContext.context.resources, phoneNumber.label ?: "")
            val noa = ContactNumberOrAddressData(address, hasPresence, number, isSip = false, showSecureChat = secureChatAllowed, typeLabel = label, listener = listener)
            list.add(noa)
        }
        numbersAndAddresses.postValue(list)
    }

    fun hasPresence(): Boolean {
        return contact.value?.hasPresence() ?: false
    }
}

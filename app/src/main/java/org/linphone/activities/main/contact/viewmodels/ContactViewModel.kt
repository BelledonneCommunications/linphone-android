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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.contact.data.ContactNumberOrAddressClickListener
import org.linphone.activities.main.contact.data.ContactNumberOrAddressData
import org.linphone.activities.main.viewmodels.ErrorReportingViewModel
import org.linphone.contact.Contact
import org.linphone.contact.ContactDataInterface
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.contact.NativeContact
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ContactViewModelFactory(private val contact: Contact) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ContactViewModel(contact) as T
    }
}

class ContactViewModel(val contactInternal: Contact) : ErrorReportingViewModel(), ContactDataInterface {
    override val contact: MutableLiveData<Contact> = MutableLiveData<Contact>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()

    val name: String
        get() = displayName.value ?: ""

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

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactUpdated(contact: Contact) {
            if (contact is NativeContact && contactInternal is NativeContact && contact.nativeId == contactInternal.nativeId) {
                Log.d("[Contact] $contact has changed")
                updateNumbersAndAddresses(contact)
            }
        }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                waitForChatRoomCreation.value = false
                chatRoomCreatedEvent.value = Event(chatRoom)
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[Contact Detail] Group chat room creation has failed !")
                waitForChatRoomCreation.value = false
                onErrorEvent.value = Event(R.string.chat_room_creation_failed_snack)
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
                if (chatRoom.state == ChatRoom.State.Created) {
                    waitForChatRoomCreation.value = false
                    chatRoomCreatedEvent.value = Event(chatRoom)
                } else {
                    chatRoom.addListener(chatRoomListener)
                }
            } else {
                waitForChatRoomCreation.value = false
                Log.e("[Contact Detail] Couldn't create chat room with address $address")
                onErrorEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }

        override fun onSmsInvite(number: String) {
            sendSmsToEvent.value = Event(number)
        }
    }

    init {
        contact.value = contactInternal
        displayName.value = contactInternal.fullName ?: contactInternal.firstName + " " + contactInternal.lastName

        updateNumbersAndAddresses(contactInternal)
        coreContext.contactsManager.addListener(contactsUpdatedListener)
        waitForChatRoomCreation.value = false
    }

    override fun onCleared() {
        destroy()
        super.onCleared()
    }

    fun destroy() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)
    }

    fun deleteContact() {
        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = java.util.ArrayList<ContentProviderOperation>()

        if (contactInternal is NativeContact) {
            val nativeContact: NativeContact = contactInternal
            Log.i("[Contact] Setting Android contact id ${nativeContact.nativeId} to batch removal")
            val args = arrayOf(nativeContact.nativeId)
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection(select, args)
                    .build()
            )
        }

        if (contactInternal.friend != null) {
            Log.i("[Contact] Removing friend")
            contactInternal.friend?.remove()
        }

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contact] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contact] $e")
            }
        }
    }

    private fun updateNumbersAndAddresses(contact: Contact) {
        val list = arrayListOf<ContactNumberOrAddressData>()
        for (address in contact.sipAddresses) {
            val value = address.asStringUriOnly()
            val presenceModel = contact.friend?.getPresenceModelForUriOrTel(value)
            val hasPresence = presenceModel?.basicStatus == PresenceBasicStatus.Open
            val isMe = coreContext.core.defaultAccount?.params?.identityAddress?.weakEqual(address) ?: false
            val secureChatAllowed = !isMe && contact.friend?.getPresenceModelForUriOrTel(value)?.hasCapability(FriendCapability.LimeX3Dh) ?: false
            val displayValue = if (coreContext.core.defaultAccount?.params?.domain == address.domain) (address.username ?: value) else value
            val noa = ContactNumberOrAddressData(address, hasPresence, displayValue, showSecureChat = secureChatAllowed, listener = listener)
            list.add(noa)
        }
        for (phoneNumber in contact.phoneNumbers) {
            val number = phoneNumber.value
            val presenceModel = contact.friend?.getPresenceModelForUriOrTel(number)
            val hasPresence = presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open
            val contactAddress = presenceModel?.contact ?: number
            val address = coreContext.core.interpretUrl(contactAddress)
            val isMe = if (address != null) coreContext.core.defaultAccount?.params?.identityAddress?.weakEqual(address) ?: false else false
            val secureChatAllowed = !isMe && contact.friend?.getPresenceModelForUriOrTel(number)?.hasCapability(FriendCapability.LimeX3Dh) ?: false
            val noa = ContactNumberOrAddressData(address, hasPresence, number, isSip = false, showSecureChat = secureChatAllowed, typeLabel = phoneNumber.typeLabel, listener = listener)
            list.add(noa)
        }
        numbersAndAddresses.value = list
    }
}

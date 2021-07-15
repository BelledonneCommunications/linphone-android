/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.contact

import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.viewmodels.ErrorReportingViewModel
import org.linphone.core.Address
import org.linphone.core.ChatRoomSecurityLevel
import org.linphone.utils.LinphoneUtils

interface ContactDataInterface {
    val contact: MutableLiveData<Contact>

    val displayName: MutableLiveData<String>

    val securityLevel: MutableLiveData<ChatRoomSecurityLevel>

    val showGroupChatAvatar: Boolean
        get() = false
}

open class GenericContactData(private val sipAddress: Address) : ContactDataInterface {
    final override val contact: MutableLiveData<Contact> = MutableLiveData<Contact>()
    final override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    final override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactUpdated(contact: Contact) {
            contactLookup()
        }
    }

    init {
        securityLevel.value = ChatRoomSecurityLevel.ClearText
        coreContext.contactsManager.addListener(contactsUpdatedListener)
        contactLookup()
    }

    open fun destroy() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)
    }

    private fun contactLookup() {
        displayName.value = LinphoneUtils.getDisplayName(sipAddress)
        contact.value =
            coreContext.contactsManager.findContactByAddress(sipAddress)
    }
}

abstract class GenericContactViewModel(private val sipAddress: Address) : ErrorReportingViewModel(), ContactDataInterface {
    final override val contact: MutableLiveData<Contact> = MutableLiveData<Contact>()
    final override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    final override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactUpdated(contact: Contact) {
            contactLookup()
        }
    }

    init {
        securityLevel.value = ChatRoomSecurityLevel.ClearText
        coreContext.contactsManager.addListener(contactsUpdatedListener)
        contactLookup()
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    private fun contactLookup() {
        displayName.value = LinphoneUtils.getDisplayName(sipAddress)
        contact.value = coreContext.contactsManager.findContactByAddress(sipAddress)
    }
}

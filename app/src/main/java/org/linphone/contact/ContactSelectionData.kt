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
package org.linphone.contact

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.utils.LinphoneUtils

class ContactSelectionData(private val searchResult: SearchResult) : ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val coroutineScope: CoroutineScope = coreContext.coroutineScope

    val isDisabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    val isSelected: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    val isLinphoneUser: Boolean by lazy {
        searchResult.friend?.getPresenceModelForUriOrTel(searchResult.phoneNumber ?: searchResult.address?.asStringUriOnly() ?: "")?.basicStatus == PresenceBasicStatus.Open
    }

    val sipUri: String by lazy {
        searchResult.phoneNumber ?: LinphoneUtils.getDisplayableAddress(searchResult.address)
    }

    val address: Address? by lazy {
        searchResult.address
    }

    val hasLimeX3DHCapability: Boolean
        get() = searchResult.hasCapability(FriendCapability.LimeX3Dh)

    init {
        isDisabled.value = false
        isSelected.value = false
        searchMatchingContact()
    }

    private fun searchMatchingContact() {
        val friend = searchResult.friend
        if (friend != null) {
            contact.value = friend!!
            displayName.value = friend.name
        } else {
            val address = searchResult.address
            if (address != null) {
                contact.value = coreContext.contactsManager.findContactByAddress(address)
                displayName.value = LinphoneUtils.getDisplayName(address)
            } else if (searchResult.phoneNumber != null) {
                contact.value =
                    coreContext.contactsManager.findContactByPhoneNumber(searchResult.phoneNumber.orEmpty())
                displayName.value = searchResult.phoneNumber.orEmpty()
            }
        }
    }
}

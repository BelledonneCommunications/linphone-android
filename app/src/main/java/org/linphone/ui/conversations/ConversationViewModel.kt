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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactData
import org.linphone.contacts.ContactsListener
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class ConversationViewModel : ViewModel() {
    private lateinit var chatRoom: ChatRoom

    val contactName = MutableLiveData<String>()

    val contactData = MutableLiveData<ContactData>()

    val subject = MutableLiveData<String>()

    val isOneToOne = MutableLiveData<Boolean>()

    private val contactsListener = object : ContactsListener {
        override fun onContactsLoaded() {
            contactLookup()
        }
    }

    init {
        coreContext.contactsManager.addListener(contactsListener)
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsListener)
    }

    fun loadChatRoom(localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)

            val found = core.searchChatRoom(
                null,
                localAddress,
                remoteSipAddress,
                arrayOfNulls(
                    0
                )
            )
            if (found != null) {
                chatRoom = found

                isOneToOne.postValue(chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt()))
                subject.postValue(chatRoom.subject)
                contactLookup()
            }
        }
    }

    private fun contactLookup() {
        if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            val remoteAddress = chatRoom.peerAddress
            val friend = chatRoom.core.findFriend(remoteAddress)
            if (friend != null) {
                contactData.postValue(ContactData(friend))
            }
            contactName.postValue(friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress))
        } else {
            if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
                val first = chatRoom.participants.firstOrNull()
                if (first != null) {
                    val remoteAddress = first.address
                    val friend = chatRoom.core.findFriend(remoteAddress)
                    if (friend != null) {
                        contactData.postValue(ContactData(friend))
                    }
                    contactName.postValue(
                        friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress)
                    )
                } else {
                    Log.e("[Conversation View Model] No participant in the chat room!")
                }
            }
        }
    }
}

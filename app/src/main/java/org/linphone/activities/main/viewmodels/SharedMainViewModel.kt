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
package org.linphone.activities.main.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.contact.Contact
import org.linphone.core.*
import org.linphone.utils.Event

class SharedMainViewModel : ViewModel() {
    val toggleDrawerEvent = MutableLiveData<Event<Boolean>>()

    /* Call history */

    val selectedCallLogGroup = MutableLiveData<GroupedCallLogData>()

    /* Chat */

    val selectedChatRoom = MutableLiveData<ChatRoom>()
    var destructionPendingChatRoom: ChatRoom? = null

    val selectedGroupChatRoom = MutableLiveData<ChatRoom>()

    val filesToShare = MutableLiveData<ArrayList<String>>()

    val textToShare = MutableLiveData<String>()

    val messageToForwardEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val contentToOpen = MutableLiveData<Content>()

    var createEncryptedChatRoom: Boolean = false

    val chatRoomParticipants = MutableLiveData<ArrayList<Address>>()

    /* Contacts */

    val selectedContact = MutableLiveData<Contact>()

    /* Accounts */

    val accountRemoved = MutableLiveData<Boolean>()

    /* Call */

    var pendingCallTransfer: Boolean = false

    /* Dialer */

    var dialerUri: String = ""
}

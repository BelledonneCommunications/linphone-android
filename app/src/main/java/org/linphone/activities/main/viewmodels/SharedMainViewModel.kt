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

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.core.*
import org.linphone.utils.Event

class SharedMainViewModel : ViewModel() {
    val toggleDrawerEvent = MutableLiveData<Event<Boolean>>()

    val layoutChangedEvent = MutableLiveData<Event<Boolean>>()
    var isSlidingPaneSlideable = MutableLiveData<Boolean>()

    /* Call history */

    val selectedCallLogGroup = MutableLiveData<GroupedCallLogData>()

    /* Chat */

    val chatRoomFragmentOpenedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val selectedChatRoom = MutableLiveData<ChatRoom>()
    var destructionPendingChatRoom: ChatRoom? = null

    val selectedGroupChatRoom = MutableLiveData<ChatRoom>()

    val filesToShare = MutableLiveData<ArrayList<String>>()

    val textToShare = MutableLiveData<String>()

    val messageToForwardEvent: MutableLiveData<Event<ChatMessage>> by lazy {
        MutableLiveData<Event<ChatMessage>>()
    }

    val isPendingMessageForward = MutableLiveData<Boolean>()

    val contentToOpen = MutableLiveData<Content>()

    var createEncryptedChatRoom: Boolean = corePreferences.forceEndToEndEncryptedChat

    val chatRoomParticipants = MutableLiveData<ArrayList<Address>>()

    var chatRoomSubject: String = ""

    // When using keyboard to share gif or other, see RichContentReceiver & RichEditText classes
    val richContentUri = MutableLiveData<Event<Uri>>()

    val refreshChatRoomInListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    /* Contacts */

    val contactFragmentOpenedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val selectedContact = MutableLiveData<Friend>()

    // For correct animations directions
    val updateContactsAnimationsBasedOnDestination: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    /* Accounts */

    val defaultAccountChanged = MutableLiveData<Boolean>()

    val accountRemoved = MutableLiveData<Boolean>()

    val accountSettingsFragmentOpenedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    /* Call */

    var pendingCallTransfer: Boolean = false

    /* Conference */

    val addressOfConferenceInfoToEdit: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val participantsListForNextScheduledMeeting: MutableLiveData<Event<ArrayList<Address>>> by lazy {
        MutableLiveData<Event<ArrayList<Address>>>()
    }

    /* Dialer */

    var dialerUri: String = ""

    // For correct animations directions
    val updateDialerAnimationsBasedOnDestination: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }
}

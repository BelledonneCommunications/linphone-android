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
package org.linphone.ui.main.viewmodel

import android.net.Uri
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.core.ChatRoom
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.utils.Event

class SharedMainViewModel @UiThread constructor() : ViewModel() {
    // When set to true, it will hide the splashscreen
    var isFirstFragmentReady: Boolean = false

    /* Sliding Pane & navigation related */

    val isSlidingPaneSlideable = MutableLiveData<Boolean>()

    val closeSlidingPaneEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openSlidingPaneEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToHistoryEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToContactsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToConversationsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToMeetingsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var currentlyDisplayedFragment = MutableLiveData<Int>()

    /* Top bar related */

    val searchFilter: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val refreshDrawerMenuAccountsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    /* Contacts related */

    val showContactEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val showNewContactEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var sipAddressToAddToNewContact: String = ""

    /* Call logs related */

    val forceRefreshCallLogsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val resetMissedCallsCountEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    /* Conversation related */

    val textToShareFromIntent = MutableLiveData<String>()

    val filesToShareFromIntent = MutableLiveData<ArrayList<String>>()

    val messageToForwardEvent = MutableLiveData<Event<MessageModel>>()

    var displayedChatRoom: ChatRoom? = null // Prevents the need to go look for the chat room
    val showConversationEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    // When using keyboard to share gif or other, see RichContentReceiver & RichEditText classes
    val richContentUri = MutableLiveData<Event<Uri>>()

    val forceRefreshConversationInfo: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val forceRefreshConversationEvents: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val newChatMessageEphemeralLifetimeToSet: MutableLiveData<Event<Long>> by lazy {
        MutableLiveData<Event<Long>>()
    }

    /* Meetings related */

    val forceRefreshMeetingsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToMeetingWaitingRoomEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val goToScheduleMeetingEvent: MutableLiveData<Event<ArrayList<String>>> by lazy {
        MutableLiveData<Event<ArrayList<String>>>()
    }

    /* Other */

    val listOfSelectedSipUrisEvent: MutableLiveData<Event<ArrayList<String>>> by lazy {
        MutableLiveData<Event<ArrayList<String>>>()
    }

    val displayFileEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }
}

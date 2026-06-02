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
import android.os.Bundle
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.Friend
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.recordings.model.RecordingModel
import org.linphone.utils.Event

class SharedMainViewModel
    @UiThread
    constructor() : ViewModel() {
    // Sliding Pane & navigation related

    val isSlidingPaneSlideable = MutableLiveData<Boolean>()

    val closeSlidingPaneEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val openSlidingPaneEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val navigateToHistoryEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val navigateToContactsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val navigateToConversationsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val navigateToMeetingsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    var currentlyDisplayedFragment = MutableLiveData<Int>()

    // Top bar related

    val searchFilter: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val refreshDrawerMenuAccountsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val refreshDrawerMenuQuitButtonEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val forceUpdateAvailableNavigationItems: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    // Account Profile related

    val goToAccountProfileEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    // Contacts related

    var displayedFriend: Friend? = null // Prevents the need to go look for the friend
    val showContactEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val showNewContactEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val forceRefreshContactsList: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    var sipAddressToAddToNewContact: String = ""

    // Call logs related

    val forceRefreshCallLogsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val resetMissedCallsCountEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    // Conversation related

    val textToShareFromIntent = MutableLiveData<String>()

    val filesToShareFromIntent = MutableLiveData<ArrayList<String>>()

    val messageToForwardEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData()
    }

    var displayedChatRoom: ChatRoom? = null // Prevents the need to go look for the chat room

    val showConversationEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val hideConversationEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    // When using keyboard to share gif or other, see RichContentReceiver & RichEditText classes
    val richContentUri = MutableLiveData<Event<Uri>>()

    val displayFileEvent: MutableLiveData<Event<Bundle>> by lazy {
        MutableLiveData()
    }

    val forceRefreshDisplayedConversationEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val forceRefreshConversationInfoEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val forceRefreshConversationEvents: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val newChatMessageEphemeralLifetimeToSetEvent: MutableLiveData<Event<Long>> by lazy {
        MutableLiveData()
    }

    val updateConversationLastMessageEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val updateUnreadMessageCountForCurrentConversationEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    // Meetings related

    var displayedMeeting: ConferenceInfo? = null // Prevents the need to go look for the conference info

    val meetingEditedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val forceRefreshMeetingsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val goToMeetingWaitingRoomEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val goToScheduleMeetingEvent: MutableLiveData<Event<Pair<String, ArrayList<String>>>> by lazy {
        MutableLiveData()
    }

    // Recordings related

    var playingRecording: RecordingModel? = null

    // Other

    val mediaViewerFullScreenMode = MutableLiveData<Boolean>()

    val listOfSelectedSipUrisEvent: MutableLiveData<Event<ArrayList<String>>> by lazy {
        MutableLiveData()
    }
}

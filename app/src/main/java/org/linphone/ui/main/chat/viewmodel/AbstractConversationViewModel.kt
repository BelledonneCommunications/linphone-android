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
@file:Suppress("EmptyMethod")

package org.linphone.ui.main.chat.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.Conference
import org.linphone.core.ConferenceListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

abstract class AbstractConversationViewModel : GenericViewModel() {
    companion object {
        private const val TAG = "[Abstract Conversation ViewModel]"
    }

    val chatRoomFoundEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val confirmGroupCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    lateinit var chatRoom: ChatRoom

    lateinit var conversationId: String

    private val conferenceListener = object : ConferenceListenerStub() {
        @WorkerThread
        override fun onStateChanged(conference: Conference, newState: Conference.State?) {
            Log.i("$TAG Conference state changed [$newState]")
            when (newState) {
                Conference.State.CreationFailed -> {
                    showRedToast(R.string.conference_failed_to_create_group_call_toast, R.drawable.warning_circle)
                    conference.removeListener(this)
                }
                Conference.State.Created -> {
                    conference.removeListener(this)
                }
                else -> {}
            }
        }
    }

    fun isChatRoomInitialized(): Boolean {
        return ::chatRoom.isInitialized
    }

    @WorkerThread
    open fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
    }

    @WorkerThread
    open fun afterNotifyingChatRoomFound(sameOne: Boolean) {
    }

    @UiThread
    fun findChatRoom(room: ChatRoom?, conversationId: String) {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Looking for conversation with conversation ID [$conversationId]")
            if (room != null && ::chatRoom.isInitialized && chatRoom == room) {
                Log.i("$TAG Conversation object already in memory, skipping")

                this@AbstractConversationViewModel.conversationId = conversationId
                beforeNotifyingChatRoomFound(sameOne = true)
                chatRoomFoundEvent.postValue(Event(true))
                afterNotifyingChatRoomFound(sameOne = true)

                return@postOnCoreThread
            }

            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (conversationId == LinphoneUtils.getConversationId(room)) {
                    Log.i("$TAG Conversation object available in sharedViewModel, using it")
                    chatRoom = room

                    this@AbstractConversationViewModel.conversationId = conversationId
                    beforeNotifyingChatRoomFound(sameOne = false)
                    chatRoomFoundEvent.postValue(Event(true))
                    afterNotifyingChatRoomFound(sameOne = false)

                    return@postOnCoreThread
                }
            }

            if (conversationId.isNotEmpty()) {
                Log.i("$TAG Searching for conversation in Core using local & peer SIP addresses")
                val found = core.searchChatRoomByIdentifier(conversationId)
                if (found != null) {
                    this@AbstractConversationViewModel.conversationId = conversationId
                    if (::chatRoom.isInitialized && chatRoom == found) {
                        Log.i("$TAG Conversation object already in memory, keeping it")
                        beforeNotifyingChatRoomFound(sameOne = true)
                        chatRoomFoundEvent.postValue(Event(true))
                        afterNotifyingChatRoomFound(sameOne = true)
                    } else {
                        chatRoom = found
                        Log.i("$TAG Found conversation in Core, using it")

                        beforeNotifyingChatRoomFound(sameOne = false)
                        chatRoomFoundEvent.postValue(Event(true))
                        afterNotifyingChatRoomFound(sameOne = false)
                    }
                } else {
                    Log.e("$TAG Failed to find conversation given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun startCall() {
        coreContext.postOnCoreThread {
            if (LinphoneUtils.isChatRoomAGroup(chatRoom) && chatRoom.participants.size >= 2) {
                confirmGroupCallEvent.postValue(Event(true))
            } else {
                val firstParticipant = chatRoom.participants.firstOrNull()
                val address = firstParticipant?.address
                if (address != null) {
                    Log.i("$TAG Audio calling SIP address [${address.asStringUriOnly()}]")
                    coreContext.startAudioCall(address)
                } else {
                    Log.e("$TAG Failed to find participant to call!")
                }
            }
        }
    }

    @UiThread
    fun startGroupCall() {
        coreContext.postOnCoreThread { core ->
            val account = core.defaultAccount
            if (account == null) {
                Log.e(
                    "$TAG No default account found, can't create group call!"
                )
                return@postOnCoreThread
            }

            val conference = LinphoneUtils.createGroupCall(account, chatRoom.subject.orEmpty())
            if (conference == null) {
                Log.e("$TAG Failed to create group call!")
                showRedToast(R.string.conference_failed_to_create_group_call_toast, R.drawable.warning_circle)
                return@postOnCoreThread
            }

            val callParams = core.createCallParams(null)
            callParams?.isVideoEnabled = true
            callParams?.videoDirection = MediaDirection.RecvOnly

            val participants = arrayOfNulls<Address>(chatRoom.participants.size)
            var index = 0
            for (participant in chatRoom.participants) {
                participants[index] = participant.address
                index += 1
            }
            Log.i(
                "$TAG Inviting ${participants.size} participant(s) into newly created conference"
            )
            if (conference.inviteParticipants(participants, callParams) != 0) {
                Log.e("$TAG Failed to invite participants into group call!")
                showRedToast(R.string.conference_failed_to_create_group_call_toast, R.drawable.warning_circle)
            } else {
                conference.addListener(conferenceListener)
            }
        }
    }
}

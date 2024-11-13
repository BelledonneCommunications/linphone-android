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
import org.linphone.core.ConferenceParams
import org.linphone.core.ConferenceScheduler
import org.linphone.core.ConferenceSchedulerListenerStub
import org.linphone.core.Factory
import org.linphone.core.Participant
import org.linphone.core.ParticipantInfo
import org.linphone.core.StreamType
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

    lateinit var localSipUri: String

    lateinit var remoteSipUri: String

    fun isChatRoomInitialized(): Boolean {
        return ::chatRoom.isInitialized
    }

    private val conferenceSchedulerListener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State
        ) {
            Log.i("$TAG Conference scheduler state is $state")
            if (state == ConferenceScheduler.State.Ready) {
                conferenceScheduler.removeListener(this)

                val conferenceAddress = conferenceScheduler.info?.uri
                if (conferenceAddress != null) {
                    Log.i(
                        "$TAG Conference info created, address is ${conferenceAddress.asStringUriOnly()}"
                    )
                    coreContext.startVideoCall(conferenceAddress)
                } else {
                    Log.e("$TAG Conference info URI is null!")
                    showRedToastEvent.postValue(
                        Event(
                            Pair(
                                R.string.conference_failed_to_create_group_call_toast,
                                R.drawable.warning_circle
                            )
                        )
                    )
                }
            } else if (state == ConferenceScheduler.State.Error) {
                conferenceScheduler.removeListener(this)
                Log.e("$TAG Failed to create group call!")
                showRedToastEvent.postValue(
                    Event(
                        Pair(
                            R.string.conference_failed_to_create_group_call_toast,
                            R.drawable.warning_circle
                        )
                    )
                )
            }
        }
    }

    @WorkerThread
    open fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
    }

    @WorkerThread
    open fun afterNotifyingChatRoomFound(sameOne: Boolean) {
    }

    @UiThread
    fun findChatRoom(room: ChatRoom?, localSipUri: String, remoteSipUri: String) {
        this.localSipUri = localSipUri
        this.remoteSipUri = remoteSipUri

        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )
            if (room != null && ::chatRoom.isInitialized && chatRoom == room) {
                Log.i("$TAG Conversation object already in memory, skipping")

                beforeNotifyingChatRoomFound(sameOne = true)
                chatRoomFoundEvent.postValue(Event(true))
                afterNotifyingChatRoomFound(sameOne = true)

                return@postOnCoreThread
            }

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)

            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (localAddress?.weakEqual(room.localAddress) == true && remoteAddress?.weakEqual(
                        room.peerAddress
                    ) == true
                ) {
                    Log.i("$TAG Conversation object available in sharedViewModel, using it")
                    chatRoom = room

                    beforeNotifyingChatRoomFound(sameOne = false)
                    chatRoomFoundEvent.postValue(Event(true))
                    afterNotifyingChatRoomFound(sameOne = false)

                    return@postOnCoreThread
                }
            }

            if (localAddress != null && remoteAddress != null) {
                Log.i("$TAG Searching for conversation in Core using local & peer SIP addresses")
                val params: ConferenceParams? = null
                val found = core.searchChatRoom(
                    params,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls<Address>(
                        0
                    )
                )
                if (found != null) {
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

            val conferenceInfo = Factory.instance().createConferenceInfo()
            conferenceInfo.organizer = account.params.identityAddress
            conferenceInfo.subject = chatRoom.subject

            // Allows to have a chat room within the conference
            conferenceInfo.setCapability(StreamType.Text, true)

            val participants = arrayOfNulls<ParticipantInfo>(chatRoom.participants.size)
            var index = 0
            for (participant in chatRoom.participants) {
                val info = Factory.instance().createParticipantInfo(participant.address)
                // For meetings, all participants must have Speaker role
                info?.role = Participant.Role.Speaker
                participants[index] = info
                index += 1
            }
            conferenceInfo.setParticipantInfos(participants)

            Log.i(
                "$TAG Creating group call with subject ${conferenceInfo.subject} and ${participants.size} participant(s)"
            )
            val conferenceScheduler = LinphoneUtils.createConferenceScheduler(account)
            conferenceScheduler.addListener(conferenceSchedulerListener)
            conferenceScheduler.account = account
            // Will trigger the conference creation/update automatically
            conferenceScheduler.info = conferenceInfo
        }
    }
}

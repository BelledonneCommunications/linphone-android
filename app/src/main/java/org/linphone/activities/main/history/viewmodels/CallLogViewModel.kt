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
package org.linphone.activities.main.history.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.*
import kotlin.collections.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.history.data.CallLogData
import org.linphone.contact.GenericContactViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CallLogViewModelFactory(private val callLog: CallLog) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CallLogViewModel(callLog) as T
    }
}

class CallLogViewModel(val callLog: CallLog) : GenericContactViewModel(callLog.remoteAddress) {
    val peerSipUri: String by lazy {
        LinphoneUtils.getDisplayableAddress(callLog.remoteAddress)
    }

    val startCallEvent: MutableLiveData<Event<CallLog>> by lazy {
        MutableLiveData<Event<CallLog>>()
    }

    val chatRoomCreatedEvent: MutableLiveData<Event<ChatRoom>> by lazy {
        MutableLiveData<Event<ChatRoom>>()
    }

    val waitForChatRoomCreation = MutableLiveData<Boolean>()

    val chatAllowed = !corePreferences.disableChat

    val secureChatAllowed = contact.value?.friend?.getPresenceModelForUriOrTel(peerSipUri)?.hasCapability(FriendCapability.LimeX3Dh) ?: false

    val relatedCallLogs = MutableLiveData<ArrayList<CallLogData>>()

    private val listener = object : CoreListenerStub() {
        override fun onCallLogUpdated(core: Core, log: CallLog) {
            if (callLog.remoteAddress.weakEqual(log.remoteAddress) && callLog.localAddress.weakEqual(log.localAddress)) {
                Log.i("[History Detail] New call log for ${callLog.remoteAddress.asStringUriOnly()} with local address ${callLog.localAddress.asStringUriOnly()}")
                addRelatedCallLogs(arrayListOf(log))
            }
        }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                waitForChatRoomCreation.value = false
                chatRoomCreatedEvent.value = Event(chatRoom)
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[History Detail] Group chat room creation has failed !")
                waitForChatRoomCreation.value = false
                onErrorEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }
    }

    init {
        waitForChatRoomCreation.value = false

        coreContext.core.addListener(listener)
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        destroy()

        super.onCleared()
    }

    fun destroy() {
        relatedCallLogs.value.orEmpty().forEach(CallLogData::destroy)
    }

    fun startCall() {
        startCallEvent.value = Event(callLog)
    }

    fun startChat(isSecured: Boolean) {
        waitForChatRoomCreation.value = true
        val chatRoom = LinphoneUtils.createOneToOneChatRoom(callLog.remoteAddress, isSecured)
        if (chatRoom != null) {
            if (chatRoom.state == ChatRoom.State.Created) {
                waitForChatRoomCreation.value = false
                chatRoomCreatedEvent.value = Event(chatRoom)
            } else {
                chatRoom.addListener(chatRoomListener)
            }
        } else {
            waitForChatRoomCreation.value = false
            Log.e("[History Detail] Couldn't create chat room with address ${callLog.remoteAddress}")
            onErrorEvent.value = Event(R.string.chat_room_creation_failed_snack)
        }
    }

    fun addRelatedCallLogs(logs: ArrayList<CallLog>) {
        val callsHistory = ArrayList<CallLogData>()

        // We assume new logs are more recent than the ones we already have, so we add them first
        for (log in logs) {
            callsHistory.add(CallLogData(log))
        }
        callsHistory.addAll(relatedCallLogs.value.orEmpty())

        relatedCallLogs.value = callsHistory
    }
}

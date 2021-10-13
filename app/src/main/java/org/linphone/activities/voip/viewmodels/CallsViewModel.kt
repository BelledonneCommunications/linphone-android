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
package org.linphone.activities.voip.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.voip.data.CallData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class CallsViewModel : ViewModel() {
    val currentCallData = MutableLiveData<CallData>()

    val callsData = MutableLiveData<List<CallData>>()

    val inactiveCallsCount = MutableLiveData<Int>()

    val currentCallUnreadChatMessageCount = MutableLiveData<Int>()

    val chatAndCallsCount = MediatorLiveData<Int>()

    val callConnectedEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val callUpdateEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val noMoreCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : CoreListenerStub() {
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            if (chatRoom == currentCallData.value?.chatRoom) {
                updateUnreadChatCount()
            }
        }

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            if (chatRoom == currentCallData.value?.chatRoom) {
                updateUnreadChatCount()
            }
        }

        override fun onLastCallEnded(core: Core) {
            currentCallData.value?.destroy()
            noMoreCallEvent.value = Event(true)
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            Log.i("[Calls] Call state changed: $state")

            val currentCall = core.currentCall
            if (currentCall != null && currentCallData.value?.call != currentCall) {

                currentCallData.value?.destroy()
                val viewModel = CallData(currentCall)
                currentCallData.value = viewModel
            } else if (currentCall == null && core.callsNb > 1) {
                val firstCall = core.calls.first()
                if (firstCall != null && currentCallData.value?.call != firstCall) {
                    currentCallData.value?.destroy()
                    val viewModel = CallData(firstCall)
                    currentCallData.value = viewModel
                }
            }

            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                removeCallFromList(call)
            } else if (state == Call.State.IncomingEarlyMedia || state == Call.State.IncomingReceived || state == Call.State.OutgoingInit) {
                if (call != core.currentCall) {
                    addCallToList(call)
                }
            } else if (call.state == Call.State.UpdatedByRemote) {
                // If the correspondent asks to turn on video while audio call,
                // defer update until user has chosen whether to accept it or not
                val remoteVideo = call.remoteParams?.videoEnabled() ?: false
                val localVideo = call.currentParams.videoEnabled()
                val autoAccept = call.core.videoActivationPolicy.automaticallyAccept
                if (remoteVideo && !localVideo && !autoAccept) {
                    if (coreContext.core.videoCaptureEnabled() || coreContext.core.videoDisplayEnabled()) {
                        call.deferUpdate()
                        callUpdateEvent.value = Event(call)
                    } else {
                        coreContext.answerCallVideoUpdateRequest(call, false)
                    }
                }
            } else if (state == Call.State.Connected) {
                callConnectedEvent.value = Event(call)
            } else if (state == Call.State.StreamsRunning) {
                callUpdateEvent.value = Event(call)
            }

            updateInactiveCallsCount()
        }
    }

    init {
        coreContext.core.addListener(listener)

        val currentCall = coreContext.core.currentCall
        if (currentCall != null) {
            currentCallData.value?.destroy()

            val viewModel = CallData(currentCall)
            currentCallData.value = viewModel
        }

        chatAndCallsCount.value = 0
        chatAndCallsCount.addSource(inactiveCallsCount) {
            chatAndCallsCount.value = updateCallsAndChatCount()
        }
        chatAndCallsCount.addSource(currentCallUnreadChatMessageCount) {
            chatAndCallsCount.value = updateCallsAndChatCount()
        }

        initCallList()
        updateInactiveCallsCount()
        updateUnreadChatCount()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        currentCallData.value?.destroy()
        callsData.value.orEmpty().forEach(CallData::destroy)

        super.onCleared()
    }

    fun mergeCallsIntoLocalConference() {
        val core = coreContext.core
        val currentCallVideoEnabled = core.currentCall?.currentParams?.videoEnabled() ?: false

        val params = core.createConferenceParams()
        params.isVideoEnabled = currentCallVideoEnabled
        Log.i("[Calls] Setting videoEnabled to [$currentCallVideoEnabled] in conference params")

        val conference = core.conference ?: core.createConferenceWithParams(params)
        conference?.addParticipants(core.calls)
    }

    private fun initCallList() {
        val calls = arrayListOf<CallData>()

        for (call in coreContext.core.calls) {
            val data: CallData = if (currentCallData.value?.call == call) {
                currentCallData.value!!
            } else {
                CallData(call)
            }
            Log.i("[Calls] Adding call ${call.callLog.callId} to calls list")
            calls.add(data)
        }

        callsData.value = calls
    }

    private fun addCallToList(call: Call) {
        Log.i("[Calls] Adding call ${call.callLog.callId} to calls list")

        val calls = arrayListOf<CallData>()
        calls.addAll(callsData.value.orEmpty())

        val data = CallData(call)
        calls.add(data)

        callsData.value = calls
    }

    private fun removeCallFromList(call: Call) {
        Log.i("[Calls] Removing call ${call.callLog.callId} from calls list")

        val calls = arrayListOf<CallData>()
        calls.addAll(callsData.value.orEmpty())

        val data = calls.find { it.call == call }
        calls.remove(data)

        callsData.value = calls
    }

    private fun updateCallsAndChatCount(): Int {
        return (inactiveCallsCount.value ?: 0) + (currentCallUnreadChatMessageCount.value ?: 0)
    }

    private fun updateUnreadChatCount() {
        currentCallUnreadChatMessageCount.value = currentCallData.value?.chatRoom?.unreadMessagesCount ?: 0
    }

    private fun updateInactiveCallsCount() {
        // TODO handle local conference
        inactiveCallsCount.value = coreContext.core.callsNb - 1
    }
}

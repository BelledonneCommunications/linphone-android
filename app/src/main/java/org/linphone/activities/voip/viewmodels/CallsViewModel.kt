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

import android.Manifest
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.voip.data.CallData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class CallsViewModel : ViewModel() {
    val currentCallData = MutableLiveData<CallData>()

    val callsData = MutableLiveData<List<CallData>>()

    val inactiveCallsCount = MutableLiveData<Int>()

    val currentCallUnreadChatMessageCount = MutableLiveData<Int>()

    val chatAndCallsCount = MediatorLiveData<Int>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isMuteMicrophoneEnabled = MutableLiveData<Boolean>()

    val askWriteExternalStoragePermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val callConnectedEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val callEndedEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val callUpdateEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val noMoreCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val listener = object : CoreListenerStub() {
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            updateUnreadChatCount()
        }

        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            updateUnreadChatCount()
        }

        override fun onLastCallEnded(core: Core) {
            Log.i("[Calls] Last call ended")
            currentCallData.value?.destroy()
            noMoreCallEvent.value = Event(true)
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            Log.i("[Calls] Call with ID [${call.callLog.callId}] state changed: $state")

            if (state == Call.State.IncomingEarlyMedia || state == Call.State.IncomingReceived || state == Call.State.OutgoingInit) {
                if (!callDataAlreadyExists(call)) {
                    addCallToList(call)
                }
            }

            val currentCall = core.currentCall
            if (currentCall != null && currentCallData.value?.call != currentCall) {
                updateCurrentCallData(currentCall)
            } else if (currentCall == null && core.callsNb > 0) {
                updateCurrentCallData(currentCall)
            }

            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                removeCallFromList(call)
                if (core.callsNb > 0) {
                    callEndedEvent.value = Event(call)
                }
            } else if (call.state == Call.State.UpdatedByRemote) {
                // If the correspondent asks to turn on video while audio call,
                // defer update until user has chosen whether to accept it or not
                val remoteVideo = call.remoteParams?.isVideoEnabled ?: false
                val localVideo = call.currentParams.isVideoEnabled
                val autoAccept = call.core.videoActivationPolicy.automaticallyAccept
                if (remoteVideo && !localVideo && !autoAccept) {
                    if (coreContext.core.isVideoCaptureEnabled || coreContext.core.isVideoDisplayEnabled) {
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
        updateMicState()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        currentCallData.value?.destroy()
        callsData.value.orEmpty().forEach(CallData::destroy)

        super.onCleared()
    }

    fun toggleMuteMicrophone() {
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.RECORD_AUDIO)
            return
        }

        val micMuted = currentCallData.value?.call?.microphoneMuted ?: false
        currentCallData.value?.call?.microphoneMuted = !micMuted
        updateMicState()
    }

    fun mergeCallsIntoConference() {
        Log.i("[Calls] Merging all calls into new conference")
        val core = coreContext.core
        val params = core.createConferenceParams(null)
        params.subject = AppUtils.getString(R.string.conference_local_title)
        // Prevent group call to start in audio only layout
        params.isVideoEnabled = true
        val conference = core.createConferenceWithParams(params)
        conference?.addParticipants(core.calls)
    }

    fun takeSnapshot() {
        if (!PermissionHelper.get().hasWriteExternalStoragePermission()) {
            askWriteExternalStoragePermissionEvent.value = Event(true)
        } else {
            if (currentCallData.value?.call?.currentParams?.isVideoEnabled == true) {
                val fileName = System.currentTimeMillis().toString() + ".jpeg"
                Log.i("[Calls] Snapshot will be save under $fileName")
                currentCallData.value?.call?.takeVideoSnapshot(FileUtils.getFileStoragePath(fileName).absolutePath)
            } else {
                Log.e("[Calls] Current call doesn't have video, can't take snapshot")
            }
        }
    }

    private fun initCallList() {
        val calls = arrayListOf<CallData>()

        for (call in coreContext.core.calls) {
            val data: CallData = if (currentCallData.value?.call == call) {
                currentCallData.value!!
            } else {
                CallData(call)
            }
            Log.i("[Calls] Adding call with ID ${call.callLog.callId} to calls list")
            calls.add(data)
        }

        callsData.value = calls
    }

    private fun addCallToList(call: Call) {
        Log.i("[Calls] Adding call with ID ${call.callLog.callId} to calls list")

        val calls = arrayListOf<CallData>()
        calls.addAll(callsData.value.orEmpty())

        val data = CallData(call)
        calls.add(data)

        callsData.value = calls
    }

    private fun removeCallFromList(call: Call) {
        Log.i("[Calls] Removing call with ID ${call.callLog.callId} from calls list")

        val calls = arrayListOf<CallData>()
        calls.addAll(callsData.value.orEmpty())

        val data = calls.find { it.call == call }
        if (data == null) {
            Log.w("[Calls] Data for call to remove wasn't found")
        } else {
            data.destroy()
            calls.remove(data)
        }

        callsData.value = calls
    }

    private fun updateCurrentCallData(currentCall: Call?) {
        var callToUse = currentCall
        if (currentCall == null) {
            Log.w("[Calls] Current call is now null")

            val firstCall = coreContext.core.calls.find { call ->
                call.state != Call.State.Error && call.state != Call.State.End && call.state != Call.State.Released
            }
            if (firstCall != null && currentCallData.value?.call != firstCall) {
                Log.i("[Calls] Using [${firstCall.remoteAddress.asStringUriOnly()}] call as \"current\" call")
                callToUse = firstCall
            }
        }

        if (callToUse == null) {
            Log.w("[Calls] No call found to be used as \"current\"")
            return
        }

        var found = false
        for (callData in callsData.value.orEmpty()) {
            if (callData.call == callToUse) {
                Log.i("[Calls] Updating current call to: ${callData.call.remoteAddress.asStringUriOnly()}")
                currentCallData.value = callData
                found = true
                break
            }
        }
        if (!found) {
            Log.w("[Calls] Call with ID [${callToUse.callLog.callId}] not found in calls data list, shouldn't happen!")
            val viewModel = CallData(callToUse)
            currentCallData.value = viewModel
        }

        updateMicState()
        // updateUnreadChatCount()
    }

    private fun callDataAlreadyExists(call: Call): Boolean {
        for (callData in callsData.value.orEmpty()) {
            if (callData.call == call) {
                return true
            }
        }
        return false
    }

    fun updateMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || currentCallData.value?.call?.microphoneMuted == true
        isMuteMicrophoneEnabled.value = currentCallData.value?.call != null
    }

    private fun updateCallsAndChatCount(): Int {
        return (inactiveCallsCount.value ?: 0) + (currentCallUnreadChatMessageCount.value ?: 0)
    }

    private fun updateUnreadChatCount() {
        // For now we don't display in-call chat, so use global unread chat messages count
        currentCallUnreadChatMessageCount.value = coreContext.core.unreadChatMessageCountFromActiveLocals
    }

    private fun updateInactiveCallsCount() {
        // TODO: handle local conference
        val callsNb = coreContext.core.callsNb
        inactiveCallsCount.value = if (callsNb > 0) callsNb - 1 else 0
    }
}

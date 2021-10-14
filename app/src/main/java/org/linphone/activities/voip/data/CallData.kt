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
package org.linphone.activities.voip.data

import android.view.View
import androidx.lifecycle.MutableLiveData
import java.util.*
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contact.GenericContactData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils

open class CallData(val call: Call) : GenericContactData(call.remoteAddress) {
    interface CallContextMenuClickListener {
        fun onShowContextMenu(anchor: View, callData: CallData)
    }

    val address = call.remoteAddress.asStringUriOnly()

    val isPaused = MutableLiveData<Boolean>()
    val isRemotelyPaused = MutableLiveData<Boolean>()
    val canBePaused = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()
    val isRemotelyRecorded = MutableLiveData<Boolean>()

    val isInRemoteConference = MutableLiveData<Boolean>()
    val remoteConferenceSubject = MutableLiveData<String>()

    val isOutgoing = MutableLiveData<Boolean>()
    val isIncoming = MutableLiveData<Boolean>()

    var chatRoom: ChatRoom? = null

    var contextMenuClickListener: CallContextMenuClickListener? = null

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            if (call != this@CallData.call) return
            Log.i("[Call] State changed: $state")

            update()
        }

        override fun onRemoteRecording(call: Call, recording: Boolean) {
            Log.i("[Call] Remote recording changed: $recording")
            isRemotelyRecorded.value = recording
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        call.addListener(listener)
        isRemotelyRecorded.value = call.remoteParams?.isRecording

        update()
        initChatRoom()
    }

    override fun destroy() {
        call.removeListener(listener)
        scope.cancel()

        super.destroy()
    }

    fun togglePause() {
        if (isCallPaused()) {
            resume()
        } else {
            pause()
        }
    }

    fun pause() {
        call.pause()
    }

    fun resume() {
        call.resume()
    }

    fun accept() {
        call.accept()
    }

    fun terminate() {
        call.terminate()
    }

    fun toggleRecording() {
        if (call.isRecording) {
            call.stopRecording()
        } else {
            call.startRecording()
        }
        isRecording.value = call.isRecording
    }

    fun showContextMenu(anchor: View) {
        contextMenuClickListener?.onShowContextMenu(anchor, this)
    }

    private fun initChatRoom() {
        val localSipUri = coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
        val remoteSipUri = call.remoteAddress.asStringUriOnly()

        if (localSipUri != null) {
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            chatRoom = coreContext.core.searchChatRoom(null, localAddress, remoteSipAddress, arrayOfNulls(0))
            if (chatRoom == null) chatRoom = coreContext.core.searchChatRoom(null, localAddress, null, arrayOf(remoteSipAddress))
            if (chatRoom == null) {
                Log.w("[Call] Failed to find existing chat room for local address [$localSipUri] and remote address [$remoteSipUri]")
                val chatRoomParams = coreContext.core.createDefaultChatRoomParams()
                // TODO: configure chat room params
                chatRoom = coreContext.core.createChatRoom(chatRoomParams, localAddress, arrayOf(remoteSipAddress))
            }

            if (chatRoom == null) {
                Log.e("[Call] Failed to create a chat room for local address [$localSipUri] and remote address [$remoteSipUri]!")
            }
        } else {
            Log.e("[Call] Failed to get either local [$localSipUri] or remote [$remoteSipUri] SIP address!")
        }
    }

    private fun isCallPaused(): Boolean {
        return when (call.state) {
            Call.State.Paused, Call.State.Pausing -> true
            else -> false
        }
    }

    private fun isCallRemotelyPaused(): Boolean {
        return when (call.state) {
            Call.State.PausedByRemote -> true
            else -> false
        }
    }

    private fun canCallBePaused(): Boolean {
        return !call.mediaInProgress() && when (call.state) {
            Call.State.StreamsRunning, Call.State.PausedByRemote -> true
            else -> false
        }
    }

    private fun update() {
        isPaused.value = isCallPaused()
        isRemotelyPaused.value = isCallRemotelyPaused()
        canBePaused.value = canCallBePaused()

        val conference = call.conference
        isInRemoteConference.value = conference != null
        if (conference != null) {
            remoteConferenceSubject.value = if (conference.subject.isNullOrEmpty()) {
                AppUtils.getString(R.string.conference_default_title)
            } else {
                conference.subject
            }
        }

        isOutgoing.value = when (call.state) {
            Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> true
            else -> false
        }
        isIncoming.value = when (call.state) {
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> true
            else -> false
        }

        // Check periodically until mediaInProgress is false
        if (call.mediaInProgress()) {
            scope.launch {
                delay(1000)
                update()
            }
        }
    }
}

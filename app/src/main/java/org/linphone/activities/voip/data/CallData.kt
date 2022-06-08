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
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import java.util.*
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.contact.GenericContactData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

open class CallData(val call: Call) : GenericContactData(call.remoteAddress) {
    interface CallContextMenuClickListener {
        fun onShowContextMenu(anchor: View, callData: CallData)
    }

    val displayableAddress = MutableLiveData<String>()

    val isPaused = MutableLiveData<Boolean>()
    val isRemotelyPaused = MutableLiveData<Boolean>()
    val canBePaused = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()
    val isRemotelyRecorded = MutableLiveData<Boolean>()

    val isInRemoteConference = MutableLiveData<Boolean>()
    val remoteConferenceSubject = MutableLiveData<String>()
    val isConferenceCall = MediatorLiveData<Boolean>()
    val conferenceParticipants = MutableLiveData<List<ConferenceInfoParticipantData>>()
    val conferenceParticipantsCountLabel = MutableLiveData<String>()

    val isOutgoing = MutableLiveData<Boolean>()
    val isIncoming = MutableLiveData<Boolean>()

    var chatRoom: ChatRoom? = null

    var contextMenuClickListener: CallContextMenuClickListener? = null

    private var timer: Timer? = null

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            if (call != this@CallData.call) return
            Log.i("[Call] State changed: $state")

            update()

            if (call.state == Call.State.UpdatedByRemote) {
                val remoteVideo = call.remoteParams?.isVideoEnabled ?: false
                val localVideo = call.currentParams.isVideoEnabled
                if (remoteVideo && !localVideo) {
                    // User has 30 secs to accept or decline call update
                    startVideoUpdateAcceptanceTimer()
                }
            } else if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                timer?.cancel()
            } else if (state == Call.State.StreamsRunning) {
                // Stop call update timer once user has accepted or declined call update
                timer?.cancel()
            }
        }

        override fun onRemoteRecording(call: Call, recording: Boolean) {
            Log.i("[Call] Remote recording changed: $recording")
            isRemotelyRecorded.value = recording
        }

        override fun onSnapshotTaken(call: Call, filePath: String) {
            Log.i("[Call] Snapshot taken: $filePath")
            val content = Factory.instance().createContent()
            content.filePath = filePath
            content.type = "image"
            content.subtype = "jpeg"
            content.name = filePath.substring(filePath.indexOf("/") + 1)

            scope.launch {
                if (Compatibility.addImageToMediaStore(coreContext.context, content)) {
                    Log.i("[Call] Adding snapshot ${content.name} to Media Store terminated")
                } else {
                    Log.e("[Call] Something went wrong while copying file to Media Store...")
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        call.addListener(listener)
        isRemotelyRecorded.value = call.remoteParams?.isRecording
        displayableAddress.value = LinphoneUtils.getDisplayableAddress(call.remoteAddress)

        isConferenceCall.addSource(remoteConferenceSubject) {
            isConferenceCall.value = remoteConferenceSubject.value.orEmpty().isNotEmpty() || conferenceParticipants.value.orEmpty().isNotEmpty()
        }
        isConferenceCall.addSource(conferenceParticipants) {
            isConferenceCall.value = remoteConferenceSubject.value.orEmpty().isNotEmpty() || conferenceParticipants.value.orEmpty().isNotEmpty()
        }

        update()
    }

    override fun destroy() {
        call.removeListener(listener)
        timer?.cancel()
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

    fun isActiveAndNotInConference(): Boolean {
        return isPaused.value == false && isRemotelyPaused.value == false && isInRemoteConference.value == false
    }

    private fun isCallPaused(): Boolean {
        return when (call.state) {
            Call.State.Paused, Call.State.Pausing -> true
            else -> false
        }
    }

    private fun isCallRemotelyPaused(): Boolean {
        return when (call.state) {
            Call.State.PausedByRemote -> {
                val conference = call.conference
                if (conference != null && conference.me.isFocus) {
                    Log.w("[Call] State is paused by remote but we are the focus of the conference, so considering call as active")
                    false
                } else {
                    true
                }
            }
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

        updateConferenceInfo()

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

    private fun updateConferenceInfo() {
        val conference = call.conference
        isInRemoteConference.value = conference != null
        if (conference != null) {
            Log.d("[Call] Found conference attached to call")
            remoteConferenceSubject.value = LinphoneUtils.getConferenceSubject(conference)
            Log.d("[Call] Found conference related to this call with subject [${remoteConferenceSubject.value}]")

            val participantsList = arrayListOf<ConferenceInfoParticipantData>()
            for (participant in conference.participantList) {
                val participantData = ConferenceInfoParticipantData(participant.address)
                participantsList.add(participantData)
            }

            conferenceParticipants.value = participantsList
            conferenceParticipantsCountLabel.value = coreContext.context.getString(R.string.conference_participants_title, participantsList.size)
        } else {
            val conferenceAddress = LinphoneUtils.getConferenceAddress(call)
            val conferenceInfo = if (conferenceAddress != null) coreContext.core.findConferenceInformationFromUri(conferenceAddress) else null
            if (conferenceInfo != null) {
                Log.d("[Call] Found matching conference info with subject: ${conferenceInfo.subject}")
                remoteConferenceSubject.value = conferenceInfo.subject

                val participantsList = arrayListOf<ConferenceInfoParticipantData>()
                for (participant in conferenceInfo.participants) {
                    val participantData = ConferenceInfoParticipantData(participant)
                    participantsList.add(participantData)
                }

                // Add organizer if not in participants list
                val organizer = conferenceInfo.organizer
                if (organizer != null) {
                    val found = participantsList.find { it.participant.weakEqual(organizer) }
                    if (found == null) {
                        val participantData = ConferenceInfoParticipantData(organizer)
                        participantsList.add(0, participantData)
                    }
                }

                conferenceParticipants.value = participantsList
                conferenceParticipantsCountLabel.value = coreContext.context.getString(R.string.conference_participants_title, participantsList.size)
            }
        }
    }

    private fun startVideoUpdateAcceptanceTimer() {
        timer?.cancel()

        timer = Timer("Call update timeout")
        timer?.schedule(
            object : TimerTask() {
                override fun run() {
                    // Decline call update
                    coreContext.videoUpdateRequestTimedOut(call)
                }
            },
            30000
        )
        Log.i("[Call] Starting 30 seconds timer to automatically decline video request")
    }
}

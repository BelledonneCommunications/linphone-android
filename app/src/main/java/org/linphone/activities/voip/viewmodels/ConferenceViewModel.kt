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
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.voip.ConferenceDisplayMode
import org.linphone.activities.voip.data.ConferenceParticipantData
import org.linphone.activities.voip.data.ConferenceParticipantDeviceData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConferenceViewModel : ViewModel() {
    val conferenceExists = MutableLiveData<Boolean>()
    val subject = MutableLiveData<String>()
    val isConferenceLocallyPaused = MutableLiveData<Boolean>()
    val isVideoConference = MutableLiveData<Boolean>()
    val isMeAdmin = MutableLiveData<Boolean>()

    val conference = MutableLiveData<Conference>()
    val conferenceCreationPending = MutableLiveData<Boolean>()
    val conferenceParticipants = MutableLiveData<List<ConferenceParticipantData>>()
    val conferenceParticipantDevices = MutableLiveData<List<ConferenceParticipantDeviceData>>()
    val conferenceDisplayMode = MutableLiveData<ConferenceDisplayMode>()
    val activeSpeakerConferenceParticipantDevices = MediatorLiveData<List<ConferenceParticipantDeviceData>>()

    val isRecording = MutableLiveData<Boolean>()
    val isRemotelyRecorded = MutableLiveData<Boolean>()

    val maxParticipantsForMosaicLayout = corePreferences.maxConferenceParticipantsForMosaicLayout

    val moreThanTwoParticipants = MutableLiveData<Boolean>()

    val speakingParticipant = MutableLiveData<ConferenceParticipantDeviceData>()
    val meParticipant = MutableLiveData<ConferenceParticipantDeviceData>()

    val participantAdminStatusChangedEvent: MutableLiveData<Event<ConferenceParticipantData>> by lazy {
        MutableLiveData<Event<ConferenceParticipantData>>()
    }

    val firstToJoinEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val allParticipantsLeftEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val secondParticipantJoinedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val moreThanTwoParticipantsJoinedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val conferenceListener = object : ConferenceListenerStub() {
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            Log.i("[Conference] Participant added: ${participant.address.asStringUriOnly()}")
            updateParticipantsList(conference)
        }

        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            Log.i("[Conference] Participant removed: ${participant.address.asStringUriOnly()}")
            updateParticipantsList(conference)

            if (conferenceParticipants.value.orEmpty().isEmpty()) {
                allParticipantsLeftEvent.value = Event(true)
            }
        }

        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference] Participant device added: ${participantDevice.address.asStringUriOnly()}")
            addParticipantDevice(participantDevice)

            if (conferenceParticipantDevices.value.orEmpty().size == 2) {
                secondParticipantJoinedEvent.value = Event(true)
            } else if (conferenceParticipantDevices.value.orEmpty().size == 3) {
                moreThanTwoParticipantsJoinedEvent.value = Event(true)
            }
        }

        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference] Participant device removed: ${participantDevice.address.asStringUriOnly()}")
            removeParticipantDevice(participantDevice)

            if (conferenceParticipantDevices.value.orEmpty().size == 2) {
                secondParticipantJoinedEvent.value = Event(true)
            }
        }

        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            Log.i("[Conference] Participant admin status changed [${participant.address.asStringUriOnly()}] is ${if (participant.isAdmin) "now admin" else "no longer admin"}")
            isMeAdmin.value = conference.me.isAdmin
            updateParticipantsList(conference)

            if (conference.me.address.weakEqual(participant.address)) {
                Log.i("[Conference] Found me participant [${participant.address.asStringUriOnly()}]")
                val participantData = ConferenceParticipantData(conference, participant)
                participantAdminStatusChangedEvent.value = Event(participantData)
                return
            }

            val participantData = conferenceParticipants.value.orEmpty().find { data -> data.participant.address.weakEqual(participant.address) }
            if (participantData != null) {
                participantAdminStatusChangedEvent.value = Event(participantData)
            } else {
                Log.w("[Conference] Failed to find participant [${participant.address.asStringUriOnly()}] in conferenceParticipants list")
            }
        }

        override fun onSubjectChanged(conference: Conference, subject: String) {
            Log.i("[Conference] Subject changed: $subject")
            this@ConferenceViewModel.subject.value = subject
        }

        override fun onParticipantDeviceStateChanged(
            conference: Conference,
            device: ParticipantDevice,
            state: ParticipantDeviceState
        ) {
            if (conference.isMe(device.address)) {
                when (state) {
                    ParticipantDeviceState.Present -> {
                        Log.i("[Conference] Entered conference")
                        isConferenceLocallyPaused.value = false
                    }
                    ParticipantDeviceState.OnHold -> {
                        Log.i("[Conference] Left conference")
                        isConferenceLocallyPaused.value = true
                    }
                    else -> {}
                }
            }
        }

        override fun onParticipantDeviceIsSpeakingChanged(
            conference: Conference,
            participantDevice: ParticipantDevice,
            isSpeaking: Boolean
        ) {
            Log.i("[Conference] Participant [${participantDevice.address.asStringUriOnly()}] is ${if (isSpeaking) "speaking" else "not speaking"}")
            if (isSpeaking) {
                val device = conferenceParticipantDevices.value.orEmpty().find {
                    it.participantDevice.address.weakEqual(participantDevice.address)
                }
                if (device != null && device != speakingParticipant.value) {
                    Log.i("[Conference] Found participant device")
                    if (!device.isMe) {
                        // TODO: FIXME: remove, this is a temporary workaround to not have your name
                        //  displayed above someone else video in active speaker layout when you talk
                        speakingParticipant.value = device!!
                    }
                } else if (device == null) {
                    Log.w("[Conference] Participant device [${participantDevice.address.asStringUriOnly()}] is speaking but couldn't find it in devices list")
                }
            }
        }

        override fun onStateChanged(conference: Conference, state: Conference.State) {
            Log.i("[Conference] State changed: $state")
            isVideoConference.value = conference.currentParams.isVideoEnabled

            when (state) {
                Conference.State.Created -> {
                    configureConference(conference)
                    conferenceCreationPending.value = false
                }
                Conference.State.TerminationPending -> {
                    terminateConference(conference)
                }
                else -> {}
            }
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State
        ) {
            Log.i("[Conference] Conference state changed: $state")
            if (state == Conference.State.Instantiated) {
                conferenceCreationPending.value = true
                initConference(conference)
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()
        activeSpeakerConferenceParticipantDevices.addSource(conferenceParticipantDevices) {
            activeSpeakerConferenceParticipantDevices.value = conferenceParticipantDevices.value.orEmpty().drop(1)
        }

        subject.value = AppUtils.getString(R.string.conference_default_title)

        var conference = coreContext.core.conference ?: coreContext.core.currentCall?.conference
        if (conference == null) {
            for (call in coreContext.core.calls) {
                if (call.conference != null) {
                    conference = call.conference
                    break
                }
            }
        }
        if (conference != null) {
            val state = conference.state
            Log.i("[Conference] Found an existing conference: $conference in state $state")
            if (state != Conference.State.TerminationPending && state != Conference.State.Terminated) {
                initConference(conference)
                if (state == Conference.State.Created) {
                    configureConference(conference)
                } else {
                    conferenceCreationPending.value = true
                }
            }
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        conference.value?.removeListener(conferenceListener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        activeSpeakerConferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)

        super.onCleared()
    }

    fun pauseConference() {
        Log.i("[Conference] Leaving conference temporarily")
        conference.value?.leave()
    }

    fun resumeConference() {
        Log.i("[Conference] Entering conference again")
        conference.value?.enter()
    }

    fun toggleRecording() {
        if (conference.value?.isRecording == true) {
            Log.i("[Conference] Stopping conference recording")
            conference.value?.stopRecording()
        } else {
            val path = LinphoneUtils.getRecordingFilePathForConference(conference.value?.currentParams?.subject)
            Log.i("[Conference] Starting recording in file $path")
            conference.value?.startRecording(path)
        }
        isRecording.value = conference.value?.isRecording
    }

    fun initConference(conference: Conference) {
        conferenceExists.value = true

        this@ConferenceViewModel.conference.value = conference
        conference.addListener(conferenceListener)

        isRecording.value = conference.isRecording
        subject.value = LinphoneUtils.getConferenceSubject(conference)

        updateConferenceLayout(conference)
    }

    fun configureConference(conference: Conference) {
        updateParticipantsList(conference)
        if (conferenceParticipants.value.orEmpty().isEmpty()) {
            firstToJoinEvent.value = Event(true)
        }

        updateParticipantsDevicesList(conference)
        if (conferenceParticipantDevices.value.orEmpty().size == 2) {
            secondParticipantJoinedEvent.value = Event(true)
        } else if (conferenceParticipantDevices.value.orEmpty().size > 2) {
            moreThanTwoParticipantsJoinedEvent.value = Event(true)
        }

        isConferenceLocallyPaused.value = !conference.isIn
        isMeAdmin.value = conference.me.isAdmin
        isVideoConference.value = conference.currentParams.isVideoEnabled
        subject.value = LinphoneUtils.getConferenceSubject(conference)

        updateConferenceLayout(conference)
    }

    fun addCallsToConference() {
        Log.i("[Conference] Trying to merge all calls into existing conference")
        val conf = conference.value
        conf ?: return

        for (call in coreContext.core.calls) {
            if (call.conference == null) {
                Log.i("[Conference] Adding call [$call] as participant for conference [$conf]")
                conf.addParticipant(call)
            }
        }
        if (!conf.isIn) {
            Log.i("[Conference] Conference was paused, resuming it")
            conf.enter()
        }
    }

    fun changeLayout(layout: ConferenceDisplayMode) {
        Log.i("[Conference] Trying to change conference layout to $layout")
        val conference = conference.value
        if (conference != null) {
            val call = conference.call
            if (call != null) {
                val params = call.core.createCallParams(call)
                if (params == null) {
                    Log.e("[Conference] Failed to create call params from conference call!")
                    return
                }

                params.isVideoEnabled = layout != ConferenceDisplayMode.AUDIO_ONLY
                params.conferenceVideoLayout = when (layout) {
                    ConferenceDisplayMode.GRID -> ConferenceLayout.Grid
                    else -> ConferenceLayout.ActiveSpeaker
                }
                call.update(params)

                conferenceDisplayMode.value = layout
                val list = sortDevicesDataList(conferenceParticipantDevices.value.orEmpty())
                conferenceParticipantDevices.value = list
            } else {
                Log.e("[Conference] Failed to get call from conference!")
            }
        } else {
            Log.e("[Conference] Conference is null in ConferenceViewModel")
        }
    }

    private fun updateConferenceLayout(conference: Conference) {
        val call = conference.call
        if (call == null) {
            Log.e("[Conference] Conference call is null!")
            return
        }

        val params = call.params
        conferenceDisplayMode.value = if (!params.isVideoEnabled) {
            ConferenceDisplayMode.AUDIO_ONLY
        } else {
            when (params.conferenceVideoLayout) {
                ConferenceLayout.Grid -> ConferenceDisplayMode.GRID
                else -> ConferenceDisplayMode.ACTIVE_SPEAKER
            }
        }

        val list = sortDevicesDataList(conferenceParticipantDevices.value.orEmpty())
        conferenceParticipantDevices.value = list

        Log.i("[Conference] Conference current layout is: ${conferenceDisplayMode.value}")
    }

    private fun terminateConference(conference: Conference) {
        conferenceExists.value = false
        isVideoConference.value = false

        conference.removeListener(conferenceListener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        activeSpeakerConferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)

        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()
    }

    private fun updateParticipantsList(conference: Conference) {
        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        val participants = arrayListOf<ConferenceParticipantData>()

        val participantsList = conference.participantList
        Log.i("[Conference] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            val participantData = ConferenceParticipantData(conference, participant)
            participants.add(participantData)
        }

        conferenceParticipants.value = participants
    }

    private fun updateParticipantsDevicesList(conference: Conference) {
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        activeSpeakerConferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        val devices = arrayListOf<ConferenceParticipantDeviceData>()

        val participantsList = conference.participantList
        Log.i("[Conference] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            for (device in participantDevices) {
                Log.i("[Conference] Participant device found: ${device.name} (${device.address.asStringUriOnly()})")
                val deviceData = ConferenceParticipantDeviceData(device, false)
                devices.add(deviceData)
            }
        }
        if (devices.isNotEmpty()) {
            speakingParticipant.value = devices.first()
        }

        for (device in conference.me.devices) {
            Log.i("[Conference] Participant device for myself found: ${device.name} (${device.address.asStringUriOnly()})")
            val deviceData = ConferenceParticipantDeviceData(device, true)
            devices.add(deviceData)
            meParticipant.value = deviceData
        }

        conferenceParticipantDevices.value = devices
        moreThanTwoParticipants.value = devices.size > 2
    }

    private fun addParticipantDevice(device: ParticipantDevice) {
        val devices = arrayListOf<ConferenceParticipantDeviceData>()
        devices.addAll(conferenceParticipantDevices.value.orEmpty())

        val existingDevice = devices.find {
            it.participantDevice.address.weakEqual(device.address)
        }
        if (existingDevice != null) {
            Log.e("[Conference] Participant is already in devices list: ${device.name} (${device.address.asStringUriOnly()})")
            return
        }

        Log.i("[Conference] New participant device found: ${device.name} (${device.address.asStringUriOnly()})")
        val deviceData = ConferenceParticipantDeviceData(device, false)
        devices.add(deviceData)

        val sortedDevices = sortDevicesDataList(devices)

        if (speakingParticipant.value == null) {
            speakingParticipant.value = deviceData
        }

        conferenceParticipantDevices.value = sortedDevices
        moreThanTwoParticipants.value = sortedDevices.size > 2
    }

    private fun removeParticipantDevice(device: ParticipantDevice) {
        val devices = arrayListOf<ConferenceParticipantDeviceData>()

        for (participantDevice in conferenceParticipantDevices.value.orEmpty()) {
            if (participantDevice.participantDevice.address.asStringUriOnly() != device.address.asStringUriOnly()) {
                devices.add(participantDevice)
            } else {
                participantDevice.destroy()
            }
        }
        if (devices.size == conferenceParticipantDevices.value.orEmpty().size) {
            Log.e("[Conference] Failed to remove participant device: ${device.name} (${device.address.asStringUriOnly()})")
        } else {
            Log.i("[Conference] Participant device removed: ${device.name} (${device.address.asStringUriOnly()})")
        }

        conferenceParticipantDevices.value = devices
        moreThanTwoParticipants.value = devices.size > 2
    }

    private fun sortDevicesDataList(devices: List<ConferenceParticipantDeviceData>): ArrayList<ConferenceParticipantDeviceData> {
        val sortedList = arrayListOf<ConferenceParticipantDeviceData>()
        sortedList.addAll(devices)

        val meDeviceData = sortedList.find {
            it.isMe
        }
        if (meDeviceData != null) {
            val index = sortedList.indexOf(meDeviceData)
            val expectedIndex = if (conferenceDisplayMode.value == ConferenceDisplayMode.ACTIVE_SPEAKER) {
                0
            } else {
                sortedList.size - 1
            }
            if (index != expectedIndex) {
                Log.i("[Conference] Me device data is at index $index, moving it to index $expectedIndex")
                sortedList.removeAt(index)
                sortedList.add(expectedIndex, meDeviceData)
            }
        }

        return sortedList
    }
}

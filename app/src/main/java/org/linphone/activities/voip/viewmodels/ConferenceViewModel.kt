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

    val twoOrMoreParticipants = MutableLiveData<Boolean>()
    val moreThanTwoParticipants = MutableLiveData<Boolean>()

    val speakingParticipantFound = MutableLiveData<Boolean>()
    val speakingParticipant = MutableLiveData<ConferenceParticipantDeviceData>()
    val speakingParticipantVideoEnabled = MutableLiveData<Boolean>()
    val meParticipant = MutableLiveData<ConferenceParticipantDeviceData>()

    val isBroadcast = MutableLiveData<Boolean>()
    val isMeListenerOnly = MutableLiveData<Boolean>()

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

    private var waitForNextStreamsRunningToUpdateLayout = false

    val reloadConferenceFragmentEvent: MutableLiveData<Event<Boolean>> by lazy {
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
        }

        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "[Conference] Participant device added: ${participantDevice.address.asStringUriOnly()}"
            )
            addParticipantDevice(conference, participantDevice)

            if (conferenceParticipantDevices.value.orEmpty().size == 2) {
                secondParticipantJoinedEvent.value = Event(true)
            } else if (conferenceParticipantDevices.value.orEmpty().size > 2) {
                moreThanTwoParticipantsJoinedEvent.value = Event(true)
            }
        }

        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "[Conference] Participant device removed: ${participantDevice.address.asStringUriOnly()}"
            )
            removeParticipantDevice(participantDevice)

            when (conferenceParticipantDevices.value.orEmpty().size) {
                1 -> {
                    speakingParticipant.value?.videoEnabled?.value = false
                    speakingParticipantVideoEnabled.value = false
                    allParticipantsLeftEvent.value = Event(true)
                }
                2 -> {
                    secondParticipantJoinedEvent.value = Event(true)
                }
                else -> {}
            }
        }

        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            Log.i(
                "[Conference] Participant admin status changed [${participant.address.asStringUriOnly()}] is ${if (participant.isAdmin) "now admin" else "no longer admin"}"
            )
            isMeAdmin.value = conference.me.isAdmin
            updateParticipantsList(conference)

            if (conference.me.address.weakEqual(participant.address)) {
                Log.i(
                    "[Conference] Found me participant [${participant.address.asStringUriOnly()}]"
                )
                val participantData = ConferenceParticipantData(conference, participant)
                participantAdminStatusChangedEvent.value = Event(participantData)
                return
            }

            val participantData = conferenceParticipants.value.orEmpty().find { data ->
                data.participant.address.weakEqual(
                    participant.address
                )
            }
            if (participantData != null) {
                participantAdminStatusChangedEvent.value = Event(participantData)
            } else {
                Log.w(
                    "[Conference] Failed to find participant [${participant.address.asStringUriOnly()}] in conferenceParticipants list"
                )
            }
        }

        override fun onSubjectChanged(conference: Conference, subject: String) {
            Log.i("[Conference] Subject changed: $subject")
            this@ConferenceViewModel.subject.value = subject
        }

        override fun onParticipantDeviceStateChanged(
            conference: Conference,
            device: ParticipantDevice,
            state: ParticipantDevice.State
        ) {
            if (conference.isMe(device.address)) {
                when (state) {
                    ParticipantDevice.State.Present -> {
                        Log.i("[Conference] Entered conference")
                        isConferenceLocallyPaused.value = false
                    }
                    ParticipantDevice.State.OnHold -> {
                        Log.i("[Conference] Left conference")
                        isConferenceLocallyPaused.value = true
                    }
                    else -> {}
                }
            } else {
                speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
            }
        }

        override fun onActiveSpeakerParticipantDevice(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "[Conference] Participant [${participantDevice.address.asStringUriOnly()}] is currently being displayed as active speaker"
            )
            val device = conferenceParticipantDevices.value.orEmpty().find {
                it.participantDevice.address.weakEqual(participantDevice.address)
            }

            if (device != null && device != speakingParticipant.value) {
                Log.i("[Conference] Found actively speaking participant device")
                speakingParticipant.value?.isActiveSpeaker?.value = false
                device.isActiveSpeaker.value = true
                speakingParticipant.value = device!!
                speakingParticipantFound.value = true
                speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
            } else if (device == null) {
                Log.w(
                    "[Conference] Participant device [${participantDevice.address.asStringUriOnly()}] is the active speaker but couldn't find it in devices list"
                )
            }
        }

        override fun onParticipantDeviceMediaAvailabilityChanged(
            conference: Conference,
            device: ParticipantDevice
        ) {
            speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
        }

        override fun onParticipantDeviceMediaCapabilityChanged(
            conference: Conference,
            device: ParticipantDevice
        ) {
            speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
        }

        override fun onStateChanged(conference: Conference, state: Conference.State) {
            Log.i("[Conference] State changed: $state")
            isVideoConference.value = conference.currentParams.isVideoEnabled && !corePreferences.disableVideo

            when (state) {
                Conference.State.Created -> {
                    configureConference(conference)
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
            } else if (state == Conference.State.Created) {
                if (conferenceCreationPending.value == true) {
                    conferenceCreationPending.value = false
                }
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (state == Call.State.StreamsRunning && waitForNextStreamsRunningToUpdateLayout) {
                waitForNextStreamsRunningToUpdateLayout = false
                reloadConferenceFragmentEvent.value = Event(true)
            }
            if (state == Call.State.StreamsRunning && call.conference?.isIn == true) {
                isConferenceLocallyPaused.value = false
                conferenceParticipantDevices.value?.forEach {
                    if (it.isMe) {
                        it.isInConference.value = true
                    }
                }
            }
        }
    }

    init {
        coreContext.core.addListener(listener)
        conferenceExists.value = false

        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()
        activeSpeakerConferenceParticipantDevices.addSource(conferenceParticipantDevices) {
            activeSpeakerConferenceParticipantDevices.value = conferenceParticipantDevices.value.orEmpty().drop(
                1
            )
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
                if (state == Conference.State.Instantiated) {
                    conferenceCreationPending.value = true
                } else if (state == Conference.State.Created) {
                    if (conferenceCreationPending.value == true) {
                        conferenceCreationPending.value = false
                    }
                    configureConference(conference)
                }
            }
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        conference.value?.removeListener(conferenceListener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(
            ConferenceParticipantDeviceData::destroy
        )
        activeSpeakerConferenceParticipantDevices.value.orEmpty().forEach(
            ConferenceParticipantDeviceData::destroy
        )

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
            val path = LinphoneUtils.getRecordingFilePathForConference(
                conference.value?.currentParams?.subject
            )
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

        isConferenceLocallyPaused.value = if (conference.call == null) false else !conference.isIn
        isMeAdmin.value = conference.me.isAdmin
        isVideoConference.value = conference.currentParams.isVideoEnabled && !corePreferences.disableVideo
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

    fun switchLayoutFromAudioOnlyToActiveSpeaker() {
        if (conferenceDisplayMode.value == ConferenceDisplayMode.AUDIO_ONLY) {
            Log.i(
                "[Conference] Trying to switch from AUDIO_ONLY to ACTIVE_SPEAKER and toggle video ON"
            )
            changeLayout(ConferenceDisplayMode.ACTIVE_SPEAKER, true)
            waitForNextStreamsRunningToUpdateLayout = true
        } else {
            Log.w(
                "[Conference] Can't switch from AUDIO_ONLY to ACTIVE_SPEAKER as current display mode isn't AUDIO_ONLY but ${conferenceDisplayMode.value}"
            )
        }
    }

    fun changeLayout(layout: ConferenceDisplayMode, forceSendingVideo: Boolean) {
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
                if (forceSendingVideo) {
                    Log.w("[Conference] Forcing video direction to SendRecv")
                    params.videoDirection = MediaDirection.SendRecv
                } else {
                    if (conferenceDisplayMode.value == ConferenceDisplayMode.AUDIO_ONLY) {
                        // Previous layout was audio only, make sure video isn't sent without user consent when switching layout
                        params.videoDirection = MediaDirection.RecvOnly
                    }
                    Log.i("[Conference] Video direction is ${params.videoDirection}")
                }

                params.conferenceVideoLayout = when (layout) {
                    ConferenceDisplayMode.GRID -> Conference.Layout.Grid
                    else -> Conference.Layout.ActiveSpeaker
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
        var videoDirection = MediaDirection.Inactive

        if (call == null) {
            conferenceDisplayMode.value = ConferenceDisplayMode.AUDIO_ONLY
            Log.w("[Conference] Call is null, assuming audio only layout for local conference")
        } else {
            val params = call.params
            videoDirection = params.videoDirection
            conferenceDisplayMode.value = if (!params.isVideoEnabled) {
                ConferenceDisplayMode.AUDIO_ONLY
            } else {
                when (params.conferenceVideoLayout) {
                    Conference.Layout.Grid -> ConferenceDisplayMode.GRID
                    else -> ConferenceDisplayMode.ACTIVE_SPEAKER
                }
            }
        }

        val list = sortDevicesDataList(conferenceParticipantDevices.value.orEmpty())
        conferenceParticipantDevices.value = list

        Log.i(
            "[Conference] Current layout is [${conferenceDisplayMode.value}], video direction is [$videoDirection]"
        )
    }

    private fun terminateConference(conference: Conference) {
        conferenceExists.value = false
        isVideoConference.value = false

        conference.removeListener(conferenceListener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(
            ConferenceParticipantDeviceData::destroy
        )
        activeSpeakerConferenceParticipantDevices.value.orEmpty().forEach(
            ConferenceParticipantDeviceData::destroy
        )

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
            Log.i(
                "[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)"
            )

            val participantData = ConferenceParticipantData(conference, participant)
            participants.add(participantData)
        }

        conferenceParticipants.value = participants
    }

    private fun updateParticipantsDevicesList(conference: Conference) {
        conferenceParticipantDevices.value.orEmpty().forEach(
            ConferenceParticipantDeviceData::destroy
        )
        activeSpeakerConferenceParticipantDevices.value.orEmpty().forEach(
            ConferenceParticipantDeviceData::destroy
        )
        val devices = arrayListOf<ConferenceParticipantDeviceData>()

        val participantsList = conference.participantList
        Log.i("[Conference] Conference has ${participantsList.size} participants")

        val activelySpeakingParticipantDevice = conference.activeSpeakerParticipantDevice
        var foundActivelySpeakingParticipantDevice = false
        speakingParticipantFound.value = false
        speakingParticipantVideoEnabled.value = false

        val conferenceInfo = conference.core.findConferenceInformationFromUri(
            conference.conferenceAddress
        )
        var allSpeaker = true
        for (info in conferenceInfo?.participantInfos.orEmpty()) {
            if (info.role == Participant.Role.Listener) {
                allSpeaker = false
            }
        }
        isBroadcast.value = !allSpeaker
        if (!allSpeaker) {
            Log.i(
                "[Conference] Not all participants are speaker, considering it is a broadcast"
            )
        }

        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i(
                "[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)"
            )

            for (device in participantDevices) {
                Log.i(
                    "[Conference] Participant device found: ${device.name} (${device.address.asStringUriOnly()})"
                )

                val info = conferenceInfo?.participantInfos?.find {
                    it.address.weakEqual(participant.address)
                }
                if (info != null) {
                    Log.i("[Conference] Participant role is [${info.role.name}]")
                    val listener = info.role == Participant.Role.Listener || info.role == Participant.Role.Unknown
                    if (listener) {
                        continue
                    }
                }

                val deviceData = ConferenceParticipantDeviceData(device, false)
                devices.add(deviceData)

                if (activelySpeakingParticipantDevice == device) {
                    Log.i(
                        "[Conference] Actively speaking participant device found: ${device.name} (${device.address.asStringUriOnly()})"
                    )
                    speakingParticipant.value = deviceData
                    deviceData.isActiveSpeaker.value = true
                    foundActivelySpeakingParticipantDevice = true
                    speakingParticipantFound.value = true
                    speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
                }
            }
        }

        if (!foundActivelySpeakingParticipantDevice && devices.isNotEmpty()) {
            Log.w(
                "[Conference] Actively speaking participant device not found, using first participant device available"
            )
            val deviceData = devices.first()
            speakingParticipant.value = deviceData
            deviceData.isActiveSpeaker.value = true
            speakingParticipantFound.value = true
            speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
        }

        for (device in conference.me.devices) {
            Log.i(
                "[Conference] Participant device for myself found: ${device.name} (${device.address.asStringUriOnly()})"
            )

            val info = conferenceInfo?.participantInfos?.find {
                it.address.weakEqual(device.address)
            }
            if (info != null) {
                Log.i("[Conference] Me role is [${info.role.name}]")
                val listener = info.role == Participant.Role.Listener || info.role == Participant.Role.Unknown
                isMeListenerOnly.value = listener
                if (listener) {
                    continue
                }
            }

            val deviceData = ConferenceParticipantDeviceData(device, true)
            devices.add(deviceData)
            meParticipant.value = deviceData
        }

        conferenceParticipantDevices.value = devices
        twoOrMoreParticipants.value = devices.size >= 2
        moreThanTwoParticipants.value = devices.size > 2
    }

    private fun addParticipantDevice(conference: Conference, device: ParticipantDevice) {
        val devices = arrayListOf<ConferenceParticipantDeviceData>()
        devices.addAll(conferenceParticipantDevices.value.orEmpty())

        val existingDevice = devices.find {
            it.participantDevice.address.weakEqual(device.address)
        }
        if (existingDevice != null) {
            Log.e(
                "[Conference] Participant is already in devices list: ${device.name} (${device.address.asStringUriOnly()})"
            )
            return
        }

        Log.i(
            "[Conference] New participant device found: ${device.name} (${device.address.asStringUriOnly()})"
        )

        val conferenceInfo = conference.core.findConferenceInformationFromUri(
            conference.conferenceAddress
        )
        val info = conferenceInfo?.participantInfos?.find {
            it.address.weakEqual(device.address)
        }
        if (info != null) {
            Log.i("[Conference] New participant role is [${info.role.name}]")
            val listener =
                info.role == Participant.Role.Listener || info.role == Participant.Role.Unknown
            if (listener) {
                return
            }
        }

        val deviceData = ConferenceParticipantDeviceData(device, false)
        devices.add(deviceData)

        val sortedDevices = sortDevicesDataList(devices)

        if (speakingParticipant.value == null || speakingParticipantFound.value == false) {
            speakingParticipant.value = deviceData
            deviceData.isActiveSpeaker.value = true
            speakingParticipantFound.value = true
            speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
        }

        conferenceParticipantDevices.value = sortedDevices
        twoOrMoreParticipants.value = sortedDevices.size >= 2
        moreThanTwoParticipants.value = sortedDevices.size > 2
    }

    private fun removeParticipantDevice(device: ParticipantDevice) {
        val devices = arrayListOf<ConferenceParticipantDeviceData>()
        var removedDeviceWasActiveSpeaker = false

        for (participantDevice in conferenceParticipantDevices.value.orEmpty()) {
            if (participantDevice.participantDevice.address.asStringUriOnly() != device.address.asStringUriOnly()) {
                devices.add(participantDevice)
            } else {
                if (speakingParticipant.value == participantDevice) {
                    Log.w(
                        "[Conference] Removed participant device was the actively speaking participant device"
                    )
                    removedDeviceWasActiveSpeaker = true
                }
                participantDevice.destroy()
            }
        }

        val devicesCount = devices.size
        if (devicesCount == conferenceParticipantDevices.value.orEmpty().size) {
            Log.e(
                "[Conference] Failed to remove participant device: ${device.name} (${device.address.asStringUriOnly()})"
            )
        }

        if (removedDeviceWasActiveSpeaker && devicesCount > 1) {
            Log.w(
                "[Conference] Updating actively speaking participant device using first one available"
            )
            // Using second device as first is ourselves
            val deviceData = devices[1]
            speakingParticipant.value = deviceData
            deviceData.isActiveSpeaker.value = true
            speakingParticipantFound.value = true
            speakingParticipantVideoEnabled.value = speakingParticipant.value?.isInConference?.value == true && speakingParticipant.value?.isSendingVideo?.value == true
        }

        conferenceParticipantDevices.value = devices
        twoOrMoreParticipants.value = devicesCount >= 2
        moreThanTwoParticipants.value = devicesCount > 2
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
                Log.i(
                    "[Conference] Me device data is at index $index, moving it to index $expectedIndex"
                )
                sortedList.removeAt(index)
                sortedList.add(expectedIndex, meDeviceData)
            }
        }

        return sortedList
    }
}

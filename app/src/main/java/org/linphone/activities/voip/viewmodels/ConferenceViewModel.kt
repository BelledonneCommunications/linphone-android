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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.flexbox.FlexDirection
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.voip.data.ConferenceParticipantData
import org.linphone.activities.voip.data.ConferenceParticipantDeviceData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class ConferenceViewModel : ViewModel() {
    val isConferencePaused = MutableLiveData<Boolean>()

    val isMeConferenceFocus = MutableLiveData<Boolean>()

    val conferenceAddress = MutableLiveData<Address>()

    val conferenceParticipants = MutableLiveData<List<ConferenceParticipantData>>()
    val conferenceParticipantDevices = MutableLiveData<List<ConferenceParticipantDeviceData>>()

    val flexboxLayoutDirection = MutableLiveData<Int>()

    val isInConference = MutableLiveData<Boolean>()

    val isVideoConference = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()
    val isRemotelyRecorded = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    private val conferenceListener = object : ConferenceListenerStub() {
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            if (conference.isMe(participant.address)) {
                Log.i("[Conference] Entered conference")
                isConferencePaused.value = false
            } else {
                Log.i("[Conference] Participant added")
                updateParticipantsList(conference)
            }
        }

        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            if (conference.isMe(participant.address)) {
                Log.i("[Conference] Left conference")
                isConferencePaused.value = true
            } else {
                Log.i("[Conference] Participant removed")
                updateParticipantsList(conference)
            }
        }

        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference] Participant device added")
            updateParticipantsDevicesList(conference)
        }

        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference] Participant device removed")
            updateParticipantsDevicesList(conference)
        }

        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            Log.i("[Conference] Participant admin status changed")
            updateParticipantsList(conference)
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State
        ) {
            Log.i("[Conference] Conference state changed: $state")
            isConferencePaused.value = !conference.isIn
            isVideoConference.value = coreContext.core.conference?.currentParams?.isVideoEnabled

            if (state == Conference.State.Instantiated) {
                conference.addListener(conferenceListener)
            } else if (state == Conference.State.Created) {
                updateParticipantsList(conference)
                isMeConferenceFocus.value = conference.me.isFocus
                conferenceAddress.value = conference.conferenceAddress
                subject.value = if (conference.subject.isNullOrEmpty()) {
                    if (conference.me.isFocus) {
                        AppUtils.getString(R.string.conference_local_title)
                    } else {
                        AppUtils.getString(R.string.conference_default_title)
                    }
                } else {
                    conference.subject
                }
            } else if (state == Conference.State.Terminated || state == Conference.State.TerminationFailed) {
                isInConference.value = false
                isVideoConference.value = false

                conference.removeListener(conferenceListener)

                conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
                conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
                conferenceParticipants.value = arrayListOf()
                conferenceParticipantDevices.value = arrayListOf()
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        isConferencePaused.value = coreContext.core.conference?.isIn != true
        isMeConferenceFocus.value = false

        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()

        isInConference.value = false
        isVideoConference.value = coreContext.core.conference?.currentParams?.isVideoEnabled

        flexboxLayoutDirection.value = FlexDirection.COLUMN

        subject.value = AppUtils.getString(R.string.conference_default_title)

        val conference = coreContext.core.conference
        if (conference != null) {
            conference.addListener(conferenceListener)
            isMeConferenceFocus.value = conference.me.isFocus
            updateParticipantsList(conference)
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)

        super.onCleared()
    }

    fun pauseConference() {
        val defaultProxyConfig = coreContext.core.defaultProxyConfig
        val localAddress = defaultProxyConfig?.identityAddress
        val participants = arrayOf<Address>()
        val remoteConference = coreContext.core.searchConference(null, localAddress, conferenceAddress.value, participants)
        val localConference = coreContext.core.searchConference(null, conferenceAddress.value, conferenceAddress.value, participants)
        val conference = remoteConference ?: localConference

        if (conference != null) {
            Log.i("[Conference] Leaving conference with address ${conferenceAddress.value?.asStringUriOnly()} temporarily")
            conference.leave()
        } else {
            Log.w("[Conference] Unable to find conference with address ${conferenceAddress.value?.asStringUriOnly()}")
        }
    }

    fun resumeConference() {
        val defaultProxyConfig = coreContext.core.defaultProxyConfig
        val localAddress = defaultProxyConfig?.identityAddress
        val participants = arrayOf<Address>()
        val remoteConference = coreContext.core.searchConference(null, localAddress, conferenceAddress.value, participants)
        val localConference = coreContext.core.searchConference(null, conferenceAddress.value, conferenceAddress.value, participants)
        val conference = remoteConference ?: localConference

        if (conference != null) {
            Log.i("[Conference] Entering again conference with address ${conferenceAddress.value?.asStringUriOnly()}")
            conference.enter()
        } else {
            Log.w("[Conference] Unable to find conference with address ${conferenceAddress.value?.asStringUriOnly()}")
        }
    }

    fun toggleRecording() {
        val conference = coreContext.core.conference
        if (conference == null) {
            Log.e("[Conference] Failed to find conference!")
            return
        }

        if (conference.isRecording) {
            conference.stopRecording()
        } else {
            val path = LinphoneUtils.getRecordingFilePathForConference()
            Log.i("[Conference] Starting recording in file $path")
            conference.startRecording(path)
        }
        isRecording.value = conference.isRecording
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

        flexboxLayoutDirection.value = if (participants.size > 4) {
            FlexDirection.ROW
        } else {
            FlexDirection.COLUMN
        }

        conferenceParticipants.value = participants
        isInConference.value = participants.isNotEmpty()
    }

    private fun updateParticipantsDevicesList(conference: Conference) {
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        val devices = arrayListOf<ConferenceParticipantDeviceData>()

        val participantsList = conference.participantList
        Log.i("[Conference] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            if (!conference.isMe(participant.address)) {
                for (device in participantDevices) {
                    Log.i("[Conference] Participant device found: ${device.name} (${device.address.asStringUriOnly()})")
                    val deviceData = ConferenceParticipantDeviceData(device)
                    devices.add(deviceData)
                }
            } else {
                Log.i("[Conference] Not adding our own devices...")
            }
        }

        conferenceParticipantDevices.value = devices
    }
}

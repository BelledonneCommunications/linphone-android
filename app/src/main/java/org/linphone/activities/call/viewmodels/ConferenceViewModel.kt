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
package org.linphone.activities.call.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.call.data.ConferenceParticipantData
import org.linphone.activities.call.data.ConferenceParticipantDeviceData
import org.linphone.core.*
import org.linphone.core.tools.Log

class ConferenceViewModel : ViewModel() {
    val isConferencePaused = MutableLiveData<Boolean>()

    val isMeConferenceFocus = MutableLiveData<Boolean>()

    val conferenceAddress = MutableLiveData<Address>()

    val conferenceParticipants = MutableLiveData<List<ConferenceParticipantData>>()
    val conferenceParticipantDevices = MutableLiveData<List<ConferenceParticipantDeviceData>>()

    val isInConference = MutableLiveData<Boolean>()

    val isVideoConference = MutableLiveData<Boolean>()

    private val conferenceListener = object : ConferenceListenerStub() {
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            if (conference.isMe(participant.address)) {
                Log.i("[Conference VM] Entered conference")
                isConferencePaused.value = false
            } else {
                Log.i("[Conference VM] Participant added")
                updateParticipantsList(conference)
            }
        }

        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            if (conference.isMe(participant.address)) {
                Log.i("[Conference VM] Left conference")
                isConferencePaused.value = true
            } else {
                Log.i("[Conference VM] Participant removed")
                updateParticipantsList(conference)
            }
        }

        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference VM] Participant device added")
            updateParticipantsDevicesList(conference)
        }

        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference VM] Participant device removed")
            updateParticipantsDevicesList(conference)
        }

        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            Log.i("[Conference VM] Participant admin status changed")
            updateParticipantsList(conference)
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State
        ) {
            Log.i("[Conference VM] Conference state changed: $state")
            isConferencePaused.value = !conference.isIn
            isVideoConference.value = conference.currentParams?.isVideoEnabled

            if (state == Conference.State.Instantiated) {
                conference.addListener(conferenceListener)
            } else if (state == Conference.State.Created) {
                updateParticipantsList(conference)
                isMeConferenceFocus.value = conference.me.isFocus
                conferenceAddress.value = conference.conferenceAddress
            } else if (state == Conference.State.Terminated || state == Conference.State.TerminationFailed) {
                isInConference.value = false
                isVideoConference.value = false
                conference.removeListener(conferenceListener)
                conferenceParticipants.value = arrayListOf()
                conferenceParticipantDevices.value = arrayListOf()
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        isConferencePaused.value = false
        isMeConferenceFocus.value = false
        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()
        isInConference.value = false

        val conference = coreContext.core.conference
        if (conference != null) {
            conference.addListener(conferenceListener)
            isConferencePaused.value = !conference.isIn
            isMeConferenceFocus.value = conference.me.isFocus
            isVideoConference.value = conference.currentParams?.isVideoEnabled
            updateParticipantsList(conference)
            updateParticipantsDevicesList(conference)
        } else {
// FIXME: This is a temporary workaround due to the fact that the OutgoingCallActivity is terminated way after the call reaching the StreamsRunning state and the CallActivity starting at that point
// If the call is put on conference by a server, then the onCallSessionStateChange callback creates the remote conference when transitioning from state Connected to StreamsRunning because the 200 OK contains a contact with a conference ID and isfocus
           val conference = coreContext.core.currentCall?.conference
        Log.i("[Conference VM] DEBUG DEBUG Found conference in current call $conference")
           if (conference != null) {
              conference.addListener(conferenceListener)
              isMeConferenceFocus.value = conference.me.isFocus
              conferenceAddress.value = conference.conferenceAddress
              isConferencePaused.value = !conference.isIn
              isVideoConference.value = conference.currentParams?.isVideoEnabled
              updateParticipantsList(conference)
              updateParticipantsDevicesList(conference)
           }
        }
        Log.i("[Conference VM] DEBUG DEBUG Initialize conference with address ${conferenceAddress.value?.asStringUriOnly()} is in conference ${isInConference.value}")
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
            Log.i("[Conference VM] Leaving conference with address ${conferenceAddress.value?.asStringUriOnly()} temporarily")
            conference.leave()
        } else {
            Log.w("[Conference VM] Unable to find conference with address ${conferenceAddress.value?.asStringUriOnly()}")
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
            Log.i("[Conference VM] Entering again conference with address ${conferenceAddress.value?.asStringUriOnly()}")
            conference.enter()
        } else {
            Log.w("[Conference VM] Unable to find conference with address ${conferenceAddress.value?.asStringUriOnly()}")
        }
    }

    private fun updateParticipantsList(conference: Conference) {
        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        val participants = arrayListOf<ConferenceParticipantData>()

        val participantsList = conference.participantList
        Log.i("[Conference VM] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference VM] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            val participantData = ConferenceParticipantData(conference, participant)
            participants.add(participantData)
        }

        conferenceParticipants.value = participants
        isInConference.value = participants.isNotEmpty()
    }

    private fun updateParticipantsDevicesList(conference: Conference) {
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        val devices = arrayListOf<ConferenceParticipantDeviceData>()

        val participantsList = conference.participantList
        Log.i("[Conference VM] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference VM] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            if (!conference.isMe(participant.address)) {
                for (device in participantDevices) {
                    Log.i("[Conference VM] Participant device found: ${device.name} (${device.address.asStringUriOnly()})")
                    val deviceData = ConferenceParticipantDeviceData(device)
                    devices.add(deviceData)
                }
            } else {
                Log.i("[Conference VM] Not adding our own devices...")
            }
        }

        conferenceParticipantDevices.value = devices
    }
}

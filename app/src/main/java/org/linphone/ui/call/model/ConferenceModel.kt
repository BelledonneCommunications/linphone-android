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
package org.linphone.ui.call.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.Call
import org.linphone.core.Conference
import org.linphone.core.ConferenceListenerStub
import org.linphone.core.Participant
import org.linphone.core.ParticipantDevice
import org.linphone.core.tools.Log

class ConferenceModel {
    companion object {
        private const val TAG = "[Conference ViewModel]"
    }

    val subject = MutableLiveData<String>()

    val sipUri = MutableLiveData<String>()

    val participantDevices = MutableLiveData<ArrayList<ConferenceParticipantDeviceModel>>()

    private lateinit var conference: Conference

    val isCurrentCallInConference = MutableLiveData<Boolean>()

    private val conferenceListener = object : ConferenceListenerStub() {
        @WorkerThread
        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "$TAG Participant device added: ${participantDevice.address.asStringUriOnly()}"
            )

            val list = arrayListOf<ConferenceParticipantDeviceModel>()
            list.addAll(participantDevices.value.orEmpty())

            val newModel = ConferenceParticipantDeviceModel(participantDevice)
            list.add(newModel)

            participantDevices.postValue(sortParticipantDevicesList(list))
        }

        @WorkerThread
        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "$TAG Participant device removed: ${participantDevice.address.asStringUriOnly()}"
            )

            val list = arrayListOf<ConferenceParticipantDeviceModel>()
            list.addAll(participantDevices.value.orEmpty())

            val toRemove = list.find {
                participantDevice.address.weakEqual(it.device.address)
            }
            if (toRemove != null) {
                toRemove.destroy()
                list.remove(toRemove)
            }

            participantDevices.postValue(list)
        }

        @WorkerThread
        override fun onParticipantDeviceStateChanged(
            conference: Conference,
            device: ParticipantDevice,
            state: ParticipantDevice.State
        ) {
            Log.i(
                "$TAG Participant device [${device.address.asStringUriOnly()}] state changed [$state]"
            )
        }

        @WorkerThread
        override fun onStateChanged(conference: Conference, state: Conference.State) {
            Log.i("$TAG State changed [$state]")
            if (conference.state == Conference.State.Created) {
                computeParticipantsDevices()
            }
        }
    }

    @WorkerThread
    fun destroy() {
        isCurrentCallInConference.postValue(false)
        if (::conference.isInitialized) {
            conference.removeListener(conferenceListener)
            participantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceModel::destroy)
        }
    }

    @WorkerThread
    fun configureFromCall(call: Call) {
        val conf = call.conference ?: return
        if (::conference.isInitialized) {
            conference.removeListener(conferenceListener)
        }

        isCurrentCallInConference.postValue(true)
        conference = conf
        conference.addListener(conferenceListener)

        Log.i(
            "$TAG Configuring conference with subject [${conference.subject}] from call [${call.callLog.callId}]"
        )
        sipUri.postValue(conference.conferenceAddress.asStringUriOnly())
        subject.postValue(conference.subject)

        if (conference.state == Conference.State.Created) {
            computeParticipantsDevices()
        }
    }

    @WorkerThread
    private fun computeParticipantsDevices() {
        participantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceModel::destroy)
        val list = arrayListOf<ConferenceParticipantDeviceModel>()

        val participants = conference.participantList
        Log.i("$TAG [${participants.size}] participant in conference")

        for (participant in participants) {
            val devices = participant.devices
            val role = participant.role

            Log.i(
                "$TAG Participant [${participant.address.asStringUriOnly()}] has [${devices.size}] devices and role [${role.name}]"
            )
            if (role == Participant.Role.Listener) {
                continue
            }

            for (device in participant.devices) {
                val model = ConferenceParticipantDeviceModel(device)
                list.add(model)
            }
        }
        Log.i(
            "$TAG [${list.size}] participant devices will be displayed (not counting ourselves)"
        )

        val ourDevices = conference.me.devices
        Log.i("$TAG We have [${ourDevices.size}] devices")
        for (device in ourDevices) {
            val model = ConferenceParticipantDeviceModel(device, true)
            list.add(model)
        }

        participantDevices.postValue(sortParticipantDevicesList(list))
    }

    private fun sortParticipantDevicesList(devices: List<ConferenceParticipantDeviceModel>): ArrayList<ConferenceParticipantDeviceModel> {
        val sortedList = arrayListOf<ConferenceParticipantDeviceModel>()
        sortedList.addAll(devices)

        val meDeviceData = sortedList.find {
            it.isMe
        }
        if (meDeviceData != null) {
            val index = sortedList.indexOf(meDeviceData)
            val expectedIndex = sortedList.size - 1
            if (index != expectedIndex) {
                Log.i(
                    "$TAG Me device data is at index $index, moving it to index $expectedIndex"
                )
                sortedList.removeAt(index)
                sortedList.add(expectedIndex, meDeviceData)
            }
        }

        return sortedList
    }
}

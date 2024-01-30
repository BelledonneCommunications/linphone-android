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

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Conference
import org.linphone.core.ConferenceListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.Participant
import org.linphone.core.ParticipantDevice
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class ConferenceModel {
    companion object {
        private const val TAG = "[Conference ViewModel]"

        const val AUDIO_ONLY_LAYOUT = -1
        const val GRID_LAYOUT = 0 // Conference.Layout.Grid
        const val ACTIVE_SPEAKER_LAYOUT = 1 // Conference.Layout.ActiveSpeaker
    }

    val subject = MutableLiveData<String>()

    val sipUri = MutableLiveData<String>()

    val participants = MutableLiveData<ArrayList<ConferenceParticipantModel>>()

    val participantDevices = MutableLiveData<ArrayList<ConferenceParticipantDeviceModel>>()

    val participantsLabel = MutableLiveData<String>()

    val activeSpeaker = MutableLiveData<ConferenceParticipantDeviceModel>()

    val isCurrentCallInConference = MutableLiveData<Boolean>()

    val conferenceLayout = MutableLiveData<Int>()

    val showLayoutMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var conference: Conference

    private val conferenceListener = object : ConferenceListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            Log.i(
                "$TAG Participant added: ${participant.address.asStringUriOnly()}"
            )
            addParticipant(participant)
        }

        @WorkerThread
        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            Log.i(
                "$TAG Participant removed: ${participant.address.asStringUriOnly()}"
            )
            removeParticipant(participant)
        }

        @WorkerThread
        override fun onActiveSpeakerParticipantDevice(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            activeSpeaker.value?.isActiveSpeaker?.postValue(false)

            val found = participantDevices.value.orEmpty().find {
                it.device == participantDevice
            }
            if (found != null) {
                Log.i("$TAG Newly active speaker participant is [${found.name}]")
                found.isActiveSpeaker.postValue(true)
                activeSpeaker.postValue(found)
            } else {
                Log.i("$TAG Failed to find actively speaking participant...")
                val model = ConferenceParticipantDeviceModel(participantDevice)
                model.isActiveSpeaker.postValue(true)
                activeSpeaker.postValue(model)
            }
        }

        @WorkerThread
        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            val newAdminStatus = participant.isAdmin
            Log.i(
                "$TAG Participant [${participant.address.asStringUriOnly()}] is [${if (newAdminStatus) "now admin" else "no longer admin"}]"
            )
            val participantModel = participants.value.orEmpty().find {
                it.participant.address.weakEqual(participant.address)
            }
            participantModel?.isAdmin?.postValue(newAdminStatus)
        }

        @WorkerThread
        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "$TAG Participant device added: ${participantDevice.address.asStringUriOnly()}"
            )
            addParticipantDevice(participantDevice)
        }

        @WorkerThread
        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "$TAG Participant device removed: ${participantDevice.address.asStringUriOnly()}"
            )
            removeParticipantDevice(participantDevice)
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
                computeParticipants()
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
            computeParticipants()
        }

        val currentLayout = getCurrentLayout(call)
        conferenceLayout.postValue(currentLayout)
    }

    @UiThread
    fun showLayoutMenu() {
        showLayoutMenuEvent.value = Event(true)
    }

    @UiThread
    fun changeLayout(newLayout: Int) {
        coreContext.postOnCoreThread {
            val call = conference.call
            if (call != null) {
                val params = call.core.createCallParams(call)
                if (params != null) {
                    val currentLayout = getCurrentLayout(call)
                    if (currentLayout != newLayout) {
                        when (newLayout) {
                            AUDIO_ONLY_LAYOUT -> {
                                Log.i("$TAG Changing conference layout to [Audio Only]")
                                params.isVideoEnabled = false
                            }
                            ACTIVE_SPEAKER_LAYOUT -> {
                                Log.i("$TAG Changing conference layout to [Active Speaker]")
                                params.conferenceVideoLayout = Conference.Layout.ActiveSpeaker
                            }
                            GRID_LAYOUT -> {
                                Log.i("$TAG Changing conference layout to [Grid]")
                                params.conferenceVideoLayout = Conference.Layout.Grid
                            }
                        }

                        if (currentLayout == AUDIO_ONLY_LAYOUT) {
                            // Previous layout was audio only, make sure video isn't sent without user consent when switching layout
                            Log.i(
                                "$TAG Previous layout was [Audio Only], enabling video but in receive only direction"
                            )
                            params.isVideoEnabled = true
                            params.videoDirection = MediaDirection.RecvOnly
                        }

                        Log.i("$TAG Updating conference's call params")
                        call.update(params)
                        conferenceLayout.postValue(newLayout)
                    } else {
                        Log.w(
                            "$TAG The conference is already using selected layout, aborting layout change"
                        )
                    }
                } else {
                    Log.e("$TAG Failed to create call params, aborting layout change")
                }
            } else {
                Log.e("$TAG Failed to get call from conference, aborting layout change")
            }
        }
    }

    @WorkerThread
    private fun getCurrentLayout(call: Call): Int {
        // DO NOT USE call.currentParams, information won't be reliable !
        return if (!call.params.isVideoEnabled) {
            Log.i("$TAG Current conference layout is [Audio Only]")
            AUDIO_ONLY_LAYOUT
        } else {
            when (val layout = call.params.conferenceVideoLayout) {
                Conference.Layout.Grid -> {
                    Log.i("$TAG Current conference layout is [Grid]")
                    GRID_LAYOUT
                }
                Conference.Layout.ActiveSpeaker -> {
                    Log.i("$TAG Current conference layout is [Active Speaker]")
                    ACTIVE_SPEAKER_LAYOUT
                }
                else -> {
                    Log.e("$TAG Unexpected conference layout value [$layout]")
                    -2
                }
            }
        }
    }

    @WorkerThread
    private fun computeParticipants() {
        participants.value.orEmpty().forEach(ConferenceParticipantModel::destroy)
        participantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceModel::destroy)

        val participantsList = arrayListOf<ConferenceParticipantModel>()
        val devicesList = arrayListOf<ConferenceParticipantDeviceModel>()

        val conferenceParticipants = conference.participantList
        Log.i("$TAG [${conferenceParticipants.size}] participant in conference")

        for (participant in conferenceParticipants) {
            val devices = participant.devices
            val role = participant.role

            Log.i(
                "$TAG Participant [${participant.address.asStringUriOnly()}] has [${devices.size}] devices and role [${role.name}]"
            )
            val participantModel = ConferenceParticipantModel(participant)
            participantsList.add(participantModel)

            if (role == Participant.Role.Listener) {
                continue
            }

            for (device in participant.devices) {
                val model = ConferenceParticipantDeviceModel(device)
                devicesList.add(model)

                if (device == conference.activeSpeakerParticipantDevice) {
                    Log.i("$TAG Using participant is [${model.name}] as current active speaker")
                    model.isActiveSpeaker.postValue(true)
                    activeSpeaker.postValue(model)
                }
            }
        }
        Log.i(
            "$TAG [${devicesList.size}] participant devices for [${participantsList.size}] participants will be displayed (not counting ourselves)"
        )

        val meParticipant = conference.me
        val meParticipantModel = ConferenceParticipantModel(meParticipant)
        participantsList.add(meParticipantModel)

        val ourDevices = conference.me.devices
        Log.i("$TAG We have [${ourDevices.size}] devices")
        for (device in ourDevices) {
            val model = ConferenceParticipantDeviceModel(device, true)
            devicesList.add(model)
        }

        participantDevices.postValue(sortParticipantDevicesList(devicesList))
        participants.postValue(participantsList)
        participantsLabel.postValue(
            AppUtils.getStringWithPlural(
                R.plurals.conference_participants_list_title,
                participantsList.size,
                "${participantsList.size}"
            )
        )
    }

    @WorkerThread
    private fun sortParticipantDevicesList(devices: List<ConferenceParticipantDeviceModel>): ArrayList<ConferenceParticipantDeviceModel> {
        val sortedList = arrayListOf<ConferenceParticipantDeviceModel>()
        sortedList.addAll(devices)

        val meDeviceData = sortedList.find {
            it.isMe
        }
        if (meDeviceData != null) {
            val index = sortedList.indexOf(meDeviceData)
            val expectedIndex = if (conferenceLayout.value == ACTIVE_SPEAKER_LAYOUT) {
                Log.i(
                    "$TAG Current conference layout is [Active Speaker], expecting our device to be at the beginning of the list"
                )
                0
            } else {
                Log.i(
                    "$TAG Current conference layout isn't [Active Speaker], expecting our device to be at the end of the list"
                )
                sortedList.size - 1
            }
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

    @WorkerThread
    private fun addParticipant(participant: Participant) {
        val list = arrayListOf<ConferenceParticipantModel>()
        list.addAll(participants.value.orEmpty())

        val newModel = ConferenceParticipantModel(participant)
        list.add(newModel)

        participants.postValue(list)
        participantsLabel.postValue(
            AppUtils.getStringWithPlural(
                R.plurals.conference_participants_list_title,
                list.size,
                "${list.size}"
            )
        )
    }

    @WorkerThread
    private fun addParticipantDevice(participantDevice: ParticipantDevice) {
        val list = arrayListOf<ConferenceParticipantDeviceModel>()
        list.addAll(participantDevices.value.orEmpty())

        val newModel = ConferenceParticipantDeviceModel(participantDevice)
        list.add(newModel)

        participantDevices.postValue(sortParticipantDevicesList(list))
    }

    @WorkerThread
    private fun removeParticipant(participant: Participant) {
        val list = arrayListOf<ConferenceParticipantModel>()
        list.addAll(participants.value.orEmpty())

        val toRemove = list.find {
            participant.address.weakEqual(it.participant.address)
        }
        if (toRemove != null) {
            toRemove.destroy()
            list.remove(toRemove)
        }

        participants.postValue(list)
        participantsLabel.postValue(
            AppUtils.getStringWithPlural(
                R.plurals.conference_participants_list_title,
                list.size,
                "${list.size}"
            )
        )
    }

    @WorkerThread
    private fun removeParticipantDevice(participantDevice: ParticipantDevice) {
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
}

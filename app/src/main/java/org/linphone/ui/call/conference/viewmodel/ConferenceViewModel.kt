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
package org.linphone.ui.call.conference.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Conference
import org.linphone.core.ConferenceListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.Participant
import org.linphone.core.ParticipantDevice
import org.linphone.core.StreamType
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.call.conference.model.ConferenceParticipantDeviceModel
import org.linphone.ui.call.conference.model.ConferenceParticipantModel
import org.linphone.ui.call.conference.view.GridBoxLayout
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class ConferenceViewModel
    @UiThread
    constructor() : GenericViewModel() {
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

    val isScreenSharing = MutableLiveData<Boolean>()

    val isPaused = MutableLiveData<Boolean>()

    val isMeParticipantSendingVideo = MutableLiveData<Boolean>()

    val isMeAdmin = MutableLiveData<Boolean>()

    val isConversationAvailable = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val firstParticipantOtherThanOurselvesJoinedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showLayoutMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val removeParticipantEvent: MutableLiveData<Event<Pair<String, Participant>>> by lazy {
        MutableLiveData<Event<Pair<String, Participant>>>()
    }

    val goToConversationEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    private lateinit var conference: Conference

    private val conferenceListener = object : ConferenceListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            Log.i(
                "$TAG Participant added: ${participant.address.asStringUriOnly()}"
            )
            addParticipant(participant)

            if (conference.participantList.size == 1) { // we do not count
                Log.i("$TAG First participant other than ourselves joined the conference")
                firstParticipantOtherThanOurselvesJoinedEvent.postValue(Event(true))
            }
        }

        @WorkerThread
        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            Log.i(
                "$TAG Participant removed: ${participant.address.asStringUriOnly()}"
            )
            removeParticipant(participant)
        }

        @WorkerThread
        override fun onParticipantDeviceMediaCapabilityChanged(
            conference: Conference,
            device: ParticipantDevice
        ) {
            if (conference.isMe(device.address)) {
                val direction = device.getStreamCapability(StreamType.Video)
                val sendingVideo = direction == MediaDirection.SendRecv || direction == MediaDirection.SendOnly
                localVideoStreamToggled(sendingVideo)
            }
        }

        @WorkerThread
        override fun onActiveSpeakerParticipantDevice(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            activeSpeaker.value?.isActiveSpeaker?.postValue(false)

            val found = participantDevices.value.orEmpty().find {
                it.device.address.equal(participantDevice.address)
            }
            if (found != null) {
                Log.i("$TAG Newly active speaker participant is [${found.name}]")
                found.isActiveSpeaker.postValue(true)
                activeSpeaker.postValue(found!!)
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
            // Only recompute participants list
            computeParticipants(true)
        }

        @WorkerThread
        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i(
                "$TAG Participant device added: ${participantDevice.address.asStringUriOnly()}"
            )

            // Since we do not compute our own devices until another participant joins,
            // We have to do it when someone else joins
            if (participantDevices.value.orEmpty().isEmpty()) {
                val list = arrayListOf<ConferenceParticipantDeviceModel>()
                val ourDevices = conference.me.devices
                Log.i("$TAG We have [${ourDevices.size}] devices, now it's time to add them")
                for (device in ourDevices) {
                    val model = ConferenceParticipantDeviceModel(device, true)
                    list.add(model)
                }

                val newModel = ConferenceParticipantDeviceModel(participantDevice)
                list.add(newModel)
                participantDevices.postValue(sortParticipantDevicesList(list))
            } else {
                addParticipantDevice(participantDevice)
            }
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
        override fun onParticipantDeviceScreenSharingChanged(
            conference: Conference,
            device: ParticipantDevice,
            enabled: Boolean
        ) {
            Log.i(
                "$TAG Participant device [${device.address.asStringUriOnly()}] is ${if (enabled) "sharing it's screen" else "no longer sharing it's screen"}"
            )
            isScreenSharing.postValue(enabled)
            if (enabled) {
                val call = conference.call
                if (call != null) {
                    val currentLayout = getCurrentLayout(call)
                    if (currentLayout == GRID_LAYOUT) {
                        Log.w(
                            "$TAG Current layout is mosaic but screen sharing was enabled, switching to active speaker layout"
                        )
                        setNewLayout(ACTIVE_SPEAKER_LAYOUT)
                    }
                } else {
                    Log.e("$TAG Screen sharing was enabled but conference's call is null!")
                }
            }
        }

        @WorkerThread
        override fun onStateChanged(conference: Conference, state: Conference.State) {
            Log.i("$TAG State changed [$state]")
            if (conference.state == Conference.State.Created) {
                val isIn = conference.isIn
                isPaused.postValue(!isIn)
                Log.i("$TAG We [${if (isIn) "are" else "aren't"}] in the conference")

                computeParticipants(false)
                if (conference.participantList.size >= 1) { // we do not count
                    Log.i("$TAG Joined conference already has at least another participant")
                    firstParticipantOtherThanOurselvesJoinedEvent.postValue(Event(true))
                }
            }
        }
    }

    init {
        isPaused.value = false
        isConversationAvailable.value = false
        isMeParticipantSendingVideo.value = false
        fullScreenMode.value = false
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

        val isIn = conference.isIn
        val state = conf.state
        if (state != Conference.State.CreationPending) {
            isPaused.postValue(!isIn)
        }
        Log.i(
            "$TAG We [${if (isIn) "are" else "aren't"}] in the conference right now, current state is [$state]"
        )

        val screenSharing = conference.screenSharingParticipant != null
        isScreenSharing.postValue(screenSharing)

        val chatEnabled = conference.currentParams.isChatEnabled
        isConversationAvailable.postValue(chatEnabled)

        val confSubject = conference.subject.orEmpty()
        Log.i(
            "$TAG Configuring conference with subject [$confSubject] from call [${call.callLog.callId}]"
        )
        sipUri.postValue(conference.conferenceAddress?.asStringUriOnly())
        subject.postValue(confSubject)

        if (conference.state == Conference.State.Created) {
            computeParticipants(false)
            if (conference.participantList.size >= 1) { // we do not count
                Log.i("$TAG Joined conference already has at least another participant")
                firstParticipantOtherThanOurselvesJoinedEvent.postValue(Event(true))
            }
        }

        val currentLayout = getCurrentLayout(call)
        conferenceLayout.postValue(currentLayout)
        if (currentLayout == GRID_LAYOUT && screenSharing) {
            Log.w(
                "$TAG Conference has a participant sharing its screen, changing layout from mosaic to active speaker"
            )
            setNewLayout(ACTIVE_SPEAKER_LAYOUT)
        }
    }

    @UiThread
    fun toggleFullScreen() {
        if (fullScreenMode.value == true) {
            // Always allow to switch off full screen mode
            fullScreenMode.value = false
            return
        }

        if (conferenceLayout.value == AUDIO_ONLY_LAYOUT) {
            // Do not allow turning full screen on for audio only conference
            return
        }

        if (isMeParticipantSendingVideo.value == false && participants.value.orEmpty().size == 1) {
            // Do not allow turning full screen on if we're alone and not sending our video
            return
        }

        fullScreenMode.value = true
    }

    @WorkerThread
    fun localVideoStreamToggled(enabled: Boolean) {
        isMeParticipantSendingVideo.postValue(enabled)
        Log.i("$TAG We [${if (enabled) "are" else "aren't"}] sending video")
    }

    @UiThread
    fun goToConversation() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Navigating to conference's conversation")
            val chatRoom = conference.chatRoom
            if (chatRoom != null) {
                goToConversationEvent.postValue(
                    Event(
                        Pair(
                            chatRoom.localAddress.asStringUriOnly(),
                            chatRoom.peerAddress.asStringUriOnly()
                        )
                    )
                )
            } else {
                Log.e(
                    "$TAG No chat room available for current conference [${conference.conferenceAddress?.asStringUriOnly()}]"
                )
            }
        }
    }

    @UiThread
    fun showLayoutMenu() {
        showLayoutMenuEvent.value = Event(true)
    }

    @UiThread
    fun changeLayout(newLayout: Int) {
        coreContext.postOnCoreThread {
            setNewLayout(newLayout)
        }
    }

    @UiThread
    fun inviteSipUrisIntoConference(uris: List<String>) {
        coreContext.postOnCoreThread { core ->
            val addresses = arrayListOf<Address>()
            for (uri in uris) {
                val address = core.interpretUrl(uri, false)
                if (address != null) {
                    addresses.add(address)
                    Log.i("$TAG Address [${address.asStringUriOnly()}] will be added to conference")
                } else {
                    Log.e(
                        "$TAG Failed to parse SIP URI [$uri] into address, can't add it to the conference!"
                    )
                    showRedToastEvent.postValue(
                        Event(
                            Pair(
                                R.string.conference_failed_to_add_participant_invalid_address_toast,
                                R.drawable.warning_circle
                            )
                        )
                    )
                }
            }
            val addressesArray = arrayOfNulls<Address>(addresses.size)
            addresses.toArray(addressesArray)
            Log.i("$TAG Trying to add [${addressesArray.size}] new participant(s) into conference")
            conference.addParticipants(addressesArray)
        }
    }

    @WorkerThread
    fun kickParticipant(participant: Participant) {
        coreContext.postOnCoreThread {
            Log.i(
                "$TAG Kicking participant [${participant.address.asStringUriOnly()}] out of conference"
            )
            conference.removeParticipant(participant)
        }
    }

    @WorkerThread
    fun setNewLayout(newLayout: Int) {
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
    private fun computeParticipants(skipDevices: Boolean) {
        participantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceModel::destroy)

        val participantsList = arrayListOf<ConferenceParticipantModel>()
        val devicesList = arrayListOf<ConferenceParticipantDeviceModel>()

        val conferenceParticipants = conference.participantList
        Log.i("$TAG [${conferenceParticipants.size}] participant in conference")

        val meParticipant = conference.me
        val admin = meParticipant.isAdmin
        isMeAdmin.postValue(admin)
        if (admin) {
            Log.i("$TAG We are admin of that conference!")
        }

        var activeSpeakerParticipantDeviceFound = false
        for (participant in conferenceParticipants) {
            val devices = participant.devices
            val role = participant.role

            Log.i(
                "$TAG Participant [${participant.address.asStringUriOnly()}] has [${devices.size}] devices and role [${role.name}]"
            )
            val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                participant.address
            )
            val participantModel = ConferenceParticipantModel(
                participant,
                avatarModel,
                admin,
                false,
                { participant -> // Remove from conference
                    removeParticipantEvent.postValue(
                        Event(Pair(avatarModel.name.value.orEmpty(), participant))
                    )
                },
                { participant, setAdmin -> // Change admin status
                    conference.setParticipantAdminStatus(participant, setAdmin)
                }
            )
            participantsList.add(participantModel)

            if (role == Participant.Role.Listener) {
                continue
            }

            if (!skipDevices) {
                for (device in devices) {
                    val model = ConferenceParticipantDeviceModel(device)
                    devicesList.add(model)

                    if (device == conference.activeSpeakerParticipantDevice) {
                        Log.i("$TAG Using participant is [${model.name}] as current active speaker")
                        model.isActiveSpeaker.postValue(true)
                        activeSpeaker.postValue(model)
                        activeSpeakerParticipantDeviceFound = true
                    }
                }
            }
        }
        Log.i(
            "$TAG [${devicesList.size}] participant devices for [${participantsList.size}] participants will be displayed (not counting ourselves)"
        )

        val meAvatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
            meParticipant.address
        )
        val meParticipantModel = ConferenceParticipantModel(
            meParticipant,
            meAvatarModel,
            admin,
            true,
            null,
            null
        )
        participantsList.add(meParticipantModel)

        val ourDevices = conference.me.devices
        Log.i("$TAG We have [${ourDevices.size}] devices")
        for (device in ourDevices) {
            if (!skipDevices) {
                val model = ConferenceParticipantDeviceModel(device, true)
                devicesList.add(model)

                if (device == conference.activeSpeakerParticipantDevice) {
                    Log.i("$TAG Using our device [${model.name}] as current active speaker")
                    model.isActiveSpeaker.postValue(true)
                    activeSpeaker.postValue(model)
                    activeSpeakerParticipantDeviceFound = true
                }
            }

            val direction = device.getStreamCapability(StreamType.Video)
            val sendingVideo = direction == MediaDirection.SendRecv || direction == MediaDirection.SendOnly
            localVideoStreamToggled(sendingVideo)
        }

        if (!activeSpeakerParticipantDeviceFound && devicesList.isNotEmpty()) {
            val first = devicesList.first()
            Log.w(
                "$TAG Failed to find current active speaker participant device, using first one [${first.name}]"
            )
            first.isActiveSpeaker.postValue(true)
            activeSpeaker.postValue(first)
        }

        participants.postValue(sortParticipantList(participantsList))
        if (!skipDevices) {
            checkIfTooManyParticipantDevicesForGridLayout(devicesList)

            if (participantsList.size == 1) {
                Log.i("$TAG We are alone in that conference, not posting devices list for now")
                participantDevices.postValue(arrayListOf())
            } else {
                participantDevices.postValue(sortParticipantDevicesList(devicesList))
            }

            participantsLabel.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.conference_participants_list_title,
                    participantsList.size,
                    "${participantsList.size}"
                )
            )
        }
    }

    @WorkerThread
    private fun sortParticipantList(devices: List<ConferenceParticipantModel>): ArrayList<ConferenceParticipantModel> {
        val sortedList = arrayListOf<ConferenceParticipantModel>()
        sortedList.addAll(devices)

        val meModel = sortedList.find {
            it.isMyself
        }
        if (meModel != null) {
            val index = sortedList.indexOf(meModel)
            val expectedIndex = 0
            if (index != expectedIndex) {
                Log.i(
                    "$TAG Me participant model is at index $index, moving it to index $expectedIndex"
                )
                sortedList.removeAt(index)
                sortedList.add(expectedIndex, meModel)
            }
        }

        return sortedList
    }

    @WorkerThread
    private fun sortParticipantDevicesList(devices: List<ConferenceParticipantDeviceModel>): ArrayList<ConferenceParticipantDeviceModel> {
        val sortedList = arrayListOf<ConferenceParticipantDeviceModel>()
        sortedList.addAll(devices)

        val meDeviceModel = sortedList.find {
            it.isMe
        }
        if (meDeviceModel != null) {
            val index = sortedList.indexOf(meDeviceModel)
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
                    "$TAG Me device model is at index $index, moving it to index $expectedIndex"
                )
                sortedList.removeAt(index)
                sortedList.add(expectedIndex, meDeviceModel)
            }
        }

        return sortedList
    }

    @WorkerThread
    private fun addParticipant(participant: Participant) {
        val list = arrayListOf<ConferenceParticipantModel>()
        list.addAll(participants.value.orEmpty())

        val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
            participant.address
        )
        val newModel = ConferenceParticipantModel(
            participant,
            avatarModel,
            isMeAdmin.value == true,
            false,
            { participant -> // Remove from conference
                removeParticipantEvent.postValue(
                    Event(Pair(avatarModel.name.value.orEmpty(), participant))
                )
            },
            { participant, setAdmin -> // Change admin status
                conference.setParticipantAdminStatus(participant, setAdmin)
            }
        )
        list.add(newModel)

        participants.postValue(sortParticipantList(list))
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

        checkIfTooManyParticipantDevicesForGridLayout(list)
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

    @WorkerThread
    fun togglePause() {
        if (::conference.isInitialized) {
            if (conference.isIn) {
                Log.i("$TAG Temporary leaving conference")
                conference.leave()
                isPaused.postValue(true)
            } else {
                Log.i("$TAG Entering conference again")
                conference.enter()
                isPaused.postValue(false)
            }
        }
    }

    @WorkerThread
    private fun checkIfTooManyParticipantDevicesForGridLayout(
        list: ArrayList<ConferenceParticipantDeviceModel>
    ) {
        if (list.size > GridBoxLayout.MAX_CHILD && conferenceLayout.value == GRID_LAYOUT) {
            Log.w(
                "$TAG Too many participant devices for grid layout, switching to active speaker layout"
            )
            setNewLayout(ACTIVE_SPEAKER_LAYOUT)
            showRedToastEvent.postValue(
                Event(
                    Pair(
                        R.string.conference_too_many_participants_for_mosaic_layout_toast,
                        R.drawable.warning_circle
                    )
                )
            )
        }
    }
}

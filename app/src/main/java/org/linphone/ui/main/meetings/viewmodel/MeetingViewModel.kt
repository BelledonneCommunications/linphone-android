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
package org.linphone.ui.main.meetings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.util.TimeZone
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.ConferenceScheduler
import org.linphone.core.ConferenceSchedulerListenerStub
import org.linphone.core.Factory
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.meetings.model.ParticipantModel
import org.linphone.ui.main.meetings.model.TimeZoneModel
import org.linphone.utils.Event
import org.linphone.utils.TimestampUtils

class MeetingViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Meeting ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val isBroadcast = MutableLiveData<Boolean>()

    val isEditable = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val sipUri = MutableLiveData<String>()

    val dateTime = MutableLiveData<String>()

    val timezone = MutableLiveData<String>()

    val description = MutableLiveData<String>()

    val speakers = MutableLiveData<ArrayList<ParticipantModel>>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val isCancelled = MutableLiveData<Boolean>()

    val conferenceInfoFoundEvent = MutableLiveData<Event<Boolean>>()

    val startTimeStamp = MutableLiveData<Long>()
    val endTimeStamp = MutableLiveData<Long>()

    val operationInProgress = MutableLiveData<Boolean>()

    val conferenceCancelledEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val conferenceInfoDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val conferenceSchedulerListener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State?
        ) {
            Log.i("$TAG Conference scheduler state is $state")
            if (state == ConferenceScheduler.State.Ready) {
                Log.i(
                    "$TAG Conference ${conferenceScheduler.info?.subject} cancelled"
                )
                val chatRoomParams = coreContext.core.createDefaultChatRoomParams()
                chatRoomParams.isGroupEnabled = false
                chatRoomParams.backend = ChatRoom.Backend.FlexisipChat
                chatRoomParams.isEncryptionEnabled = true
                chatRoomParams.subject = "Meeting cancelled" // Won't be used
                conferenceScheduler.sendInvitations(chatRoomParams) // Send cancel ICS
            } else if (state == ConferenceScheduler.State.Error) {
                operationInProgress.postValue(false)
            }
        }

        override fun onInvitationsSent(
            conferenceScheduler: ConferenceScheduler,
            failedInvitations: Array<out Address>?
        ) {
            if (failedInvitations?.isNotEmpty() == true) {
                for (address in failedInvitations) {
                    Log.e(
                        "$TAG Conference cancelled ICS wasn't sent to participant ${address.asStringUriOnly()}"
                    )
                }
            } else {
                Log.i(
                    "$TAG Conference cancelled ICS successfully sent to all participants"
                )
            }
            conferenceScheduler.removeListener(this)

            operationInProgress.postValue(false)
            conferenceCancelledEvent.postValue(Event(true))
        }
    }

    private lateinit var conferenceInfo: ConferenceInfo

    init {
        operationInProgress.value = false
    }

    @UiThread
    fun findConferenceInfo(meeting: ConferenceInfo?, uri: String) {
        coreContext.postOnCoreThread { core ->
            if (meeting != null && ::conferenceInfo.isInitialized && meeting == conferenceInfo) {
                Log.i("$TAG ConferenceInfo object already in memory, skipping")
                conferenceInfoFoundEvent.postValue(Event(true))
                return@postOnCoreThread
            }

            val address = Factory.instance().createAddress(uri)

            if (meeting != null && (!::conferenceInfo.isInitialized || conferenceInfo != meeting)) {
                if (address != null && meeting.uri?.equal(address) == true) {
                    Log.i("$TAG ConferenceInfo object available in sharedViewModel, using it")
                    conferenceInfo = meeting
                    configureConferenceInfo()
                    conferenceInfoFoundEvent.postValue(Event(true))
                    return@postOnCoreThread
                }
            }

            if (address != null) {
                val found = core.findConferenceInformationFromUri(address)
                if (found != null) {
                    Log.i("$TAG Conference info with SIP URI [$uri] was found")
                    conferenceInfo = found
                    configureConferenceInfo()
                    conferenceInfoFoundEvent.postValue(Event(true))
                } else {
                    Log.e("$TAG Conference info with SIP URI [$uri] couldn't be found!")
                    conferenceInfoFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse SIP URI [$uri] as Address!")
                conferenceInfoFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            if (::conferenceInfo.isInitialized) {
                Log.i("$TAG Deleting conference information [$conferenceInfo]")
                core.deleteConferenceInformation(conferenceInfo)
                conferenceInfoDeletedEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun cancel() {
        coreContext.postOnCoreThread { core ->
            if (::conferenceInfo.isInitialized) {
                Log.i("$TAG Cancelling conference information [$conferenceInfo]")
                operationInProgress.postValue(true)
                val conferenceScheduler = core.createConferenceScheduler()
                conferenceScheduler.addListener(conferenceSchedulerListener)
                conferenceScheduler.cancelConference(conferenceInfo)
            }
        }
    }

    @UiThread
    fun refreshInfo(uri: String) {
        coreContext.postOnCoreThread { core ->
            val address = Factory.instance().createAddress(uri)
            if (address != null) {
                val found = core.findConferenceInformationFromUri(address)
                if (found != null) {
                    Log.i("$TAG Conference info with SIP URI [$uri] was found, updating info")
                    conferenceInfo = found
                    configureConferenceInfo()
                }
            }
        }
    }

    @WorkerThread
    private fun configureConferenceInfo() {
        if (::conferenceInfo.isInitialized) {
            subject.postValue(conferenceInfo.subject)
            sipUri.postValue(conferenceInfo.uri?.asStringUriOnly() ?: "")
            description.postValue(conferenceInfo.description)

            val state = conferenceInfo.state
            Log.i("$TAG Conference info is in state [$state]")
            isCancelled.postValue(state == ConferenceInfo.State.Cancelled)

            val timestamp = conferenceInfo.dateTime
            val duration = conferenceInfo.duration
            val date = TimestampUtils.toString(
                timestamp,
                onlyDate = true,
                shortDate = false,
                hideYear = false
            )
            val startTime = TimestampUtils.timeToString(timestamp)
            val end = timestamp + (duration * 60)
            val endTime = TimestampUtils.timeToString(end)
            startTimeStamp.postValue(timestamp * 1000)
            endTimeStamp.postValue(end * 1000)
            dateTime.postValue("$date | $startTime - $endTime")

            timezone.postValue(TimeZoneModel(TimeZone.getDefault()).toString())

            val organizerAddress = conferenceInfo.organizer
            if (organizerAddress != null) {
                val localAccount = coreContext.core.accountList.find { account ->
                    val address = account.params.identityAddress
                    address != null && organizerAddress.weakEqual(address)
                }
                isEditable.postValue(localAccount != null)
            } else {
                isEditable.postValue(false)
                Log.e(
                    "$TAG No organizer SIP URI found for: ${conferenceInfo.uri?.asStringUriOnly()}"
                )
            }

            computeParticipantsList()
        }
    }

    private fun computeParticipantsList() {
        val speakersList = arrayListOf<ParticipantModel>()
        val participantsList = arrayListOf<ParticipantModel>()

        var allSpeaker = true
        val organizer = conferenceInfo.organizer
        var organizerFound = false
        for (info in conferenceInfo.participantInfos) {
            val participant = info.address
            val isOrganizer = organizer?.weakEqual(participant) == true
            Log.d(
                "$TAG Conference [${conferenceInfo.subject}] ${if (isOrganizer) "organizer" else "participant"} [${participant.asStringUriOnly()}] is a [${info.role}]"
            )
            if (isOrganizer) {
                organizerFound = true
            }

            if (info.role == Participant.Role.Listener) {
                allSpeaker = false
                participantsList.add(ParticipantModel(participant, isOrganizer))
            } else {
                speakersList.add(ParticipantModel(participant, isOrganizer))
            }
        }
        Log.i(
            "$TAG Found [${speakersList.size}] participants for conference [${conferenceInfo.uri?.asStringUriOnly()}]"
        )

        if (allSpeaker) {
            Log.i("$TAG All participants have Speaker role, considering it is a meeting")
            participantsList.addAll(speakersList)
        }

        if (!organizerFound && organizer != null) {
            Log.i("$TAG Organizer not found in participants list, adding it to participants list")
            participantsList.add(ParticipantModel(organizer, true))
        }

        isBroadcast.postValue(!allSpeaker)
        speakers.postValue(speakersList)
        participants.postValue(participantsList)
    }
}

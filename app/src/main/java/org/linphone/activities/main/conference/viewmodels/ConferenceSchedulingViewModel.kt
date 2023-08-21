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
package org.linphone.activities.main.conference.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.conference.data.ConferenceSchedulingParticipantData
import org.linphone.activities.main.conference.data.Duration
import org.linphone.activities.main.conference.data.TimeZoneData
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ConferenceSchedulingViewModel : ContactsSelectionViewModel() {
    val subject = MutableLiveData<String>()
    val description = MutableLiveData<String>()

    val scheduleForLater = MutableLiveData<Boolean>()
    val isUpdate = MutableLiveData<Boolean>()

    val isBroadcastAllowed = MutableLiveData<Boolean>()
    val mode = MutableLiveData<String>()
    val modesList: List<String>

    val formattedDate = MutableLiveData<String>()
    val formattedTime = MutableLiveData<String>()

    val isEncrypted = MutableLiveData<Boolean>()

    val sendInviteViaChat = MutableLiveData<Boolean>()
    val sendInviteViaEmail = MutableLiveData<Boolean>()

    val participantsData = MutableLiveData<List<ConferenceSchedulingParticipantData>>()
    val speakersData = MutableLiveData<List<ConferenceSchedulingParticipantData>>()

    val address = MutableLiveData<Address>()

    val conferenceCreationInProgress = MutableLiveData<Boolean>()

    val conferenceCreationCompletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val continueEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    var timeZone = MutableLiveData<TimeZoneData>()
    val timeZones: List<TimeZoneData> = computeTimeZonesList()

    var duration = MutableLiveData<Duration>()
    val durationList: List<Duration> = computeDurationList()

    var dateTimestamp: Long = System.currentTimeMillis()
    var hour: Int = 0
    var minutes: Int = 0

    private var confInfo: ConferenceInfo? = null
    private val conferenceScheduler = coreContext.core.createConferenceScheduler()

    private val selectedSpeakersAddresses = MutableLiveData<ArrayList<Address>>()

    private val listener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State
        ) {
            Log.i("[Conference Creation] Conference scheduler state is $state")
            if (state == ConferenceScheduler.State.Ready) {
                val conferenceAddress = conferenceScheduler.info?.uri
                Log.i(
                    "[Conference Creation] Conference info created, address will be ${conferenceAddress?.asStringUriOnly()}"
                )
                conferenceAddress ?: return

                address.value = conferenceAddress!!

                if (scheduleForLater.value == true) {
                    if (sendInviteViaChat.value == true) {
                        // Send conference info even when conf is not scheduled for later
                        // as the conference server doesn't invite participants automatically
                        Log.i(
                            "[Conference Creation] Scheduled conference is ready, sending invitations by chat"
                        )
                        val chatRoomParams = LinphoneUtils.getConferenceInvitationsChatRoomParams()
                        conferenceScheduler.sendInvitations(chatRoomParams)
                    } else {
                        Log.i(
                            "[Conference Creation] Scheduled conference is ready, we were asked not to send invitations by chat so leaving fragment"
                        )
                        conferenceCreationInProgress.value = false
                        conferenceCreationCompletedEvent.value = Event(true)
                    }
                } else {
                    Log.i("[Conference Creation] Group call is ready, leaving fragment")
                    conferenceCreationInProgress.value = false
                    conferenceCreationCompletedEvent.value = Event(true)
                }
            } else if (state == ConferenceScheduler.State.Error) {
                Log.e("[Conference Creation] Failed to create conference!")
                conferenceCreationInProgress.value = false
                onMessageToNotifyEvent.value = Event(R.string.conference_creation_failed)
            }
        }

        override fun onInvitationsSent(
            conferenceScheduler: ConferenceScheduler,
            failedInvitations: Array<out Address>?
        ) {
            conferenceCreationInProgress.value = false

            if (failedInvitations?.isNotEmpty() == true) {
                for (address in failedInvitations) {
                    Log.e(
                        "[Conference Creation] Conference information wasn't sent to participant ${address.asStringUriOnly()}"
                    )
                }
                onMessageToNotifyEvent.value = Event(
                    R.string.conference_schedule_info_not_sent_to_participant
                )
            } else {
                Log.i(
                    "[Conference Creation] Conference information successfully sent to all participants"
                )
            }

            val conferenceAddress = conferenceScheduler.info?.uri
            if (conferenceAddress == null) {
                Log.e("[Conference Creation] Conference address is null!")
            } else {
                conferenceCreationCompletedEvent.value = Event(true)
            }
        }
    }

    init {
        sipContactsSelected.value = true

        subject.value = ""
        scheduleForLater.value = false
        isUpdate.value = false

        isBroadcastAllowed.value = !corePreferences.disableBroadcastConference
        modesList = arrayListOf(
            AppUtils.getString(R.string.conference_schedule_mode_meeting),
            AppUtils.getString(R.string.conference_schedule_mode_broadcast)
        )
        mode.value = modesList.first() // Meeting by default

        isEncrypted.value = false
        sendInviteViaChat.value = true
        sendInviteViaEmail.value = false

        timeZone.value = timeZones.find {
            it.id == TimeZone.getDefault().id
        }
        duration.value = durationList.find {
            it.value == 60
        }

        continueEnabled.value = false
        continueEnabled.addSource(subject) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }
        continueEnabled.addSource(scheduleForLater) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }
        continueEnabled.addSource(formattedDate) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }
        continueEnabled.addSource(formattedTime) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }

        conferenceScheduler.addListener(listener)
    }

    override fun onCleared() {
        conferenceScheduler.removeListener(listener)
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)
        speakersData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)

        super.onCleared()
    }

    fun prePopulateParticipantsList(participants: ArrayList<Address>, isSchedule: Boolean) {
        selectedAddresses.value = participants
        scheduleForLater.value = isSchedule
    }

    fun populateFromConferenceInfo(conferenceInfo: ConferenceInfo) {
        // Pre-set data from existing conference info, used when editing an already scheduled broadcast or meeting
        confInfo = conferenceInfo

        address.value = conferenceInfo.uri
        subject.value = conferenceInfo.subject
        description.value = conferenceInfo.description
        isUpdate.value = true

        val dateTime = conferenceInfo.dateTime
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateTime * 1000
        setDate(calendar.timeInMillis)
        setTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))

        val conferenceDuration = conferenceInfo.duration
        duration.value = durationList.find { it.value == conferenceDuration }
        scheduleForLater.value = conferenceDuration > 0

        val participantsList = arrayListOf<Address>()
        val speakersList = arrayListOf<Address>()
        for (info in conferenceInfo.participantInfos) {
            val participant = info.address
            participantsList.add(participant)
            if (info.role == Participant.Role.Speaker) {
                speakersList.add(participant)
            }
        }
        if (participantsList.count() == speakersList.count()) {
            // All participants are speaker, this is a meeting, clear speakers
            Log.i("[Conference Creation] Conference info is a meeting")
            speakersList.clear()
            mode.value = modesList.first()
        } else {
            Log.i("[Conference Creation] Conference info is a broadcast")
            mode.value = modesList.last()
        }
        selectedAddresses.value = participantsList
        selectedSpeakersAddresses.value = speakersList

        computeParticipantsData()
    }

    fun toggleSchedule() {
        scheduleForLater.value = scheduleForLater.value == false
    }

    fun setDate(d: Long) {
        dateTimestamp = d
        formattedDate.value = TimestampUtils.dateToString(dateTimestamp, false)
    }

    fun setTime(h: Int, m: Int) {
        hour = h
        minutes = m
        formattedTime.value = TimestampUtils.timeToString(hour, minutes)
    }

    fun updateEncryption(enable: Boolean) {
        isEncrypted.value = enable
    }

    fun computeParticipantsData() {
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)
        speakersData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)

        val participantsList = arrayListOf<ConferenceSchedulingParticipantData>()
        val speakersList = arrayListOf<ConferenceSchedulingParticipantData>()

        for (address in selectedAddresses.value.orEmpty()) {
            val isSpeaker = address in selectedSpeakersAddresses.value.orEmpty()
            val data = ConferenceSchedulingParticipantData(
                address,
                showLimeBadge = isEncrypted.value == true,
                showBroadcastControls = isModeBroadcastCurrentlySelected(),
                speaker = isSpeaker,
                onAddedToSpeakers = { data ->
                    Log.i(
                        "[Conference Creation] Participant [${address.asStringUriOnly()}] added to speakers"
                    )
                    val participants = arrayListOf<ConferenceSchedulingParticipantData>()
                    participants.addAll(participantsData.value.orEmpty())
                    participants.remove(data)
                    participantsData.value = participants

                    val speakers = arrayListOf<ConferenceSchedulingParticipantData>()
                    speakers.addAll(speakersData.value.orEmpty())
                    speakers.add(data)
                    speakersData.value = speakers
                },
                onRemovedFromSpeakers = { data ->
                    Log.i(
                        "[Conference Creation] Participant [${address.asStringUriOnly()}] removed from speakers"
                    )
                    val speakers = arrayListOf<ConferenceSchedulingParticipantData>()
                    speakers.addAll(speakersData.value.orEmpty())
                    speakers.remove(data)
                    speakersData.value = speakers

                    val participants = arrayListOf<ConferenceSchedulingParticipantData>()
                    participants.addAll(participantsData.value.orEmpty())
                    participants.add(data)
                    participantsData.value = participants
                }
            )

            if (isSpeaker) {
                speakersList.add(data)
            } else {
                participantsList.add(data)
            }
        }

        participantsData.value = participantsList
        speakersData.value = speakersList
    }

    fun createConference() {
        val participantsCount = selectedAddresses.value.orEmpty().size
        if (participantsCount == 0) {
            Log.e("[Conference Creation] Couldn't create conference without any participant!")
            return
        }

        conferenceCreationInProgress.value = true
        val core = coreContext.core
        val localAccount = core.defaultAccount
        val localAddress = localAccount?.params?.identityAddress

        val conferenceInfo = if (isUpdate.value == true) {
            confInfo?.clone() ?: Factory.instance().createConferenceInfo()
        } else {
            Factory.instance().createConferenceInfo()
        }
        conferenceInfo.organizer = localAddress
        conferenceInfo.subject = subject.value
        conferenceInfo.description = description.value

        val participants = arrayOfNulls<ParticipantInfo>(selectedAddresses.value.orEmpty().size)
        var index = 0
        val isBroadcast = isModeBroadcastCurrentlySelected()
        for (participant in participantsData.value.orEmpty()) {
            val info = Factory.instance().createParticipantInfo(participant.sipAddress)
            // For meetings, all participants must have Speaker role
            info?.role = if (isBroadcast) Participant.Role.Listener else Participant.Role.Speaker
            participants[index] = info
            index += 1
        }
        for (speaker in speakersData.value.orEmpty()) {
            val info = Factory.instance().createParticipantInfo(speaker.sipAddress)
            info?.role = Participant.Role.Speaker
            participants[index] = info
            index += 1
        }
        conferenceInfo.setParticipantInfos(participants)

        if (scheduleForLater.value == true) {
            val startTime = getConferenceStartTimestamp()
            conferenceInfo.dateTime = startTime
            val duration = duration.value?.value ?: 0
            conferenceInfo.duration = duration
        }

        confInfo = conferenceInfo
        conferenceScheduler.account = localAccount
        // Will trigger the conference creation/update automatically
        conferenceScheduler.info = conferenceInfo
    }

    fun isModeBroadcastCurrentlySelected(): Boolean {
        return mode.value == AppUtils.getString(R.string.conference_schedule_mode_broadcast)
    }

    private fun computeTimeZonesList(): List<TimeZoneData> {
        return TimeZone.getAvailableIDs().map { id -> TimeZoneData(TimeZone.getTimeZone(id)) }.toList().sorted()
    }

    private fun computeDurationList(): List<Duration> {
        // Duration value is in minutes as according to conferenceInfo.setDuration() doc
        return arrayListOf(Duration(30, "30min"), Duration(60, "1h"), Duration(120, "2h"))
    }

    private fun allMandatoryFieldsFilled(): Boolean {
        return !subject.value.isNullOrEmpty() &&
            (
                scheduleForLater.value == false ||
                    (
                        !formattedDate.value.isNullOrEmpty() &&
                            !formattedTime.value.isNullOrEmpty()
                        )
                )
    }

    private fun getConferenceStartTimestamp(): Long {
        val calendar = Calendar.getInstance(
            TimeZone.getTimeZone(timeZone.value?.id ?: TimeZone.getDefault().id)
        )
        calendar.timeInMillis = dateTimestamp
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minutes)
        return calendar.timeInMillis / 1000 // Linphone expects a time_t (so in seconds)
    }
}

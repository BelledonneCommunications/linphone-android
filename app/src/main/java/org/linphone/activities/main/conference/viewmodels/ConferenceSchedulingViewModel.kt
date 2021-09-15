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

import androidx.lifecycle.MutableLiveData
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.conference.data.ConferenceSchedulingParticipantData
import org.linphone.activities.main.conference.data.Duration
import org.linphone.activities.main.conference.data.TimeZoneData
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.TimestampUtils

class ConferenceSchedulingViewModel : ContactsSelectionViewModel() {
    val subject = MutableLiveData<String>()

    val scheduleForLater = MutableLiveData<Boolean>()

    val formattedDate = MutableLiveData<String>()
    val formattedTime = MutableLiveData<String>()

    val isEncrypted = MutableLiveData<Boolean>()

    val sendInviteViaChat = MutableLiveData<Boolean>()
    val sendInviteViaEmail = MutableLiveData<Boolean>()

    val participantsData = MutableLiveData<List<ConferenceSchedulingParticipantData>>()

    val address = MutableLiveData<String>()

    val conferenceCreationInProgress = MutableLiveData<Boolean>()

    val copyToClipboardEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val conferenceCreationCompletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var timeZone = MutableLiveData<TimeZoneData>()
    val timeZones: List<TimeZoneData> = computeTimeZonesList()

    var duration = MutableLiveData<Duration>()
    val durationList: List<Duration> = computeDurationList()

    private var date: Long = 0
    private var hour: Int = 0
    private var minutes: Int = 0

    private val listener = object : CoreListenerStub() {
        override fun onConferenceInfoOnSent(core: Core, conferenceInfo: ConferenceInfo) {
            Log.i("[Conference Creation] Conference information successfully sent to all participants")
            conferenceCreationInProgress.value = false
            conferenceCreationCompletedEvent.value = Event(true)
        }

        override fun onConferenceInfoOnParticipantError(
            core: Core,
            conferenceInfo: ConferenceInfo,
            participant: Address,
            error: ConferenceInfoError?
        ) {
            Log.e("[Conference Creation] Conference information wasn't sent to participant ${participant.asStringUriOnly()}")
            onErrorEvent.value = Event(R.string.conference_schedule_info_not_sent_to_participant)
            conferenceCreationInProgress.value = false
        }
    }

    init {
        sipContactsSelected.value = true

        subject.value = ""
        scheduleForLater.value = false
        isEncrypted.value = false
        sendInviteViaChat.value = true
        sendInviteViaEmail.value = false

        address.value = "sip:video-conference-0@sip.linphone.org" // TODO: get real conference address
        timeZone.value = timeZones.find {
            it.id == TimeZone.getDefault().id
        }
        duration.value = durationList.find {
            it.value == 60
        }

        coreContext.core.addListener(listener)
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)

        super.onCleared()
    }

    fun toggleSchedule() {
        scheduleForLater.value = scheduleForLater.value == false
    }

    fun setDate(d: Long) {
        date = d
        formattedDate.value = TimestampUtils.dateToString(date)
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
        val list = arrayListOf<ConferenceSchedulingParticipantData>()

        for (address in selectedAddresses.value.orEmpty()) {
            val data = ConferenceSchedulingParticipantData(address, isEncrypted.value == true)
            list.add(data)
        }

        participantsData.value = list
    }

    fun createConference() {
        val participantsCount = selectedAddresses.value.orEmpty().size
        if (participantsCount == 0) {
            Log.e("[Conference Creation] Couldn't create conference without any participant!")
            return
        }

        conferenceCreationInProgress.value = true

        val participants = arrayOfNulls<Address>(participantsCount)
        selectedAddresses.value?.toArray(participants)

        if (scheduleForLater.value == true) {
            val conferenceInfo = Factory.instance().createConferenceInfo()
            conferenceInfo.uri = Factory.instance().createAddress(address.value.orEmpty())
            conferenceInfo.setParticipants(participants)
            conferenceInfo.organizer = coreContext.core.defaultAccount?.params?.identityAddress
            conferenceInfo.subject = subject.value
            conferenceInfo.duration = duration.value?.value ?: 0

            val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone.value?.id ?: TimeZone.getDefault().id))
            calendar.timeInMillis = date
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minutes)
            conferenceInfo.dateTime = calendar.timeInMillis / 1000 // Linphone expects a time_t (so in seconds)
            Log.i("[Conference Creation] Conference date & time set to ${TimestampUtils.dateToString(calendar.timeInMillis)} ${TimestampUtils.timeToString(calendar.timeInMillis)}, duration = ${conferenceInfo.duration}")

            coreContext.core.sendConferenceInformation(conferenceInfo, "")
        } else {
            val conferenceParams = coreContext.core.createConferenceParams()
            val conference = coreContext.core.createConferenceWithParams(conferenceParams)
            if (conference == null) {
                Log.e("[Conference Creation] Couldn't create conference from params!")
                onErrorEvent.value = Event(R.string.conference_schedule_creation_failure)
                conferenceCreationInProgress.value = false
                return
            }

            val callParams = coreContext.core.createCallParams(null)
            conference.inviteParticipants(participants, callParams)

            conferenceCreationInProgress.value = false
            conferenceCreationCompletedEvent.value = Event(true)
        }
    }

    fun copyAddressToClipboard() {
        copyToClipboardEvent.value = Event(address.value.orEmpty())
    }

    private fun computeTimeZonesList(): List<TimeZoneData> {
        return TimeZone.getAvailableIDs().map { id -> TimeZoneData(TimeZone.getTimeZone(id)) }.toList().sorted()
    }

    private fun computeDurationList(): List<Duration> {
        return arrayListOf(Duration(30, "30min"), Duration(60, "1h"), Duration(120, "2h"))
    }
}

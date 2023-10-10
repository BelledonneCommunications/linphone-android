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
import androidx.lifecycle.ViewModel
import java.util.Locale
import java.util.TimeZone
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.ConferenceInfo
import org.linphone.core.Factory
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import org.linphone.ui.main.meetings.model.ParticipantModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.TimestampUtils

class MeetingViewModel @UiThread constructor() : ViewModel() {
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

    val conferenceInfoFoundEvent = MutableLiveData<Event<Boolean>>()

    val startTimeStamp = MutableLiveData<Long>()
    val endTimeStamp = MutableLiveData<Long>()

    private lateinit var conferenceInfo: ConferenceInfo

    init {
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            speakers.value.orEmpty().forEach(ParticipantModel::destroy)
            participants.value.orEmpty().forEach(ParticipantModel::destroy)
        }
    }

    @UiThread
    fun findConferenceInfo(uri: String) {
        coreContext.postOnCoreThread { core ->
            val address = Factory.instance().createAddress(uri)
            if (address != null) {
                val found = core.findConferenceInformationFromUri(address)
                if (found != null) {
                    conferenceInfo = found
                    configureConferenceInfo()
                    conferenceInfoFoundEvent.postValue(Event(true))
                } else {
                    conferenceInfoFoundEvent.postValue(Event(false))
                }
            } else {
                conferenceInfoFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun join() {
        // TODO
    }

    @WorkerThread
    private fun configureConferenceInfo() {
        if (::conferenceInfo.isInitialized) {
            subject.postValue(conferenceInfo.subject)
            sipUri.postValue(conferenceInfo.uri?.asStringUriOnly() ?: "")
            description.postValue(conferenceInfo.description)

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

            timezone.postValue(
                AppUtils.getFormattedString(
                    R.string.meeting_schedule_timezone_title,
                    TimeZone.getDefault().displayName
                )
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            )

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
        speakers.value.orEmpty().forEach(ParticipantModel::destroy)
        participants.value.orEmpty().forEach(ParticipantModel::destroy)

        val speakersList = arrayListOf<ParticipantModel>()
        val participantsList = arrayListOf<ParticipantModel>()

        var allSpeaker = true
        val organizer = conferenceInfo.organizer
        var organizerFound = false
        for (info in conferenceInfo.participantInfos) {
            val participant = info.address
            val isOrganizer = organizer?.weakEqual(participant) ?: false
            Log.i(
                "$TAG Conference [${subject.value}] ${if (isOrganizer) "organizer" else "participant"} [${participant.asStringUriOnly()}] is a [${info.role}]"
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
        if (!organizerFound && organizer != null) {
            Log.i("$TAG Organizer not found in participants list, adding it to participants list")
            participantsList.add(ParticipantModel(organizer, true))
        }

        isBroadcast.postValue(!allSpeaker)
        speakers.postValue(speakersList)
        participants.postValue(participantsList)
    }
}

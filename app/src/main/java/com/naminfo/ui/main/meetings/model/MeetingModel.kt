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
package com.naminfo.ui.main.meetings.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import com.naminfo.LinphoneApplication.Companion.coreContext
import org.linphone.core.ConferenceInfo
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import com.naminfo.utils.TimestampUtils

class MeetingModel
    @WorkerThread
    constructor(val conferenceInfo: ConferenceInfo) {
    companion object {
        private const val TAG = "[Meeting Model]"
    }

    val id = conferenceInfo.uri?.asStringUriOnly() ?: ""

    val timestamp = conferenceInfo.dateTime

    val day = TimestampUtils.dayOfWeek(timestamp)

    val dayNumber = TimestampUtils.dayOfMonth(timestamp)

    val month = TimestampUtils.month(timestamp)

    val isToday = TimestampUtils.isToday(timestamp)

    val isAfterToday = TimestampUtils.isAfterToday(timestamp)

    private val startTime = TimestampUtils.timeToString(timestamp)

    private val endTime = TimestampUtils.timeToString(timestamp + (conferenceInfo.duration * 60))

    val time = "$startTime - $endTime"

    val isBroadcast = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val firstMeetingOfTheDay = MutableLiveData<Boolean>()

    val weekLabel = TimestampUtils.firstAndLastDayOfWeek(timestamp)

    val firstMeetingOfTheWeek = MutableLiveData<Boolean>()

    val isCancelled = conferenceInfo.state == ConferenceInfo.State.Cancelled

    init {
        subject.postValue(conferenceInfo.subject)

        var allSpeaker = true
        for (participant in conferenceInfo.participantInfos) {
            if (participant.role == Participant.Role.Listener) {
                allSpeaker = false
                break
            }
        }

        isBroadcast.postValue(!allSpeaker)
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            Log.w("$TAG Deleting conference info [${conferenceInfo.uri?.asStringUriOnly()}]")
            core.deleteConferenceInformation(conferenceInfo)
        }
    }

    @WorkerThread
    fun isOrganizer(): Boolean {
        return coreContext.core.accountList.find { account ->
            val address = account.params.identityAddress
            address != null && conferenceInfo.organizer?.weakEqual(address) == true
        } != null
    }
}

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
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.conference.data.ScheduledConferenceData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class ScheduledConferencesViewModel : ViewModel() {
    val conferences = MutableLiveData<ArrayList<ScheduledConferenceData>>()

    val showTerminated = MutableLiveData<Boolean>()

    private val conferenceScheduler: ConferenceScheduler by lazy {
        val scheduler = coreContext.core.createConferenceScheduler()
        scheduler.addListener(conferenceListener)
        scheduler
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceInfoReceived(core: Core, conferenceInfo: ConferenceInfo) {
            Log.i("[Scheduled Conferences] New conference info received")
            computeConferenceInfoList()
        }
    }

    private val conferenceListener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State
        ) {
            Log.i("[Scheduled Conferences] Conference scheduler state is $state")
            if (state == ConferenceScheduler.State.Ready) {
                Log.i("[Scheduled Conferences] Conference ${conferenceScheduler.info?.subject} cancelled")
                val chatRoomParams = LinphoneUtils.getConferenceInvitationsChatRoomParams()
                conferenceScheduler.sendInvitations(chatRoomParams) // Send cancel ICS
            }
        }

        override fun onInvitationsSent(
            conferenceScheduler: ConferenceScheduler,
            failedInvitations: Array<out Address>?
        ) {
            if (failedInvitations?.isNotEmpty() == true) {
                for (address in failedInvitations) {
                    Log.e("[Scheduled Conferences] Conference cancelled ICS wasn't sent to participant ${address.asStringUriOnly()}")
                }
            } else {
                Log.i("[Scheduled Conferences] Conference cancelled ICS successfully sent to all participants")
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        showTerminated.value = false

        computeConferenceInfoList()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        conferences.value.orEmpty().forEach(ScheduledConferenceData::destroy)

        super.onCleared()
    }

    fun applyFilter() {
        computeConferenceInfoList()
    }

    fun deleteConferenceInfo(data: ScheduledConferenceData) {
        val conferenceInfoList = arrayListOf<ScheduledConferenceData>()

        conferenceInfoList.addAll(conferences.value.orEmpty())
        conferenceInfoList.remove(data)

        if (data.conferenceInfo.state != ConferenceInfo.State.Cancelled && data.canEdit.value == true) {
            Log.i("[Scheduled Conferences] Cancelling conference ${data.conferenceInfo.subject}")
            conferenceScheduler.cancelConference(data.conferenceInfo)
        }

        data.delete()
        data.destroy()
        conferences.value = conferenceInfoList
    }

    fun deleteConferencesInfo(toRemoveList: List<ScheduledConferenceData>) {
        val conferenceInfoList = arrayListOf<ScheduledConferenceData>()

        for (confInfo in conferences.value.orEmpty()) {
            if (confInfo in toRemoveList) {
                confInfo.delete()
                confInfo.destroy()
            } else {
                conferenceInfoList.add(confInfo)
            }
        }

        conferences.value = conferenceInfoList
    }

    private fun computeConferenceInfoList() {
        conferences.value.orEmpty().forEach(ScheduledConferenceData::destroy)

        val conferencesList = arrayListOf<ScheduledConferenceData>()

        val now = System.currentTimeMillis() / 1000 // Linphone uses time_t in seconds

        if (showTerminated.value == true) {
            for (conferenceInfo in coreContext.core.conferenceInformationList) {
                if (conferenceInfo.duration == 0) continue // This isn't a scheduled conference, don't display it
                val limit = conferenceInfo.dateTime + conferenceInfo.duration
                if (limit >= now) continue // This isn't a terminated conference, don't display it
                val data = ScheduledConferenceData(conferenceInfo, true)
                conferencesList.add(0, data) // Keep terminated meetings list in reverse order to always display most recent on top
            }
        } else {
            val oneHourAgo = now - 7200 // Show all conferences from 2 hours ago and forward
            for (conferenceInfo in coreContext.core.getConferenceInformationListAfterTime(oneHourAgo)) {
                if (conferenceInfo.duration == 0) continue // This isn't a scheduled conference, don't display it
                val data = ScheduledConferenceData(conferenceInfo, false)
                conferencesList.add(data)
            }
        }

        conferences.value = conferencesList
        Log.i("[Scheduled Conferences] Found ${conferencesList.size} future conferences")
    }
}

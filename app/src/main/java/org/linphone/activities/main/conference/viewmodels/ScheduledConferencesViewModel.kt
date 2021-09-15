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
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class ScheduledConferencesViewModel : ViewModel() {
    val conferences = MutableLiveData<ArrayList<ScheduledConferenceData>>()

    private val listener = object : CoreListenerStub() {
        override fun onConferenceInfoReceived(core: Core, conferenceInfo: ConferenceInfo) {
            Log.i("[Scheduled Conferences] New conference info received")
            val conferencesList = arrayListOf<ScheduledConferenceData>()
            conferencesList.addAll(conferences.value.orEmpty())
            val data = ScheduledConferenceData(conferenceInfo)
            conferencesList.add(data)
            conferences.value = conferencesList
        }
    }

    init {
        coreContext.core.addListener(listener)
        computeConferenceInfoList()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        conferences.value.orEmpty().forEach(ScheduledConferenceData::destroy)
        super.onCleared()
    }

    fun deleteConferenceInfo(data: ScheduledConferenceData) {
        val conferenceInfoList = arrayListOf<ScheduledConferenceData>()

        conferenceInfoList.addAll(conferences.value.orEmpty())
        conferenceInfoList.remove(data)

        data.delete()
        data.destroy()
        conferences.value = conferenceInfoList
    }

    private fun computeConferenceInfoList() {
        conferences.value.orEmpty().forEach(ScheduledConferenceData::destroy)

        val conferencesList = arrayListOf<ScheduledConferenceData>()

        val now = System.currentTimeMillis() / 1000 // Linphone uses time_t in seconds
        val oneHourAgo = now - 3600 // Show all conferences from 1 hour ago and forward
        for (conferenceInfo in coreContext.core.getConferenceInformationListAfterTime(oneHourAgo)) {
            val data = ScheduledConferenceData(conferenceInfo)
            conferencesList.add(data)
        }

        conferences.value = conferencesList
        Log.i("[Scheduled Conferences] Found ${conferencesList.size} future conferences")
    }
}

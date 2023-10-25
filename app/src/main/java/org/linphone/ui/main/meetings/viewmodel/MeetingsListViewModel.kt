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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel
import org.linphone.utils.TimestampUtils

class MeetingsListViewModel @UiThread constructor() : AbstractTopBarViewModel() {
    companion object {
        private const val TAG = "[Meetings List ViewModel]"
    }

    val meetings = MutableLiveData<ArrayList<MeetingModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    private var currentFilter = ""

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onConferenceInfoReceived(core: Core, conferenceInfo: ConferenceInfo) {
            Log.i("$TAG Conference info received [${conferenceInfo.uri?.asStringUriOnly()}]")
            computeMeetingsList(currentFilter)
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            computeMeetingsList(currentFilter)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun applyFilter(filter: String = currentFilter) {
        currentFilter = filter

        coreContext.postOnCoreThread {
            computeMeetingsList(filter)
        }
    }

    @WorkerThread
    private fun computeMeetingsList(filter: String) {
        val list = arrayListOf<MeetingModel>()

        var source = coreContext.core.defaultAccount?.conferenceInformationList
        if (source == null) {
            Log.e(
                "$TAG Failed to obtain conferences information list from default account, using Core"
            )
            source = coreContext.core.conferenceInformationList
        }

        var previousModel: MeetingModel? = null
        var firstMeetingOfTodayFound = false
        for (info: ConferenceInfo in source) {
            val add = if (filter.isNotEmpty()) {
                val organizerCheck = info.organizer?.asStringUriOnly()?.contains(
                    filter,
                    ignoreCase = true
                ) ?: false
                val subjectCheck = info.subject?.contains(filter, ignoreCase = true) ?: false
                val descriptionCheck = info.description?.contains(filter, ignoreCase = true) ?: false
                val participantsCheck = info.participantInfos.find {
                    it.address.asStringUriOnly().contains(filter, ignoreCase = true)
                } != null
                organizerCheck || subjectCheck || descriptionCheck || participantsCheck
            } else {
                true
            }
            if (add) {
                val model = MeetingModel(info)
                val firstMeetingOfTheDay = if (previousModel != null) {
                    previousModel.day != model.day || previousModel.dayNumber != model.dayNumber
                } else {
                    true
                }
                model.firstMeetingOfTheDay.postValue(firstMeetingOfTheDay)

                if (firstMeetingOfTheDay && model.isToday) {
                    firstMeetingOfTodayFound = true
                    model.displayTodayIndicator.postValue(true)
                }

                list.add(model)
                previousModel = model
            }
        }

        if (!firstMeetingOfTodayFound) {
            val firstMeetingAfterToday = list.find {
                TimestampUtils.isAfterToday(it.timestamp)
            }
            Log.i("$TAG $firstMeetingAfterToday")
            if (firstMeetingAfterToday != null) {
                firstMeetingAfterToday.displayTodayIndicator.postValue(true)
            }
        }

        meetings.postValue(list)
    }
}

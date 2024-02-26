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
import org.linphone.ui.main.meetings.model.MeetingListItemModel
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel

class MeetingsListViewModel @UiThread constructor() : AbstractTopBarViewModel() {
    companion object {
        private const val TAG = "[Meetings List ViewModel]"
    }

    val meetings = MutableLiveData<ArrayList<MeetingListItemModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

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
    override fun filter() {
        coreContext.postOnCoreThread {
            computeMeetingsList(currentFilter)
        }
    }

    @WorkerThread
    private fun computeMeetingsList(filter: String) {
        val list = arrayListOf<MeetingListItemModel>()

        var source = coreContext.core.defaultAccount?.conferenceInformationList
        if (source == null) {
            Log.e(
                "$TAG Failed to obtain conferences information list from default account, using Core"
            )
            source = coreContext.core.conferenceInformationList
        }

        var previousModel: MeetingModel? = null
        var meetingForTodayFound = false
        for (info: ConferenceInfo in source) {
            if (info.duration == 0) continue // This isn't a scheduled conference, don't display it
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

                // Insert "Today" fake model before the first one of today
                if (firstMeetingOfTheDay && model.isToday) {
                    list.add(MeetingListItemModel(null))
                    meetingForTodayFound = true
                }

                // If no meeting was found for today, insert "Today" fake model before the next meeting to come
                if (!meetingForTodayFound && model.isAfterToday) {
                    list.add(MeetingListItemModel(null))
                    meetingForTodayFound = true
                }

                list.add(MeetingListItemModel(model))
                previousModel = model
            }
        }

        // If no meeting was found after today, insert "Today" fake model at the end
        if (!meetingForTodayFound) {
            list.add(MeetingListItemModel(null))
        }

        meetings.postValue(list)
    }
}

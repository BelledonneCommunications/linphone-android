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
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.Address
import org.linphone.core.ConferenceInfo
import org.linphone.core.ConferenceScheduler
import org.linphone.core.ConferenceSchedulerListenerStub
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.meetings.model.MeetingListItemModel
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.ui.main.viewmodel.AbstractMainViewModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class MeetingsListViewModel
    @UiThread
    constructor() : AbstractMainViewModel() {
    companion object {
        private const val TAG = "[Meetings List ViewModel]"
    }

    val meetings = MutableLiveData<ArrayList<MeetingListItemModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val operationInProgress = MutableLiveData<Boolean>()

    val conferenceCancelledEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onConferenceInfoReceived(core: Core, conferenceInfo: ConferenceInfo) {
            Log.i("$TAG Conference info received [${conferenceInfo.uri?.asStringUriOnly()}]")
            computeMeetingsListFromLocallyStoredInfo()
        }
    }

    private val accountListener = object : AccountListenerStub() {
        @WorkerThread
        override fun onConferenceInformationUpdated(
            account: Account,
            infos: Array<ConferenceInfo>
        ) {
            Log.i(
                "$TAG Conference information updated with [${infos.size}] items for current account, reloading list"
            )
            computeMeetingsList(currentFilter, infos)
        }
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
                val params = LinphoneUtils.getChatRoomParamsToCancelMeeting()
                if (params != null) {
                    conferenceScheduler.sendInvitations(params)
                } else {
                    operationInProgress.postValue(false)
                }
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

    init {
        operationInProgress.value = false
        fetchInProgress.value = true

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            core.defaultAccount?.addListener(accountListener)

            computeMeetingsListFromLocallyStoredInfo()
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.defaultAccount?.removeListener(accountListener)
            core.removeListener(coreListener)
        }
    }

    @UiThread
    override fun filter() {
        coreContext.postOnCoreThread {
            computeMeetingsListFromLocallyStoredInfo()
        }
    }

    @UiThread
    fun cancelMeeting(conferenceInfo: ConferenceInfo) {
        coreContext.postOnCoreThread { core ->
            Log.w("$TAG Cancelling conference info [${conferenceInfo.uri?.asStringUriOnly()}]")
            operationInProgress.postValue(true)
            val conferenceScheduler = LinphoneUtils.createConferenceScheduler(
                LinphoneUtils.getDefaultAccount()
            )
            conferenceScheduler.addListener(conferenceSchedulerListener)
            conferenceScheduler.cancelConference(conferenceInfo)
        }
    }

    @WorkerThread
    private fun computeMeetingsListFromLocallyStoredInfo() {
        var source = coreContext.core.defaultAccount?.conferenceInformationList
        if (source == null) {
            Log.e(
                "$TAG Failed to obtain conferences information list from default account, using Core"
            )
            source = coreContext.core.conferenceInformationList
        }
        computeMeetingsList(currentFilter, source)
    }

    @WorkerThread
    private fun computeMeetingsList(filter: String, source: Array<ConferenceInfo>) {
        if (meetings.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

        val list = arrayListOf<MeetingListItemModel>()
        var previousModel: MeetingModel? = null
        var previousModelWeekLabel = ""
        var meetingForTodayFound = false
        Log.d("$TAG There are [${source.size}] conference info in DB")

        for (info: ConferenceInfo in source) {
            if (info.duration == 0) {
                Log.d(
                    "$TAG Skipping conference info [${info.subject}] with uri [${info.uri?.asStringUriOnly()}] because it has no duration"
                )
                continue
            } // This isn't a scheduled conference, don't display it
            val add = if (filter.isNotEmpty()) {
                val organizerCheck = info.organizer?.asStringUriOnly()?.contains(
                    filter,
                    ignoreCase = true
                ) == true
                val subjectCheck = info.subject?.contains(filter, ignoreCase = true) == true
                val descriptionCheck = info.description?.contains(filter, ignoreCase = true) == true
                val participantsCheck = info.participantInfos.find {
                    it.address.asStringUriOnly().contains(filter, ignoreCase = true)
                } != null
                organizerCheck || subjectCheck || descriptionCheck || participantsCheck
            } else {
                true
            }

            if (add) {
                Log.d("$TAG Found meeting model info [${info.subject}]")
                val model = MeetingModel(info)

                var firstMeetingOfTheWeek = previousModelWeekLabel != model.weekLabel

                val firstMeetingOfTheDay = if (previousModel != null) {
                    previousModel.day != model.day || previousModel.dayNumber != model.dayNumber
                } else {
                    true
                }
                model.firstMeetingOfTheDay.postValue(firstMeetingOfTheDay)

                if (model.isToday && !meetingForTodayFound) {
                    Log.i("$TAG Found a meeting scheduled for today")
                    meetingForTodayFound = true
                }

                // If no meeting was found for today, insert "Today" fake model before the next meeting to come,
                // but only add that fake meeting if filter is empty
                if (!meetingForTodayFound && model.isAfterToday) {
                    if (filter.isEmpty()) {
                        val todayWeekLabel = TimestampUtils.firstAndLastDayOfWeek(
                            System.currentTimeMillis(),
                            false
                        )
                        val first = previousModelWeekLabel != todayWeekLabel
                        list.add(MeetingListItemModel(null, first))
                        meetingForTodayFound = true

                        // Consider next meeting is first of the week (do not count "no meeting today" as first)
                        previousModelWeekLabel = model.weekLabel
                        firstMeetingOfTheWeek = true
                    }
                } else {
                    previousModelWeekLabel = model.weekLabel
                }

                list.add(MeetingListItemModel(model, firstMeetingOfTheWeek))
                previousModel = model
            }
        }

        // If no meeting was found after today, insert "Today" fake model at the end,
        // but only add that fake meeting if filter is empty
        if (!meetingForTodayFound && filter.isEmpty()) {
            val todayWeekLabel = TimestampUtils.firstAndLastDayOfWeek(
                System.currentTimeMillis(),
                false
            )
            val firstMeetingOfTheWeek = previousModelWeekLabel != todayWeekLabel
            list.add(MeetingListItemModel(null, firstMeetingOfTheWeek))
        }

        Log.d("$TAG We will display [${list.size}] conference info from the ones fetched from DB")
        meetings.postValue(list)
    }
}

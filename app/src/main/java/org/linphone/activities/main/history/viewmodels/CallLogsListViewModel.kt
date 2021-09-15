/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.history.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class CallLogsListViewModel : ViewModel() {
    val displayedCallLogs = MutableLiveData<List<GroupedCallLogData>>()

    val filter = MutableLiveData<CallLogsFilter>()

    val showConferencesFilter = MutableLiveData<Boolean>()

    val contactsUpdatedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val callLogs = MutableLiveData<ArrayList<GroupedCallLogData>>()
    private val missedCallLogs = MutableLiveData<ArrayList<GroupedCallLogData>>()
    private val conferenceCallLogs = MutableLiveData<ArrayList<GroupedCallLogData>>()

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            updateCallLogs()
        }
    }

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Call Logs] Contacts have changed")
            contactsUpdatedEvent.value = Event(true)
        }
    }

    init {
        filter.value = CallLogsFilter.ALL
        updateCallLogs()

        showConferencesFilter.value = LinphoneUtils.isRemoteConferencingAvailable()

        coreContext.core.addListener(listener)
        coreContext.contactsManager.addListener(contactsUpdatedListener)
    }

    override fun onCleared() {
        callLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)
        missedCallLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)
        conferenceCallLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)
        displayedCallLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)

        coreContext.contactsManager.removeListener(contactsUpdatedListener)
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun showAllCallLogs() {
        filter.value = CallLogsFilter.ALL
        applyFilter()
    }

    fun showOnlyMissedCallLogs() {
        filter.value = CallLogsFilter.MISSED
        applyFilter()
    }

    fun showOnlyConferenceCallLogs() {
        filter.value = CallLogsFilter.CONFERENCE
        applyFilter()
    }

    fun deleteCallLogGroup(callLog: GroupedCallLogData?) {
        if (callLog != null) {
            for (log in callLog.callLogs) {
                coreContext.core.removeCallLog(log)
            }
        }

        updateCallLogs()
    }

    fun deleteCallLogGroups(listToDelete: ArrayList<GroupedCallLogData>) {
        for (callLog in listToDelete) {
            for (log in callLog.callLogs) {
                coreContext.core.removeCallLog(log)
            }
        }

        updateCallLogs()
    }

    private fun computeCallLogs(callLogs: Array<CallLog>, missed: Boolean, conference: Boolean): ArrayList<GroupedCallLogData> {
        var previousCallLogGroup: GroupedCallLogData? = null
        val list = arrayListOf<GroupedCallLogData>()

        for (callLog in callLogs) {
            if ((!missed && !conference) || (missed && LinphoneUtils.isCallLogMissed(callLog)) || (conference && callLog.wasConference())) {
                if (previousCallLogGroup == null) {
                    previousCallLogGroup = GroupedCallLogData(callLog)
                } else if (previousCallLogGroup.lastCallLog.localAddress.weakEqual(callLog.localAddress) &&
                    previousCallLogGroup.lastCallLog.remoteAddress.equal(callLog.remoteAddress)
                ) {
                    if (TimestampUtils.isSameDay(
                            previousCallLogGroup.lastCallLog.startDate,
                            callLog.startDate
                        )
                    ) {
                        previousCallLogGroup.callLogs.add(callLog)
                        previousCallLogGroup.lastCallLog = callLog
                    } else {
                        list.add(previousCallLogGroup)
                        previousCallLogGroup = GroupedCallLogData(callLog)
                    }
                } else {
                    list.add(previousCallLogGroup)
                    previousCallLogGroup = GroupedCallLogData(callLog)
                }
            }
        }
        if (previousCallLogGroup != null && !list.contains(previousCallLogGroup)) {
            list.add(previousCallLogGroup)
        }

        return list
    }

    private fun updateCallLogs() {
        callLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)
        missedCallLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)
        conferenceCallLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)

        val allCallLogs = coreContext.core.callLogs
        callLogs.value = computeCallLogs(allCallLogs, false, false)
        missedCallLogs.value = computeCallLogs(allCallLogs, true, false)
        conferenceCallLogs.value = computeCallLogs(allCallLogs, false, true)
        applyFilter()
    }

    private fun applyFilter() {
        displayedCallLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)
        val displayedList = arrayListOf<GroupedCallLogData>()

        when (filter.value) {
            CallLogsFilter.MISSED -> displayedList.addAll(missedCallLogs.value.orEmpty())
            CallLogsFilter.CONFERENCE -> displayedList.addAll(conferenceCallLogs.value.orEmpty())
            else -> displayedList.addAll(callLogs.value.orEmpty())
        }

        displayedCallLogs.value = displayedList
    }
}

enum class CallLogsFilter {
    ALL,
    MISSED,
    CONFERENCE
}

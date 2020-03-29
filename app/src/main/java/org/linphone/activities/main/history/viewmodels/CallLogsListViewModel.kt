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
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class CallLogsListViewModel : ViewModel() {
    val callLogs = MutableLiveData<ArrayList<CallLog>>()
    val missedCallLogs = MutableLiveData<ArrayList<CallLog>>()

    val missedCallLogsSelected = MutableLiveData<Boolean>()

    val contactsUpdatedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

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
        missedCallLogsSelected.value = false
        updateCallLogs()

        coreContext.core.addListener(listener)
        coreContext.contactsManager.addListener(contactsUpdatedListener)
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun deleteCallLogs(listToDelete: ArrayList<CallLog>) {
        val list = arrayListOf<CallLog>()
        list.addAll(callLogs.value.orEmpty())

        val missedList = arrayListOf<CallLog>()
        missedList.addAll(missedCallLogs.value.orEmpty())

        for (callLog in listToDelete) {
            coreContext.core.removeCallLog(callLog)
            list.remove(callLog)
            missedList.remove(callLog)
        }

        callLogs.value = list
        missedCallLogs.value = missedList
    }

    private fun updateCallLogs() {
        val list = arrayListOf<CallLog>()
        val missedList = arrayListOf<CallLog>()

        for (callLog in coreContext.core.callLogs) {
            list.add(callLog)
            if (callLog.status == Call.Status.Missed) missedList.add(callLog)
        }

        callLogs.value = list
        missedCallLogs.value = missedList
    }
}

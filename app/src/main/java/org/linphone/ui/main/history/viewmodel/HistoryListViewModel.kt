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
package org.linphone.ui.main.history.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactsManager
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.GlobalState
import org.linphone.core.tools.Log
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.ui.main.viewmodel.AbstractMainViewModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class HistoryListViewModel @UiThread constructor() : AbstractMainViewModel() {
    companion object {
        private const val TAG = "[History List ViewModel]"
    }

    val callLogs = MutableLiveData<ArrayList<CallLogModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val historyInsertedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onGlobalStateChanged(core: Core, state: GlobalState?, message: String) {
            if (state == GlobalState.On) {
                Log.i("$TAG Core just started, fetching history")
                computeCallLogsList(currentFilter)
            }
        }

        @WorkerThread
        override fun onCallLogUpdated(core: Core, callLog: CallLog) {
            Log.i("$TAG A call log was created, updating list")
            computeCallLogsList(currentFilter)
            historyInsertedEvent.postValue(Event(true))
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            computeCallLogsList(currentFilter)
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        fetchInProgress.value = true

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.addListener(contactsListener)
            core.addListener(coreListener)

            computeCallLogsList(currentFilter)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.removeListener(contactsListener)
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun removeAllCallLogs() {
        coreContext.postOnCoreThread { core ->
            val account = LinphoneUtils.getDefaultAccount()
            if (account != null) {
                account.clearCallLogs()
            } else {
                for (callLog in core.callLogs) {
                    core.removeCallLog(callLog)
                }
            }

            historyDeletedEvent.postValue(Event(true))
            computeCallLogsList(currentFilter)
        }
    }

    @UiThread
    override fun filter() {
        coreContext.postOnCoreThread {
            computeCallLogsList(currentFilter)
        }
    }

    @WorkerThread
    private fun computeCallLogsList(filter: String) {
        if (coreContext.core.globalState != GlobalState.On) {
            Log.e("$TAG Core isn't ON yet, do not attempt to get calls history")
            return
        }

        if (callLogs.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

        val list = arrayListOf<CallLogModel>()
        var count = 0

        val account = LinphoneUtils.getDefaultAccount()
        val logs = account?.callLogs ?: coreContext.core.callLogs
        for (callLog in logs) {
            if (callLog.remoteAddress.asStringUriOnly().contains(filter)) {
                val model = CallLogModel(callLog)
                list.add(model)
                count += 1
            }

            if (count == 20) {
                callLogs.postValue(list)
            }
        }

        Log.i("$TAG Fetched [${list.size}] call log(s)")
        callLogs.postValue(list)
    }
}

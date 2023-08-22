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
package org.linphone.ui.main.calls.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.ui.main.calls.model.CallLogModel
import org.linphone.utils.Event

class CallsListViewModel @UiThread constructor() : ViewModel() {
    val callLogs = MutableLiveData<ArrayList<CallLogModel>>()

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var currentFilter = ""

    private val coreListener = object : CoreListenerStub() {
        override fun onCallLogUpdated(core: Core, callLog: CallLog) {
            computeCallLogsList(currentFilter)
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            computeCallLogsList(currentFilter)
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
            computeCallLogsList(currentFilter)
        }
    }

    @UiThread
    fun removeAllCallLogs() {
        coreContext.postOnCoreThread { core ->
            for (callLog in core.callLogs) {
                core.removeCallLog(callLog)
            }
            historyDeletedEvent.postValue(Event(true))
            computeCallLogsList(currentFilter)
        }
    }

    @WorkerThread
    private fun computeCallLogsList(filter: String) {
        val list = arrayListOf<CallLogModel>()

        // TODO : filter depending on currently selected account
        // TODO : Add support for call logs in magic search
        for (callLog in coreContext.core.callLogs) {
            val model = CallLogModel(callLog)
            list.add(model)
        }

        callLogs.postValue(list)
    }
}

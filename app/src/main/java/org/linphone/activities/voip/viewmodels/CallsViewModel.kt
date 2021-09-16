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
package org.linphone.activities.voip.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.voip.data.CallData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class CallsViewModel : ViewModel() {
    val currentCallData = MutableLiveData<CallData>()

    val callsData = MutableLiveData<List<CallData>>()

    val noMoreCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            Log.i("[Calls] Call state changed: $state")

            val currentCall = core.currentCall
            if (currentCall != null && currentCallData.value?.call != currentCall) {
                currentCallData.value?.destroy()
                val viewModel = CallData(currentCall)
                currentCallData.value = viewModel
            }

            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                updateCallsList()

                if (core.callsNb == 0) {
                    currentCallData.value?.destroy()
                    noMoreCallEvent.value = Event(true)
                }
            } else if (state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia || state == Call.State.OutgoingInit) {
                updateCallsList()
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        val currentCall = coreContext.core.currentCall
        if (currentCall != null) {
            currentCallData.value?.destroy()

            val viewModel = CallData(currentCall)
            currentCallData.value = viewModel
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        currentCallData.value?.destroy()
        callsData.value.orEmpty().forEach(CallData::destroy)

        super.onCleared()
    }

    private fun updateCallsList() {
        callsData.value.orEmpty().forEach(CallData::destroy)
        val calls = arrayListOf<CallData>()

        for (call in coreContext.core.calls) {
            val data: CallData = if (currentCallData.value?.call == call) {
                currentCallData.value!!
            } else {
                CallData(call)
            }
            calls.add(data)
        }

        callsData.value = calls
    }
}

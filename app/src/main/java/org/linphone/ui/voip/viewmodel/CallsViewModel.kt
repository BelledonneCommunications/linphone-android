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
package org.linphone.ui.voip.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class CallsViewModel : ViewModel() {
    val goToActiveCallEvent = MutableLiveData<Event<Boolean>>()

    val showIncomingCallEvent = MutableLiveData<Event<Boolean>>()

    val showOutgoingCallEvent = MutableLiveData<Event<Boolean>>()

    val noMoreCallEvent = MutableLiveData<Event<Boolean>>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("[Calls ViewModel] No more call, leaving VoIP activity")
            noMoreCallEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (call == core.currentCall || core.currentCall == null) {
                when (call.state) {
                    Call.State.Connected -> {
                        goToActiveCallEvent.postValue(Event(true))
                    }
                    else -> {
                    }
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            if (core.callsNb > 0) {
                val currentCall = core.currentCall ?: core.calls.first()

                when (currentCall.state) {
                    Call.State.Connected, Call.State.StreamsRunning, Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote, Call.State.UpdatedByRemote, Call.State.Updating -> {
                        goToActiveCallEvent.postValue(Event(true))
                    }
                    Call.State.OutgoingInit, Call.State.OutgoingRinging, Call.State.OutgoingProgress, Call.State.OutgoingEarlyMedia -> {
                        showOutgoingCallEvent.postValue(Event(true))
                    }
                    Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                        showIncomingCallEvent.postValue(Event(true))
                    }
                    else -> {
                    }
                }
            } else {
                noMoreCallEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }
}
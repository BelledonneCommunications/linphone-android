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
import org.linphone.core.Alert
import org.linphone.core.AlertListenerStub
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.voip.model.CallModel
import org.linphone.utils.Event

class CallsViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Calls ViewModel]"

        private const val ALERT_NETWORK_TYPE_KEY = "network-type"
        private const val ALERT_NETWORK_TYPE_WIFI = "wifi"
        private const val ALERT_NETWORK_TYPE_CELLULAR = "mobile"
    }

    val calls = MutableLiveData<ArrayList<CallModel>>()

    val goToActiveCallEvent = MutableLiveData<Event<Boolean>>()

    val showIncomingCallEvent = MutableLiveData<Event<Boolean>>()

    val showOutgoingCallEvent = MutableLiveData<Event<Boolean>>()

    val noMoreCallEvent = MutableLiveData<Event<Boolean>>()

    val showLowWifiSignalEvent = MutableLiveData<Event<Boolean>>()

    val showLowCellularSignalEvent = MutableLiveData<Event<Boolean>>()

    private val alertListener = object : AlertListenerStub() {
        @WorkerThread
        override fun onTerminated(alert: Alert) {
            val remote = alert.call.remoteAddress.asStringUriOnly()
            Log.w("$TAG Alert of type [${alert.type}] dismissed for call from [$remote]")
            alert.removeListener(this)

            if (alert.type == Alert.Type.QoSLowSignal) {
                when (val signalType = alert.informations?.getString(ALERT_NETWORK_TYPE_KEY)) {
                    ALERT_NETWORK_TYPE_WIFI -> {
                        Log.i("$TAG Wi-Fi signal no longer low")
                        showLowWifiSignalEvent.postValue(Event(false))
                    }
                    ALERT_NETWORK_TYPE_CELLULAR -> {
                        Log.i("$TAG Cellular signal no longer low")
                        showLowCellularSignalEvent.postValue(Event(false))
                    }
                    else -> {
                        Log.w(
                            "$TAG Unexpected type of signal [$signalType] found in alert information"
                        )
                    }
                }
            }
        }
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG No more call, leaving VoIP activity")
            noMoreCallEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            // Update calls list if needed
            val found = calls.value.orEmpty().find {
                it.call == call
            }
            if (found == null) {
                if (state != Call.State.End && state != Call.State.Released && state != Call.State.Error) {
                    Log.i(
                        "$TAG Found a call [${call.remoteAddress.asStringUriOnly()}] not yet in calls list, let's add it"
                    )
                    val list = arrayListOf<CallModel>()
                    list.addAll(calls.value.orEmpty())
                    val model = CallModel(call)
                    list.add(model)
                    calls.postValue(list)
                }
            } else {
                if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                    Log.i(
                        "$TAG Call [${call.remoteAddress.asStringUriOnly()}] shouldn't be in calls list anymore, let's remove it"
                    )
                    val list = arrayListOf<CallModel>()
                    list.addAll(calls.value.orEmpty())
                    list.remove(found)
                    calls.postValue(list)
                    found.destroy()
                }
            }

            if (call == core.currentCall || core.currentCall == null) {
                Log.i(
                    "$TAG Current call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]"
                )
                when (call.state) {
                    Call.State.Connected -> {
                        goToActiveCallEvent.postValue(Event(true))
                    }
                    else -> {
                    }
                }
            }
        }

        @WorkerThread
        override fun onNewAlertTriggered(core: Core, alert: Alert) {
            val remote = alert.call.remoteAddress.asStringUriOnly()
            Log.w("$TAG Alert of type [${alert.type}] triggered for call from [$remote]")
            alert.addListener(alertListener)

            if (alert.type == Alert.Type.QoSLowSignal) {
                when (val networkType = alert.informations?.getString(ALERT_NETWORK_TYPE_KEY)) {
                    ALERT_NETWORK_TYPE_WIFI -> {
                        Log.i("$TAG Triggered low signal alert is for Wi-Fi")
                        showLowWifiSignalEvent.postValue(Event(true))
                    }
                    ALERT_NETWORK_TYPE_CELLULAR -> {
                        Log.i("$TAG Triggered low signal alert is for cellular")
                        showLowCellularSignalEvent.postValue(Event(true))
                    }
                    else -> {
                        Log.w(
                            "$TAG Unexpected type of signal [$networkType] found in alert information"
                        )
                    }
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            if (core.callsNb > 0) {
                val list = arrayListOf<CallModel>()
                for (call in core.calls) {
                    val model = CallModel(call)
                    list.add(model)
                }
                calls.postValue(list)

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
                    else -> {}
                }
            } else {
                Log.w("$TAG No call found, leaving VoIP activity")
                noMoreCallEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            calls.value.orEmpty().forEach(CallModel::destroy)
            core.removeListener(coreListener)
        }
    }
}

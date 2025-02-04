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
package org.linphone.ui.call.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.call.model.CallModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CallsViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Calls ViewModel]"
    }

    val calls = MutableLiveData<ArrayList<CallModel>>()

    val callsExceptCurrentOne = MutableLiveData<ArrayList<CallModel>>()

    val callsCount = MutableLiveData<Int>()

    val showTopBar = MutableLiveData<Boolean>()

    val goToActiveCallEvent = MutableLiveData<Event<Boolean>>()

    val showIncomingCallEvent = MutableLiveData<Event<Boolean>>()

    val showOutgoingCallEvent = MutableLiveData<Event<Boolean>>()

    val noCallFoundEvent = MutableLiveData<Event<Boolean>>()

    val callsTopBarLabel = MutableLiveData<String>()

    val callsTopBarIcon = MutableLiveData<Int>()

    val callsTopBarStatus = MutableLiveData<String>()

    val goToCallsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")

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
                    callsCount.postValue(list.size)
                    Log.i("$TAG There is [${list.size}] calls at this time")
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
                    callsCount.postValue(list.size)
                    Log.i("$TAG There is [${list.size}] calls at this time")
                    found.destroy()
                }
            }

            updateOtherCallsInfo()

            // Update currently displayed fragment according to call state
            if (call == core.currentCall || core.currentCall == null) {
                Log.i(
                    "$TAG Current call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]"
                )
                when (call.state) {
                    Call.State.Connected -> {
                        goToActiveCallEvent.postValue(Event(call.conference == null))
                    }
                    else -> {
                    }
                }
            }

            if (LinphoneUtils.isCallIncoming(call.state)) {
                Log.i("$TAG Asking activity to show incoming call fragment")
                showIncomingCallEvent.postValue(Event(true))
            } else if (LinphoneUtils.isCallEnding(call.state)) {
                if (core.callsNb > 0) {
                    val newCurrentCall = core.currentCall ?: core.calls.firstOrNull()
                    if (newCurrentCall != null) {
                        if (LinphoneUtils.isCallIncoming(newCurrentCall.state)) {
                            Log.i("$TAG Asking activity to show incoming call fragment")
                            showIncomingCallEvent.postValue(Event(true))
                        } else {
                            if (newCurrentCall.conference == null) {
                                Log.i("$TAG Asking activity to show active call fragment")
                                goToActiveCallEvent.postValue(Event(true))
                            } else {
                                Log.i(
                                    "$TAG Asking activity to show active conference call fragment"
                                )
                                goToActiveCallEvent.postValue(Event(false))
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        showTopBar.value = false

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            if (core.callsNb > 0) {
                val list = arrayListOf<CallModel>()
                for (call in core.calls) {
                    val model = CallModel(call)
                    list.add(model)
                }
                calls.postValue(list)
                callsCount.postValue(list.size)
                Log.i("$TAG There is [${list.size}] calls")

                val currentCall = core.currentCall ?: core.calls.first()
                Log.i("$TAG Current call is [${currentCall.remoteAddress.asStringUriOnly()}]")

                when (currentCall.state) {
                    Call.State.Connected, Call.State.StreamsRunning, Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote, Call.State.UpdatedByRemote, Call.State.Updating -> {
                        goToActiveCallEvent.postValue(Event(currentCall.conference == null))
                    }
                    Call.State.OutgoingInit, Call.State.OutgoingRinging, Call.State.OutgoingProgress, Call.State.OutgoingEarlyMedia -> {
                        showOutgoingCallEvent.postValue(Event(true))
                    }
                    Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                        showIncomingCallEvent.postValue(Event(true))
                    }
                    else -> {}
                }

                updateOtherCallsInfo()
            } else {
                Log.w("$TAG No call found, leaving Call activity")
                noCallFoundEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            calls.value.orEmpty().forEach(CallModel::destroy)
            callsCount.postValue(0)
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun topBarClicked() {
        coreContext.postOnCoreThread { core ->
            if (core.callsNb == 1) {
                val currentCall = core.currentCall ?: core.calls.first()
                goToActiveCallEvent.postValue(Event(currentCall.conference == null))
            } else {
                goToCallsListEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun mergeCallsIntoConference() {
        coreContext.postOnCoreThread { core ->
            val callsCount = core.callsNb
            val defaultAccount = LinphoneUtils.getDefaultAccount()
            val subject = if (defaultAccount != null && defaultAccount.params.audioVideoConferenceFactoryAddress != null) {
                Log.i("$TAG Merging [$callsCount] calls into a remotely hosted conference")
                AppUtils.getString(R.string.conference_remotely_hosted_title)
            } else {
                Log.i("$TAG Merging [$callsCount] calls into a locally hosted conference")
                AppUtils.getString(R.string.conference_locally_hosted_title)
            }

            val conference = LinphoneUtils.createGroupCall(defaultAccount, subject)
            if (conference == null) {
                Log.e("$TAG Failed to create conference!")
                showRedToast(R.string.conference_failed_to_merge_calls_into_conference_toast, R.drawable.warning_circle)
            } else {
                conference.addParticipants(core.calls)
            }
        }
    }

    @WorkerThread
    private fun updateOtherCallsInfo() {
        val core = coreContext.core

        callsExceptCurrentOne.value.orEmpty().forEach(CallModel::destroy)
        val list = arrayListOf<CallModel>()
        for (call in core.calls) {
            if (call != core.currentCall) {
                list.add(CallModel(call))
            }
        }
        callsExceptCurrentOne.postValue(list)

        if (core.callsNb > 1) {
            showTopBar.postValue(true)
            if (core.callsNb == 2) {
                val found = core.calls.find {
                    it.state == Call.State.Paused
                }
                callsTopBarIcon.postValue(R.drawable.phone_pause)
                if (found != null) {
                    val remoteAddress = found.callLog.remoteAddress
                    val conference = found.conference
                    if (conference != null) {
                        callsTopBarLabel.postValue(conference.subject)
                    } else {
                        val contact = coreContext.contactsManager.findContactByAddress(
                            remoteAddress
                        )
                        callsTopBarLabel.postValue(
                            contact?.name ?: LinphoneUtils.getDisplayName(remoteAddress)
                        )
                    }
                    callsTopBarStatus.postValue(LinphoneUtils.callStateToString(found.state))
                } else {
                    Log.e("$TAG Failed to find a paused call")
                }
            } else {
                callsTopBarLabel.postValue(
                    AppUtils.getFormattedString(R.string.calls_paused_count_label, core.callsNb - 1)
                )
                callsTopBarStatus.postValue("") // TODO: improve ?
            }
        } else {
            if (core.callsNb == 1) {
                callsTopBarIcon.postValue(R.drawable.phone)

                val call = core.calls.first()
                val conference = call.conference
                if (conference != null) {
                    callsTopBarLabel.postValue(conference.subject)
                } else {
                    val remoteAddress = call.callLog.remoteAddress
                    val contact = coreContext.contactsManager.findContactByAddress(
                        remoteAddress
                    )
                    callsTopBarLabel.postValue(
                        contact?.name ?: LinphoneUtils.getDisplayName(remoteAddress)
                    )
                }
                callsTopBarStatus.postValue(LinphoneUtils.callStateToString(call.state))
            }
        }
    }
}

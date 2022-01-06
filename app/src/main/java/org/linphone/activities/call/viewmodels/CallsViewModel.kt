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
package org.linphone.activities.call.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class CallsViewModel : ViewModel() {
    val currentCallViewModel = MutableLiveData<CallViewModel>()

    val noActiveCall = MutableLiveData<Boolean>()

    val callPausedByRemote = MutableLiveData<Boolean>()

    val pausedCalls = MutableLiveData<ArrayList<CallViewModel>>()

    val noMoreCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val callUpdateEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val askWriteExternalStoragePermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            Log.i("[Calls VM] Call state changed: $state")
            callPausedByRemote.value = (state == Call.State.PausedByRemote) and (call.conference == null)

            val currentCall = core.currentCall
            noActiveCall.value = currentCall == null
            if (currentCall == null) {
                currentCallViewModel.value?.destroy()
            } else if (currentCallViewModel.value?.call != currentCall) {
                val viewModel = CallViewModel(currentCall)
                currentCallViewModel.value = viewModel
            }

            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                if (core.callsNb == 0) {
                    noMoreCallEvent.value = Event(true)
                } else {
                    removeCallFromPausedListIfPresent(call)
                }
            } else if (state == Call.State.Paused) {
                addCallToPausedList(call)
            } else if (state == Call.State.Resuming) {
                removeCallFromPausedListIfPresent(call)
            } else if (call.state == Call.State.UpdatedByRemote) {
                // If the correspondent asks to turn on video while audio call,
                // defer update until user has chosen whether to accept it or not
                val remoteVideo = call.remoteParams?.isVideoEnabled ?: false
                val localVideo = call.currentParams.isVideoEnabled
                val autoAccept = call.core.videoActivationPolicy.automaticallyAccept
                if (remoteVideo && !localVideo && !autoAccept) {
                    if (coreContext.core.isVideoCaptureEnabled || coreContext.core.isVideoDisplayEnabled) {
                        call.deferUpdate()
                        callUpdateEvent.value = Event(call)
                    } else {
                        coreContext.answerCallVideoUpdateRequest(call, false)
                    }
                }
            } else if (state == Call.State.StreamsRunning) {
                callUpdateEvent.value = Event(call)
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        val currentCall = coreContext.core.currentCall
        noActiveCall.value = currentCall == null
        if (currentCall != null) {
            currentCallViewModel.value?.destroy()

            val viewModel = CallViewModel(currentCall)
            currentCallViewModel.value = viewModel
        }

        callPausedByRemote.value = currentCall?.state == Call.State.PausedByRemote

        for (call in coreContext.core.calls) {
            if (call.state == Call.State.Paused || call.state == Call.State.Pausing) {
                addCallToPausedList(call)
            }
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun answerCallVideoUpdateRequest(call: Call, accept: Boolean) {
        coreContext.answerCallVideoUpdateRequest(call, accept)
    }

    fun takeScreenshot() {
        if (!PermissionHelper.get().hasWriteExternalStoragePermission()) {
            askWriteExternalStoragePermissionEvent.value = Event(true)
        } else {
            currentCallViewModel.value?.takeScreenshot()
        }
    }

    private fun addCallToPausedList(call: Call) {
        if (call.conference != null) return // Conference will be displayed as paused, no need to display the call as well

        val list = arrayListOf<CallViewModel>()
        list.addAll(pausedCalls.value.orEmpty())

        for (pausedCallViewModel in list) {
            if (pausedCallViewModel.call == call) {
                return
            }
        }

        val viewModel = CallViewModel(call)
        list.add(viewModel)
        pausedCalls.value = list
    }

    private fun removeCallFromPausedListIfPresent(call: Call) {
        val list = arrayListOf<CallViewModel>()
        list.addAll(pausedCalls.value.orEmpty())

        for (pausedCallViewModel in list) {
            if (pausedCallViewModel.call == call) {
                pausedCallViewModel.destroy()
                list.remove(pausedCallViewModel)
                break
            }
        }

        pausedCalls.value = list
    }
}

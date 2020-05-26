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
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.GenericContactViewModel
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class CallViewModelFactory(private val call: Call) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return CallViewModel(call) as T
    }
}

open class CallViewModel(val call: Call) : GenericContactViewModel(call.remoteAddress) {
    val address: String by lazy {
        call.remoteAddress.clean() // To remove gruu if any
        call.remoteAddress.asStringUriOnly()
    }

    val isPaused = MutableLiveData<Boolean>()

    val callEndedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val callConnectedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            if (call != this@CallViewModel.call) return

            isPaused.value = state == Call.State.Paused

            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                callEndedEvent.value = Event(true)

                if (state == Call.State.Error) {
                    Log.e("[Call View Model] Error state reason is ${call.reason}")
                }
            } else if (call.state == Call.State.Connected) {
                callConnectedEvent.value = Event(true)
            }
        }
    }

    init {
        call.addListener(listener)

        isPaused.value = call.state == Call.State.Paused
    }

    override fun onCleared() {
        call.removeListener(listener)

        super.onCleared()
    }

    fun terminateCall() {
        coreContext.terminateCall(call)
    }

    fun pause() {
        call.pause()
    }

    fun resume() {
        call.resume()
    }

    fun removeFromConference() {
        if (call.conference != null) {
            call.conference.removeParticipant(call.remoteAddress)
            if (call.core.conferenceSize <= 1) call.core.leaveConference()
        }
    }
}

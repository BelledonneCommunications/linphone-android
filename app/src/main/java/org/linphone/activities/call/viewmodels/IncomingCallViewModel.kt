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
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.*
import org.linphone.utils.Event

class IncomingCallViewModelFactory(private val call: Call) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return IncomingCallViewModel(call) as T
    }
}

class IncomingCallViewModel(call: Call) : CallViewModel(call) {
    val screenLocked = MutableLiveData<Boolean>()

    val earlyMediaVideoEnabled = MutableLiveData<Boolean>()

    val inviteWithVideo = MutableLiveData<Boolean>()

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (core.callsNb == 0) {
                callEndedEvent.value = Event(true)
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        screenLocked.value = false
        inviteWithVideo.value = call.remoteParams?.isVideoEnabled == true && coreContext.core.videoActivationPolicy.automaticallyAccept
        earlyMediaVideoEnabled.value = corePreferences.acceptEarlyMedia &&
            call.state == Call.State.IncomingEarlyMedia &&
            call.currentParams.isVideoEnabled
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun answer(doAction: Boolean) {
        if (doAction) coreContext.answerCall(call)
    }

    fun decline(doAction: Boolean) {
        if (doAction) coreContext.declineCall(call)
    }
}

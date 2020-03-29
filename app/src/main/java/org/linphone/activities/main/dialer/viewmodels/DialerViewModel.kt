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
package org.linphone.activities.main.dialer.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.dialer.NumpadDigitListener
import org.linphone.core.*

class DialerViewModel : ViewModel() {
    val enteredUri = MutableLiveData<String>()

    val atLeastOneCall = MutableLiveData<Boolean>()

    val transferVisibility = MutableLiveData<Boolean>()

    val onKeyClick: NumpadDigitListener = object : NumpadDigitListener {
        override fun handleClick(key: Char) {
            enteredUri.value += key.toString()
            coreContext.core.playDtmf(key, 1)
        }

        override fun handleLongClick(key: Char): Boolean {
            if (key == '1') {
                val voiceMailUri = corePreferences.voiceMailUri
                if (voiceMailUri != null) {
                    coreContext.startCall(voiceMailUri)
                }
            } else {
                enteredUri.value += key.toString()
            }
            return true
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String?
        ) {
            atLeastOneCall.value = core.callsNb > 0
        }
    }

    init {
        coreContext.core.addListener(listener)

        enteredUri.value = ""
        atLeastOneCall.value = coreContext.core.callsNb > 0
        transferVisibility.value = false
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun eraseLastChar() {
        enteredUri.value = enteredUri.value?.dropLast(1)
    }

    fun eraseAll(): Boolean {
        enteredUri.value = ""
        return true
    }

    fun startCall() {
        val addressToCall = enteredUri.value.orEmpty()
        if (addressToCall.isNotEmpty()) {
            coreContext.startCall(addressToCall)
        } else {
            setLastOutgoingCallAddress()
        }
    }

    fun transferCall() {
        val addressToCall = enteredUri.value.orEmpty()
        if (addressToCall.isNotEmpty()) {
            coreContext.transferCallTo(addressToCall)
        } else {
            setLastOutgoingCallAddress()
        }
    }

    private fun setLastOutgoingCallAddress() {
        val callLog = coreContext.core.lastOutgoingCallLog
        if (callLog != null) {
            enteredUri.value = callLog.remoteAddress.asStringUriOnly()
        }
    }
}

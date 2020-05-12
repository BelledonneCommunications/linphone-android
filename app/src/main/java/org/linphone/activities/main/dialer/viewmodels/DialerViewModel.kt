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
import org.linphone.core.tools.Log

class DialerViewModel : ViewModel() {
    val enteredUri = MutableLiveData<String>()

    val atLeastOneCall = MutableLiveData<Boolean>()

    val transferVisibility = MutableLiveData<Boolean>()

    val showPreview = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    private var addressWaitingNetworkToBeCalled: String? = null
    private var timeAtWitchWeTriedToCall: Long = 0

    val onKeyClick: NumpadDigitListener = object : NumpadDigitListener {
        override fun handleClick(key: Char) {
            enteredUri.value += key.toString()
            if (coreContext.core.callsNb == 0) {
                coreContext.core.playDtmf(key, 1)
            }
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

        override fun onNetworkReachable(core: Core, reachable: Boolean) {
            if (reachable && addressWaitingNetworkToBeCalled.orEmpty().isNotEmpty()) {
                val now = System.currentTimeMillis()
                if (now - timeAtWitchWeTriedToCall > 1000) {
                    Log.e("[Dialer] More than 1 second has passed waiting for network, abort auto call to $addressWaitingNetworkToBeCalled")
                    enteredUri.value = addressWaitingNetworkToBeCalled
                } else {
                    Log.i("[Dialer] Network is available, continue auto call to $addressWaitingNetworkToBeCalled")
                    coreContext.startCall(addressWaitingNetworkToBeCalled.orEmpty())
                }

                addressWaitingNetworkToBeCalled = null
                timeAtWitchWeTriedToCall = 0
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        enteredUri.value = ""
        atLeastOneCall.value = coreContext.core.callsNb > 0
        transferVisibility.value = false

        showSwitchCamera.value = coreContext.showSwitchCameraButton()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun updateShowVideoPreview() {
        val videoPreview = corePreferences.videoPreview
        showPreview.value = videoPreview
        coreContext.core.enableVideoPreview(videoPreview)
    }

    fun eraseLastChar() {
        enteredUri.value = enteredUri.value?.dropLast(1)
    }

    fun eraseAll(): Boolean {
        enteredUri.value = ""
        return true
    }

    fun directCall(to: String) {
        if (coreContext.core.isNetworkReachable) {
            coreContext.startCall(to)
        } else {
            Log.w("[Dialer] Network isnt't reachable at the time, wait for network to start call (happens mainly when app is cold started)")
            timeAtWitchWeTriedToCall = System.currentTimeMillis()
            addressWaitingNetworkToBeCalled = to
        }
    }

    fun startCall() {
        val addressToCall = enteredUri.value.orEmpty()
        if (addressToCall.isNotEmpty()) {
            coreContext.startCall(addressToCall)
            eraseAll()
        } else {
            setLastOutgoingCallAddress()
        }
    }

    fun transferCall() {
        val addressToCall = enteredUri.value.orEmpty()
        if (addressToCall.isNotEmpty()) {
            coreContext.transferCallTo(addressToCall)
            eraseAll()
        } else {
            setLastOutgoingCallAddress()
        }
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }

    private fun setLastOutgoingCallAddress() {
        val callLog = coreContext.core.lastOutgoingCallLog
        if (callLog != null) {
            enteredUri.value = callLog.remoteAddress.asStringUriOnly()
        }
    }
}

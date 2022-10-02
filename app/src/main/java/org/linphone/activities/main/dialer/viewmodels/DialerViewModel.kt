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

import android.content.Context
import android.os.Vibrator
import android.text.Editable
import android.widget.EditText
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.dialer.NumpadDigitListener
import org.linphone.activities.main.viewmodels.LogsUploadViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class DialerViewModel : LogsUploadViewModel() {
    val enteredUri = MutableLiveData<String>()

    val atLeastOneCall = MutableLiveData<Boolean>()

    val transferVisibility = MutableLiveData<Boolean>()

    val showPreview = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val autoInitiateVideoCalls = MutableLiveData<Boolean>()

    val scheduleConferenceAvailable = MutableLiveData<Boolean>()

    val updateAvailableEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val vibrator = coreContext.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var addressWaitingNetworkToBeCalled: String? = null
    private var timeAtWitchWeTriedToCall: Long = 0

    private var enteredUriCursorPosition: Int = 0

    val onKeyClick: NumpadDigitListener = object : NumpadDigitListener {
        override fun handleClick(key: Char) {
            val sb: StringBuilder = StringBuilder(enteredUri.value)
            try {
                sb.insert(enteredUriCursorPosition, key.toString())
            } catch (ioobe: IndexOutOfBoundsException) {
                sb.insert(sb.length, key.toString())
            }
            enteredUri.value = sb.toString()

            if (vibrator.hasVibrator() && corePreferences.dtmfKeypadVibration) {
                Compatibility.eventVibration(vibrator)
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
            message: String
        ) {
            atLeastOneCall.value = core.callsNb > 0
        }

        override fun onNetworkReachable(core: Core, reachable: Boolean) {
            val address = addressWaitingNetworkToBeCalled.orEmpty()
            if (reachable && address.isNotEmpty()) {
                val now = System.currentTimeMillis()
                if (now - timeAtWitchWeTriedToCall > 1000) {
                    Log.e("[Dialer] More than 1 second has passed waiting for network, abort auto call to $address")
                    enteredUri.value = address
                } else {
                    Log.i("[Dialer] Network is available, continue auto call to $address")
                    coreContext.startCall(address)
                }

                addressWaitingNetworkToBeCalled = null
                timeAtWitchWeTriedToCall = 0
            }
        }

        override fun onVersionUpdateCheckResultReceived(
            core: Core,
            result: VersionUpdateCheckResult,
            version: String?,
            url: String?
        ) {
            if (result == VersionUpdateCheckResult.NewVersionAvailable) {
                Log.i("[Dialer] Update available, version [$version], url [$url]")
                if (url != null && url.isNotEmpty()) {
                    updateAvailableEvent.value = Event(url)
                }
            }
        }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            scheduleConferenceAvailable.value = LinphoneUtils.isRemoteConferencingAvailable()
        }
    }

    init {
        coreContext.core.addListener(listener)

        enteredUri.value = ""
        atLeastOneCall.value = coreContext.core.callsNb > 0
        transferVisibility.value = false

        showSwitchCamera.value = coreContext.showSwitchCameraButton()
        scheduleConferenceAvailable.value = LinphoneUtils.isRemoteConferencingAvailable()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    // This is to workaround the cursor being set to the start when pressing a digit
    fun onBeforeUriChanged(editText: EditText, count: Int, after: Int) {
        enteredUriCursorPosition = editText.selectionEnd
        enteredUriCursorPosition += after - count
    }

    fun onAfterUriChanged(editText: EditText, editable: Editable?) {
        val newLength = editable?.length ?: 0
        if (newLength <= enteredUriCursorPosition) enteredUriCursorPosition = newLength
        if (enteredUriCursorPosition < 0) enteredUriCursorPosition = 0
        editText.setSelection(enteredUriCursorPosition)
    }

    fun updateShowVideoPreview() {
        val videoPreview = corePreferences.videoPreview
        showPreview.value = videoPreview
        coreContext.core.isVideoPreviewEnabled = videoPreview
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

    fun transferCall(): Boolean {
        val addressToCall = enteredUri.value.orEmpty()
        return if (addressToCall.isNotEmpty()) {
            onMessageToNotifyEvent.value = Event(
                if (coreContext.transferCallTo(addressToCall)) {
                    org.linphone.R.string.dialer_transfer_succeded
                } else {
                    org.linphone.R.string.dialer_transfer_failed
                }
            )
            eraseAll()
            true
        } else {
            setLastOutgoingCallAddress()
            false
        }
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }

    private fun setLastOutgoingCallAddress() {
        val callLog = coreContext.core.lastOutgoingCallLog
        if (callLog != null) {
            enteredUri.value = LinphoneUtils.getDisplayableAddress(callLog.remoteAddress)
        }
    }
}

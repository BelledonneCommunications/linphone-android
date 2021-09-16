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
package org.linphone.activities.voip.data

import androidx.lifecycle.MutableLiveData
import java.util.*
import org.linphone.contact.GenericContactData
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.tools.Log

open class CallData(val call: Call) : GenericContactData(call.remoteAddress) {
    val address = call.remoteAddress.asStringUriOnly()

    val isPaused = MutableLiveData<Boolean>()
    val canBePaused = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            if (call != this@CallData.call) return
            Log.i("[Call] State changed: $state")

            updatePause()
        }
    }

    init {
        call.addListener(listener)

        updatePause()
    }

    override fun destroy() {
        call.removeListener(listener)

        super.destroy()
    }

    fun togglePause() {
        if (isCallPaused()) {
            resume()
        } else {
            pause()
        }
    }

    fun pause() {
        call.pause()
    }

    fun resume() {
        call.resume()
    }

    fun toggleRecording() {
        if (call.isRecording) {
            call.stopRecording()
        } else {
            call.startRecording()
        }
        isRecording.value = call.isRecording
    }

    fun isCallActive(): Boolean {
        return when (call.state) {
            Call.State.Connected, Call.State.StreamsRunning, Call.State.UpdatedByRemote, Call.State.Updating -> true
            else -> false
        }
    }

    private fun isCallPaused(): Boolean {
        return when (call.state) {
            Call.State.Paused, Call.State.Pausing -> true
            else -> false
        }
    }

    private fun canCallBePaused(): Boolean {
        return !call.mediaInProgress() && when (call.state) {
            Call.State.StreamsRunning, Call.State.PausedByRemote -> true
            else -> false
        }
    }

    private fun updatePause() {
        isPaused.value = isCallPaused()
        canBePaused.value = canCallBePaused()

        // Check periodically until mediaInProgress is false
        /*if (call.mediaInProgress()) {
            viewModelScope.launch {
                delay(1000)
                updatePause()
            }
        }*/
    }
}

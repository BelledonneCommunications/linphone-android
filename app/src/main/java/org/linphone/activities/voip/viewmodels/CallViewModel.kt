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
package org.linphone.activities.voip.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.compatibility.Compatibility
import org.linphone.contact.GenericContactViewModel
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.Factory
import org.linphone.core.tools.Log

open class CallViewModel(val call: Call) : GenericContactViewModel(call.remoteAddress) {
    val isPaused = MutableLiveData<Boolean>()
    val canBePaused = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            if (call != this@CallViewModel.call) return
            Log.i("[Call] State changed: $state")

            updatePause()
        }

        override fun onSnapshotTaken(call: Call, filePath: String) {
            Log.i("[Call] Snapshot taken, saved at $filePath")
            val content = Factory.instance().createContent()
            content.filePath = filePath
            content.type = "image"
            content.subtype = "jpeg"
            content.name = filePath.substring(filePath.indexOf("/") + 1)

            viewModelScope.launch {
                if (Compatibility.addImageToMediaStore(coreContext.context, content)) {
                    Log.i("[Call] Adding snapshot ${content.name} to Media Store terminated")
                } else {
                    Log.e("[Call] Something went wrong while copying file to Media Store...")
                }
            }
        }
    }

    init {
        call.addListener(listener)

        updatePause()
    }

    override fun onCleared() {
        destroy()
        super.onCleared()
    }

    fun destroy() {
        call.removeListener(listener)
    }

    fun togglePause() {
        if (isCallPaused()) {
            resume()
        } else {
            pause()
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

    private fun updatePause() {
        isPaused.value = isCallPaused()
        canBePaused.value = canCallBePaused()

        // Check periodically until mediaInProgress is false
        if (call.mediaInProgress()) {
            viewModelScope.launch {
                delay(1000)
                updatePause()
            }
        }
    }
}

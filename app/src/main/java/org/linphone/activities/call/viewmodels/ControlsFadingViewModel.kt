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
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class ControlsFadingViewModel : ViewModel() {
    val areControlsHidden = MutableLiveData<Boolean>()

    val isVideoPreviewHidden = MutableLiveData<Boolean>()

    val videoEnabledEvent = MutableLiveData<Event<Boolean>>()

    private var timer: Timer? = null

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String?
        ) {
            if (state == Call.State.StreamsRunning || state == Call.State.Updating || state == Call.State.UpdatedByRemote) {
                Log.i("[Controls Fading] Call is in state $state, video is enabled? ${call.currentParams.videoEnabled()}")
                if (call.currentParams.videoEnabled()) {
                    videoEnabledEvent.value = Event(true)
                    startTimer()
                } else {
                    videoEnabledEvent.value = Event(false)
                    stopTimer()
                }
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        areControlsHidden.value = false
        isVideoPreviewHidden.value = false

        val currentCall = coreContext.core.currentCall
        if (currentCall != null && currentCall.currentParams.videoEnabled()) {
            videoEnabledEvent.value = Event(true)
            startTimer()
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        stopTimer()

        super.onCleared()
    }

    fun showMomentarily() {
        stopTimer()
        startTimer()
    }

    private fun stopTimer() {
        timer?.cancel()

        areControlsHidden.value = false
    }

    private fun startTimer() {
        timer?.cancel()

        timer = Timer("Hide UI controls scheduler")
        timer?.schedule(object : TimerTask() {
            override fun run() {
                areControlsHidden.postValue(coreContext.core.currentCall?.currentParams?.videoEnabled() ?: false)
            }
        }, 3000)
    }
}

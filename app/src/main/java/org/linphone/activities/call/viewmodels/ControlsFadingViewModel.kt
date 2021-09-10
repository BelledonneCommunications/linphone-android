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

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class ControlsFadingViewModel : ViewModel() {
    val areControlsHidden = MutableLiveData<Boolean>()

    val isVideoPreviewHidden = MutableLiveData<Boolean>()
    val isVideoPreviewResizedForPip = MutableLiveData<Boolean>()

    val videoEnabled = MutableLiveData<Boolean>()

    val proximitySensorEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    private val nonEarpieceOutputAudioDevice = MutableLiveData<Boolean>()

    private var timer: Timer? = null

    private var disabled: Boolean = false

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (state == Call.State.StreamsRunning || state == Call.State.Updating || state == Call.State.UpdatedByRemote) {
                val isVideoCall = coreContext.isVideoCallOrConferenceActive()
                Log.i("[Controls Fading] Call is in state $state, video is ${if (isVideoCall) "enabled" else "disabled"}")
                if (isVideoCall) {
                    videoEnabled.value = true
                    startTimer()
                } else {
                    videoEnabled.value = false
                    stopTimer()
                }
            }
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                Log.i("[Controls Fading] Output audio device changed to: ${audioDevice.id}")
                nonEarpieceOutputAudioDevice.value = audioDevice.type != AudioDevice.Type.Earpiece
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        areControlsHidden.value = false
        isVideoPreviewHidden.value = false
        isVideoPreviewResizedForPip.value = false
        nonEarpieceOutputAudioDevice.value = coreContext.core.outputAudioDevice?.type != AudioDevice.Type.Earpiece

        val isVideoCall = coreContext.isVideoCallOrConferenceActive()
        videoEnabled.value = isVideoCall
        if (isVideoCall) {
            startTimer()
        }

        proximitySensorEnabled.value = shouldEnableProximitySensor()
        proximitySensorEnabled.addSource(videoEnabled) {
            proximitySensorEnabled.value = shouldEnableProximitySensor()
        }
        proximitySensorEnabled.addSource(nonEarpieceOutputAudioDevice) {
            proximitySensorEnabled.value = shouldEnableProximitySensor()
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

    fun disable(disable: Boolean) {
        disabled = disable
        if (disabled) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    private fun shouldEnableProximitySensor(): Boolean {
        return !(videoEnabled.value ?: false) && !(nonEarpieceOutputAudioDevice.value ?: false)
    }

    private fun stopTimer() {
        timer?.cancel()

        areControlsHidden.value = false
    }

    private fun startTimer() {
        timer?.cancel()
        if (disabled) return

        timer = Timer("Hide UI controls scheduler")
        timer?.schedule(
            object : TimerTask() {
                override fun run() {
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            val videoEnabled = coreContext.isVideoCallOrConferenceActive()
                            areControlsHidden.postValue(videoEnabled)
                        }
                    }
                }
            },
            3000
        )
    }
}

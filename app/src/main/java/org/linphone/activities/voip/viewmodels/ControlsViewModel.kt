/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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

import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AudioRouteUtils
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class ControlsViewModel : ViewModel() {
    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isMuteMicrophoneEnabled = MutableLiveData<Boolean>()

    val isSpeakerSelected = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isVideoUpdateInProgress = MutableLiveData<Boolean>()

    val isSwitchCameraAvailable = MutableLiveData<Boolean>()

    val showExtras = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val goToCallsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var previewX: Float = 0f
    private var previewY: Float = 0f
    val previewTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previewX = view.x - event.rawX
                previewY = view.y - event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + previewX)
                    .y(event.rawY + previewY)
                    .setDuration(0)
                    .start()
                true
            }
            else -> {
                view.performClick()
                false
            }
        }
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i("[Call Controls] State changed: $state")
            if (state == Call.State.StreamsRunning) {
                isVideoUpdateInProgress.value = false
            }
            updateUI()
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            Log.i("[Call Controls] Audio device changed: ${audioDevice.deviceName}")
            updateSpeakerState()
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            Log.i("[Call Controls] Audio devices list updated")

            if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
                AudioRouteUtils.routeAudioToHeadset()
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        updateUI()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun hangUp() {
        val core = coreContext.core
        when {
            core.currentCall != null -> core.currentCall?.terminate()
            core.conference?.isIn == true -> core.terminateConference()
            else -> core.terminateAllCalls()
        }
    }

    fun toggleMuteMicrophone() {
        val micEnabled = coreContext.core.micEnabled()
        coreContext.core.enableMic(!micEnabled)
        updateMicState()
    }

    fun toggleSpeaker() {
        if (AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()) {
            forceEarpieceAudioRoute()
        } else {
            forceSpeakerAudioRoute()
        }
    }

    fun toggleVideo() {
        val core = coreContext.core
        val currentCall = core.currentCall
        val conference = core.conference

        if (conference != null && conference.isIn) {
            val params = core.createConferenceParams()
            val videoEnabled = conference.currentParams.isVideoEnabled
            params.isVideoEnabled = !videoEnabled
            conference.updateParams(params)
        } else if (currentCall != null) {
            val state = currentCall.state
            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error)
                return

            isVideoUpdateInProgress.value = true
            val params = core.createCallParams(currentCall)
            params?.enableVideo(!currentCall.currentParams.videoEnabled())
            currentCall.update(params)
        }
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }

    fun showExtraButtons() {
        showExtras.value = true
    }

    fun hideExtraButton() {
        showExtras.value = false
    }

    fun toggleFullScreen() {
        if (isVideoEnabled.value == false) return
        fullScreenMode.value = fullScreenMode.value != true
    }

    fun goToCallsList() {
        goToCallsListEvent.value = Event(true)
    }

    private fun updateUI() {
        updateVideoAvailable()
        updateVideoEnabled()
        updateMicState()
        updateSpeakerState()
    }

    private fun updateMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || !coreContext.core.micEnabled()
        isMuteMicrophoneEnabled.value = coreContext.core.currentCall != null || coreContext.core.conference?.isIn == true
    }

    private fun updateSpeakerState() {
        isSpeakerSelected.value = AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()
    }

    private fun forceEarpieceAudioRoute() {
        if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
            Log.i("[Call Controls] Headset found, route audio to it instead of earpiece")
            AudioRouteUtils.routeAudioToHeadset()
        } else {
            AudioRouteUtils.routeAudioToEarpiece()
        }
    }

    private fun forceSpeakerAudioRoute() {
        AudioRouteUtils.routeAudioToSpeaker()
    }

    private fun updateVideoAvailable() {
        val core = coreContext.core
        val currentCall = core.currentCall
        isVideoAvailable.value = (core.videoCaptureEnabled() || core.videoPreviewEnabled()) &&
            (
                (currentCall != null && !currentCall.mediaInProgress()) ||
                    core.conference?.isIn == true
                )
    }

    private fun updateVideoEnabled() {
        val enabled = coreContext.isVideoCallOrConferenceActive()
        isVideoEnabled.value = enabled
        isSwitchCameraAvailable.value = enabled && coreContext.showSwitchCameraButton()
    }
}

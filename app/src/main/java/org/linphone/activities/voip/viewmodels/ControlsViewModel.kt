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

import android.Manifest
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
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

    val goToConferenceParticipantsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToChatEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToCallsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showCallStatistics: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToNumpadEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
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

            if (coreContext.isVideoCallOrConferenceActive() && !PermissionHelper.get().hasCameraPermission()) {
                askPermissionEvent.value = Event(Manifest.permission.CAMERA)
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

    val extraButtonsMenuTranslateY = MutableLiveData<Float>()
    private val extraButtonsMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.voip_call_extra_buttons_translate_y), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                extraButtonsMenuTranslateY.value = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    init {
        coreContext.core.addListener(listener)
        extraButtonsMenuTranslateY.value = AppUtils.getDimension(R.dimen.voip_call_extra_buttons_translate_y)

        updateUI()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        extraButtonsMenuAnimator.end()

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
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.RECORD_AUDIO)
            return
        }

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
        if (!PermissionHelper.get().hasCameraPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.CAMERA)
            return
        }

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
        extraButtonsMenuAnimator.start()
        showExtras.value = true
    }

    fun hideExtraButtons() {
        extraButtonsMenuAnimator.reverse()
        showExtras.value = false
    }

    fun toggleFullScreen() {
        if (isVideoEnabled.value == false) return
        fullScreenMode.value = fullScreenMode.value != true
    }

    fun goToConferenceParticipantsList() {
        goToConferenceParticipantsListEvent.value = Event(true)
    }

    fun goToChat() {
        goToChatEvent.value = Event(true)
    }

    fun goToNumpad() {
        goToNumpadEvent.value = Event(true)
    }

    fun goToCallsList() {
        goToCallsListEvent.value = Event(true)
    }

    fun showCallStats() {
        showCallStatistics.value = Event(true)
    }

    private fun updateUI() {
        updateVideoAvailable()
        updateVideoEnabled()
        updateMicState()
        updateSpeakerState()
    }

    fun updateMicState() {
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

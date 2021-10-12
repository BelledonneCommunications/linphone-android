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
import androidx.lifecycle.MediatorLiveData
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

    val isBluetoothHeadsetSelected = MutableLiveData<Boolean>()

    val audioRoutesSelected = MutableLiveData<Boolean>()

    val audioRoutesEnabled = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isVideoUpdateInProgress = MutableLiveData<Boolean>()

    val isSwitchCameraAvailable = MutableLiveData<Boolean>()

    val isVideoPreviewResizedForPip = MutableLiveData<Boolean>()

    val isOutgoingEarlyMedia = MutableLiveData<Boolean>()

    val showExtras = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val chatRoomCreationInProgress = MutableLiveData<Boolean>()

    val numpadVisible = MutableLiveData<Boolean>()

    val dtmfHistory = MutableLiveData<String>()

    val callStatsVisible = MutableLiveData<Boolean>()

    val proximitySensorEnabled = MediatorLiveData<Boolean>()

    val goToConferenceParticipantsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToChatEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToCallsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToConferenceLayoutSettings: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val nonEarpieceOutputAudioDevice = MutableLiveData<Boolean>()

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

            isOutgoingEarlyMedia.value = state == Call.State.OutgoingEarlyMedia
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

            nonEarpieceOutputAudioDevice.value = audioDevice.type != AudioDevice.Type.Earpiece
            updateSpeakerState()
            updateBluetoothHeadsetState()
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            Log.i("[Call Controls] Audio devices list updated")
            val wasBluetoothPreviouslyAvailable = audioRoutesEnabled.value == true
            updateAudioRoutesState()

            if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
                AudioRouteUtils.routeAudioToHeadset()
            } else if (!wasBluetoothPreviouslyAvailable && corePreferences.routeAudioToBluetoothIfAvailable) {
                // Only attempt to route audio to bluetooth automatically when bluetooth device is connected
                if (AudioRouteUtils.isBluetoothAudioRouteAvailable()) {
                    AudioRouteUtils.routeAudioToBluetooth()
                }
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

    val audioRoutesMenuTranslateY = MutableLiveData<Float>()
    private val audioRoutesMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.voip_audio_routes_menu_translate_y), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                audioRoutesMenuTranslateY.value = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    init {
        coreContext.core.addListener(listener)

        extraButtonsMenuTranslateY.value = AppUtils.getDimension(R.dimen.voip_call_extra_buttons_translate_y)
        audioRoutesMenuTranslateY.value = AppUtils.getDimension(R.dimen.voip_audio_routes_menu_translate_y)
        audioRoutesSelected.value = false

        nonEarpieceOutputAudioDevice.value = coreContext.core.outputAudioDevice?.type != AudioDevice.Type.Earpiece
        proximitySensorEnabled.value = shouldProximitySensorBeEnabled()
        proximitySensorEnabled.addSource(isVideoEnabled) {
            proximitySensorEnabled.value = shouldProximitySensorBeEnabled()
        }
        proximitySensorEnabled.addSource(nonEarpieceOutputAudioDevice) {
            proximitySensorEnabled.value = shouldProximitySensorBeEnabled()
        }

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

    fun answer() {
        val currentCall = coreContext.core.currentCall
        if (currentCall != null) {
            coreContext.answerCall(currentCall)
        } else {
            Log.e("[Controls] Cant't find any current call to answer")
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

    fun toggleRoutesMenu() {
        audioRoutesSelected.value = audioRoutesSelected.value != true
        if (audioRoutesSelected.value == true) {
            audioRoutesMenuAnimator.start()
        } else {
            audioRoutesMenuAnimator.reverse()
        }
    }

    fun forceEarpieceAudioRoute() {
        if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
            Log.i("[Call Controls] Headset found, route audio to it instead of earpiece")
            AudioRouteUtils.routeAudioToHeadset()
        } else {
            AudioRouteUtils.routeAudioToEarpiece()
        }
    }

    fun forceSpeakerAudioRoute() {
        AudioRouteUtils.routeAudioToSpeaker()
    }

    fun forceBluetoothAudioRoute() {
        AudioRouteUtils.routeAudioToBluetooth()
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

    fun hideExtraButtons(skipAnimation: Boolean) {
        // Animation must be skipped when called from Fragment's onPause() !
        if (skipAnimation) {
            extraButtonsMenuTranslateY.value = AppUtils.getDimension(R.dimen.voip_call_extra_buttons_translate_y)
        } else {
            extraButtonsMenuAnimator.reverse()
        }
        showExtras.value = false
        chatRoomCreationInProgress.value = false
    }

    fun toggleFullScreen() {
        if (isVideoEnabled.value == false) return
        fullScreenMode.value = fullScreenMode.value != true
    }

    fun goToConferenceParticipantsList() {
        goToConferenceParticipantsListEvent.value = Event(true)
    }

    fun goToChat() {
        chatRoomCreationInProgress.value = true
        goToChatEvent.value = Event(true)
    }

    fun showNumpad() {
        hideExtraButtons(false)
        numpadVisible.value = true
    }

    fun hideNumpad() {
        numpadVisible.value = false
    }

    fun handleDtmfClick(key: Char) {
        dtmfHistory.value = "${dtmfHistory.value.orEmpty()}$key"
        coreContext.core.playDtmf(key, 1)
        coreContext.core.currentCall?.sendDtmf(key)
    }

    fun goToCallsList() {
        goToCallsListEvent.value = Event(true)
    }

    fun showCallStats() {
        hideExtraButtons(false)
        callStatsVisible.value = true
    }

    fun hideCallStats() {
        callStatsVisible.value = false
    }

    fun goToConferenceLayout() {
        goToConferenceLayoutSettings.value = Event(true)
    }

    private fun updateUI() {
        updateVideoAvailable()
        updateVideoEnabled()
        updateMicState()
        updateSpeakerState()
        updateAudioRoutesState()
    }

    fun updateMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || !coreContext.core.micEnabled()
        isMuteMicrophoneEnabled.value = coreContext.core.currentCall != null || coreContext.core.conference?.isIn == true
    }

    private fun updateSpeakerState() {
        isSpeakerSelected.value = AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()
    }

    private fun updateBluetoothHeadsetState() {
        isBluetoothHeadsetSelected.value = AudioRouteUtils.isBluetoothAudioRouteCurrentlyUsed()
    }

    private fun updateAudioRoutesState() {
        val bluetoothDeviceAvailable = AudioRouteUtils.isBluetoothAudioRouteAvailable()
        audioRoutesEnabled.value = bluetoothDeviceAvailable

        if (!bluetoothDeviceAvailable) {
            audioRoutesSelected.value = false
            audioRoutesEnabled.value = false
        }
    }

    private fun updateVideoAvailable() {
        val core = coreContext.core
        val currentCall = core.currentCall
        isVideoAvailable.value = (core.videoCaptureEnabled() || core.videoPreviewEnabled()) &&
            ((currentCall != null && !currentCall.mediaInProgress()) || core.conference?.isIn == true)
    }

    private fun updateVideoEnabled() {
        val enabled = coreContext.isVideoCallOrConferenceActive()
        isVideoEnabled.value = enabled
        isSwitchCameraAvailable.value = enabled && coreContext.showSwitchCameraButton()
    }

    private fun shouldProximitySensorBeEnabled(): Boolean {
        return !(isVideoEnabled.value ?: false) && !(nonEarpieceOutputAudioDevice.value ?: false)
    }
}

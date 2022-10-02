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
import android.view.animation.LinearInterpolator
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.window.layout.FoldingFeature
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
    val isSpeakerSelected = MutableLiveData<Boolean>()

    val isBluetoothHeadsetSelected = MutableLiveData<Boolean>()

    val audioRoutesSelected = MutableLiveData<Boolean>()

    val audioRoutesEnabled = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isSendingVideo = MutableLiveData<Boolean>()

    val isVideoUpdateInProgress = MutableLiveData<Boolean>()

    val isSwitchCameraAvailable = MutableLiveData<Boolean>()

    val isOutgoingEarlyMedia = MutableLiveData<Boolean>()

    val showExtras = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val folded = MutableLiveData<Boolean>()

    val pipMode = MutableLiveData<Boolean>()

    val chatRoomCreationInProgress = MutableLiveData<Boolean>()

    val numpadVisible = MutableLiveData<Boolean>()

    val dtmfHistory = MutableLiveData<String>()

    val callStatsVisible = MutableLiveData<Boolean>()

    val proximitySensorEnabled = MediatorLiveData<Boolean>()

    val showTakeSnapshotButton = MutableLiveData<Boolean>()

    val goToConferenceParticipantsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToChatEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToCallsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToConferenceLayoutSettingsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val goToDialerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val foldingState = MutableLiveData<FoldingFeature>()

    private val nonEarpieceOutputAudioDevice = MutableLiveData<Boolean>()

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
                if (!call.currentParams.isVideoEnabled && fullScreenMode.value == true) {
                    fullScreenMode.value = false
                }
                isVideoUpdateInProgress.value = false
            } else if (state == Call.State.PausedByRemote) {
                fullScreenMode.value = false
            }

            if (core.currentCall?.currentParams?.isVideoEnabled == true && !PermissionHelper.get().hasCameraPermission()) {
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

    val bouncyCounterTranslateY = MutableLiveData<Float>()

    private val bounceAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.voip_counter_bounce_offset), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                bouncyCounterTranslateY.value = value
            }
            interpolator = LinearInterpolator()
            duration = 250
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    init {
        coreContext.core.addListener(listener)

        fullScreenMode.value = false
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

        if (corePreferences.enableAnimations) bounceAnimator.start()
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
            Log.e("[Controls] Can't find any current call to answer")
        }
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
        updateSpeakerState()
        updateBluetoothHeadsetState()
    }

    fun forceSpeakerAudioRoute() {
        AudioRouteUtils.routeAudioToSpeaker()
        updateSpeakerState()
        updateBluetoothHeadsetState()
    }

    fun forceBluetoothAudioRoute() {
        AudioRouteUtils.routeAudioToBluetooth()
        updateSpeakerState()
        updateBluetoothHeadsetState()
    }

    fun toggleVideo() {
        if (!PermissionHelper.get().hasCameraPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.CAMERA)
            return
        }

        val core = coreContext.core
        val currentCall = core.currentCall
        if (currentCall != null) {
            val state = currentCall.state
            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error)
                return

            isVideoUpdateInProgress.value = true
            val params = core.createCallParams(currentCall)
            if (currentCall.conference != null) {
                if (params?.isVideoEnabled == false) {
                    params.isVideoEnabled = true
                    params.videoDirection = MediaDirection.SendRecv
                } else {
                    if (params?.videoDirection == MediaDirection.SendRecv || params?.videoDirection == MediaDirection.SendOnly) {
                        params.videoDirection = MediaDirection.RecvOnly
                    } else {
                        params?.videoDirection = MediaDirection.SendRecv
                    }
                }
            } else {
                params?.isVideoEnabled = !currentCall.currentParams.isVideoEnabled
            }
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
        if (fullScreenMode.value == false && isVideoEnabled.value == false) return
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

    fun showCallStats(skipAnimation: Boolean = false) {
        hideExtraButtons(skipAnimation)
        callStatsVisible.value = true
    }

    fun hideCallStats() {
        callStatsVisible.value = false
    }

    fun goToConferenceLayout() {
        goToConferenceLayoutSettingsEvent.value = Event(true)
    }

    fun goToDialerForCallTransfer() {
        goToDialerEvent.value = Event(true)
    }

    fun goToDialerForNewCall() {
        goToDialerEvent.value = Event(false)
    }

    private fun updateUI() {
        updateVideoAvailable()
        updateVideoEnabled()
        updateSpeakerState()
        updateBluetoothHeadsetState()
        updateAudioRoutesState()
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
        }
    }

    private fun updateVideoAvailable() {
        val core = coreContext.core
        val currentCall = core.currentCall
        isVideoAvailable.value = (core.isVideoCaptureEnabled || core.isVideoPreviewEnabled) &&
            ((currentCall != null && !currentCall.mediaInProgress()) || core.conference?.isIn == true)
    }

    private fun updateVideoEnabled() {
        val currentCall = coreContext.core.currentCall ?: coreContext.core.calls.firstOrNull()
        val enabled = currentCall?.currentParams?.isVideoEnabled ?: false
        // Prevent speaker to turn on each time a participant joins a video conference
        val isConference = currentCall?.conference != null
        if (enabled && !isConference && isVideoEnabled.value == false) {
            Log.i("[Call Controls] Video is being turned on")
            if (corePreferences.routeAudioToSpeakerWhenVideoIsEnabled) {
                // Do not turn speaker on when video is enabled if headset or bluetooth is used
                if (!AudioRouteUtils.isHeadsetAudioRouteAvailable() &&
                    !AudioRouteUtils.isBluetoothAudioRouteCurrentlyUsed()
                ) {
                    Log.i("[Call Controls] Video enabled and no wired headset not bluetooth in use, routing audio to speaker")
                    AudioRouteUtils.routeAudioToSpeaker()
                }
            }
        }

        isVideoEnabled.value = enabled
        showTakeSnapshotButton.value = enabled && corePreferences.showScreenshotButton
        var isVideoBeingSent = if (coreContext.core.currentCall?.conference != null) {
            val videoDirection = coreContext.core.currentCall?.currentParams?.videoDirection
            videoDirection == MediaDirection.SendRecv || videoDirection == MediaDirection.SendOnly
        } else {
            true
        }
        isSendingVideo.value = isVideoBeingSent
        isSwitchCameraAvailable.value = enabled && coreContext.showSwitchCameraButton() && isVideoBeingSent
    }

    private fun shouldProximitySensorBeEnabled(): Boolean {
        return !(isVideoEnabled.value ?: false) && !(nonEarpieceOutputAudioDevice.value ?: false)
    }
}

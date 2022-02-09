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

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.os.Vibrator
import android.view.animation.LinearInterpolator
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.dialer.NumpadDigitListener
import org.linphone.compatibility.Compatibility
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.*
import org.linphone.utils.Event

class ControlsViewModel : ViewModel() {
    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isMuteMicrophoneEnabled = MutableLiveData<Boolean>()

    val isSpeakerSelected = MutableLiveData<Boolean>()

    val isBluetoothHeadsetSelected = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isVideoUpdateInProgress = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val isPauseEnabled = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()

    val isConferencingAvailable = MutableLiveData<Boolean>()

    val unreadMessagesCount = MutableLiveData<Int>()

    val numpadVisibility = MutableLiveData<Boolean>()

    val optionsVisibility = MutableLiveData<Boolean>()

    val audioRoutesSelected = MutableLiveData<Boolean>()

    val audioRoutesEnabled = MutableLiveData<Boolean>()

    val takeScreenshotEnabled = MutableLiveData<Boolean>()

    val chatClickedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val addCallClickedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val transferCallClickedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askAudioRecordPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val askCameraPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val somethingClickedEvent = MutableLiveData<Event<Boolean>>()

    val chatAllowed = !corePreferences.disableChat

    private val vibrator = coreContext.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val chatUnreadCountTranslateY = MutableLiveData<Float>()

    val optionsMenuTranslateY = MutableLiveData<Float>()

    val audioRoutesMenuTranslateY = MutableLiveData<Float>()

    val toggleNumpadEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val bounceAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.tabs_fragment_unread_count_bounce_offset), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                chatUnreadCountTranslateY.value = -value
            }
            interpolator = LinearInterpolator()
            duration = 250
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    private val optionsMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.call_options_menu_translate_y), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                optionsMenuTranslateY.value = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    private val audioRoutesMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.call_audio_routes_menu_translate_y), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                audioRoutesMenuTranslateY.value = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    val onKeyClick: NumpadDigitListener = object : NumpadDigitListener {
        override fun handleClick(key: Char) {
            coreContext.core.playDtmf(key, 1)
            somethingClickedEvent.value = Event(true)
            coreContext.core.currentCall?.sendDtmf(key)

            if (vibrator.hasVibrator() && corePreferences.dtmfKeypadVibration) {
                Compatibility.eventVibration(vibrator)
            }
        }

        override fun handleLongClick(key: Char): Boolean {
            return true
        }
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            updateUnreadChatCount()
        }

        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            updateUnreadChatCount()
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (state == Call.State.StreamsRunning) {
                isVideoUpdateInProgress.value = false
            }

            if (coreContext.isVideoCallOrConferenceActive() && !PermissionHelper.get().hasCameraPermission()) {
                askCameraPermissionEvent.value = Event(Manifest.permission.CAMERA)
            }

            updateUI()
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            Log.i("[Call] Audio device changed: ${audioDevice.deviceName}")
            updateSpeakerState()
            updateBluetoothHeadsetState()
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            Log.i("[Call] Audio devices list updated")
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

    init {
        coreContext.core.addListener(listener)
        val currentCall = coreContext.core.currentCall

        updateMuteMicState()
        updateAudioRelated()
        updateUnreadChatCount()

        numpadVisibility.value = false
        optionsVisibility.value = false
        audioRoutesSelected.value = false

        isRecording.value = currentCall?.isRecording
        isVideoUpdateInProgress.value = false
        showSwitchCamera.value = coreContext.showSwitchCameraButton()

        chatUnreadCountTranslateY.value = 0f
        optionsMenuTranslateY.value = AppUtils.getDimension(R.dimen.call_options_menu_translate_y)
        audioRoutesMenuTranslateY.value = AppUtils.getDimension(R.dimen.call_audio_routes_menu_translate_y)

        takeScreenshotEnabled.value = corePreferences.showScreenshotButton

        updateUI()
        if (corePreferences.enableAnimations) bounceAnimator.start()
    }

    override fun onCleared() {
        if (corePreferences.enableAnimations) bounceAnimator.end()
        optionsMenuAnimator.end()
        audioRoutesMenuAnimator.end()
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun updateUnreadChatCount() {
        unreadMessagesCount.value = coreContext.core.unreadChatMessageCountFromActiveLocals
    }

    fun toggleMuteMicrophone() {
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            askAudioRecordPermissionEvent.value = Event(Manifest.permission.RECORD_AUDIO)
            return
        }

        somethingClickedEvent.value = Event(true)
        val micEnabled = coreContext.core.isMicEnabled
        coreContext.core.isMicEnabled = !micEnabled
        updateMuteMicState()
    }

    fun toggleSpeaker() {
        somethingClickedEvent.value = Event(true)
        if (AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()) {
            forceEarpieceAudioRoute()
        } else {
            forceSpeakerAudioRoute()
        }
    }

    fun switchCamera() {
        somethingClickedEvent.value = Event(true)
        coreContext.switchCamera()
    }

    fun terminateCall() {
        val core = coreContext.core
        when {
            core.currentCall != null -> core.currentCall?.terminate()
            core.conference?.isIn == true -> core.terminateConference()
            else -> core.terminateAllCalls()
        }
    }

    fun toggleVideo() {
        if (!PermissionHelper.get().hasCameraPermission()) {
            askCameraPermissionEvent.value = Event(Manifest.permission.CAMERA)
            return
        }

        val core = coreContext.core
        val currentCall = core.currentCall
        val conference = core.conference

        if (conference != null && conference.isIn) {
            val params = core.createConferenceParams()
            val videoEnabled = conference.currentParams.isVideoEnabled
            params.isVideoEnabled = !videoEnabled
            Log.i("[Controls VM] Conference current param for video is $videoEnabled")
            conference.updateParams(params)
        } else if (currentCall != null) {
            val state = currentCall.state
            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error)
                return

            isVideoUpdateInProgress.value = true
            val params = core.createCallParams(currentCall)
            params?.isVideoEnabled = !currentCall.currentParams.isVideoEnabled
            currentCall.update(params)
        }
    }

    fun toggleOptionsMenu() {
        somethingClickedEvent.value = Event(true)
        optionsVisibility.value = optionsVisibility.value != true
        if (optionsVisibility.value == true) {
            optionsMenuAnimator.start()
        } else {
            optionsMenuAnimator.reverse()
        }
    }

    fun toggleNumpadVisibility() {
        somethingClickedEvent.value = Event(true)
        numpadVisibility.value = numpadVisibility.value != true
        toggleNumpadEvent.value = Event(numpadVisibility.value ?: true)
    }

    fun toggleRoutesMenu() {
        somethingClickedEvent.value = Event(true)
        audioRoutesSelected.value = audioRoutesSelected.value != true
        if (audioRoutesSelected.value == true) {
            audioRoutesMenuAnimator.start()
        } else {
            audioRoutesMenuAnimator.reverse()
        }
    }

    fun toggleRecording(closeMenu: Boolean) {
        somethingClickedEvent.value = Event(true)

        val core = coreContext.core
        val currentCall = core.currentCall
        val conference = core.conference

        when {
            currentCall != null -> {
                if (currentCall.isRecording) {
                    currentCall.stopRecording()
                } else {
                    currentCall.startRecording()
                }
                isRecording.value = currentCall.isRecording
            }
            conference != null -> {
                val path = LinphoneUtils.getRecordingFilePathForConference()
                if (conference.isRecording) {
                    conference.stopRecording()
                } else {
                    conference.startRecording(path)
                }
                isRecording.value = conference.isRecording
            }
            else -> {
                isRecording.value = false
            }
        }

        if (closeMenu) toggleOptionsMenu()
    }

    fun onChatClicked() {
        chatClickedEvent.value = Event(true)
    }

    fun onAddCallClicked() {
        addCallClickedEvent.value = Event(true)
        toggleOptionsMenu()
    }

    fun onTransferCallClicked() {
        transferCallClickedEvent.value = Event(true)
        toggleOptionsMenu()
    }

    fun startConference() {
        somethingClickedEvent.value = Event(true)

        val core = coreContext.core
        val currentCallVideoEnabled = core.currentCall?.currentParams?.isVideoEnabled ?: false

        val params = core.createConferenceParams()
        params.isVideoEnabled = currentCallVideoEnabled
        Log.i("[Call] Setting videoEnabled to [$currentCallVideoEnabled] in conference params")

        val conference = core.conference ?: core.createConferenceWithParams(params)
        conference?.addParticipants(core.calls)

        toggleOptionsMenu()
    }

    fun forceEarpieceAudioRoute() {
        somethingClickedEvent.value = Event(true)
        if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
            Log.i("[Call] Headset found, route audio to it instead of earpiece")
            AudioRouteUtils.routeAudioToHeadset()
        } else {
            AudioRouteUtils.routeAudioToEarpiece()
        }
    }

    fun forceSpeakerAudioRoute() {
        somethingClickedEvent.value = Event(true)
        AudioRouteUtils.routeAudioToSpeaker()
    }

    fun forceBluetoothAudioRoute() {
        somethingClickedEvent.value = Event(true)
        AudioRouteUtils.routeAudioToBluetooth()
    }

    fun updateMuteMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || !coreContext.core.isMicEnabled
        isMuteMicrophoneEnabled.value = coreContext.core.currentCall != null || coreContext.core.conference?.isIn == true
    }

    private fun updateAudioRelated() {
        updateSpeakerState()
        updateBluetoothHeadsetState()
        updateAudioRoutesState()
    }

    private fun updateUI() {
        val currentCall = coreContext.core.currentCall
        updateVideoAvailable()
        updateVideoEnabled()
        isPauseEnabled.value = currentCall != null && !currentCall.mediaInProgress()
        isMuteMicrophoneEnabled.value = currentCall != null || coreContext.core.conference?.isIn == true
        updateConferenceState()

        // Check periodically until mediaInProgress is false
        if (currentCall != null && currentCall.mediaInProgress()) {
            viewModelScope.launch {
                delay(1000)
                updateUI()
            }
        }
    }

    private fun updateSpeakerState() {
        isSpeakerSelected.value = AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()
    }

    private fun updateAudioRoutesState() {
        val bluetoothDeviceAvailable = AudioRouteUtils.isBluetoothAudioRouteAvailable()
        audioRoutesEnabled.value = bluetoothDeviceAvailable
        if (!bluetoothDeviceAvailable) {
            audioRoutesSelected.value = false
        }
    }

    private fun updateBluetoothHeadsetState() {
        isBluetoothHeadsetSelected.value = AudioRouteUtils.isBluetoothAudioRouteCurrentlyUsed()
    }

    private fun updateVideoAvailable() {
        val core = coreContext.core
        val currentCall = core.currentCall
        isVideoAvailable.value = (core.isVideoCaptureEnabled || core.isVideoPreviewEnabled) &&
            (
                (currentCall != null && !currentCall.mediaInProgress()) ||
                    core.conference?.isIn == true
                )
    }

    private fun updateVideoEnabled() {
        val enabled = coreContext.isVideoCallOrConferenceActive()
        isVideoEnabled.value = enabled
    }

    private fun updateConferenceState() {
        val core = coreContext.core
        isConferencingAvailable.value = core.callsNb > max(1, core.conference?.participantCount ?: 0) && !core.soundResourcesLocked()
    }
}

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
import android.content.Context
import android.os.Vibrator
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.math.max
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.dialer.NumpadDigitListener
import org.linphone.compatibility.Compatibility
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

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

    val audioRoutesVisibility = MutableLiveData<Boolean>()

    val audioRoutesEnabled = MutableLiveData<Boolean>()

    val chatClickedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val addCallClickedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val transferCallClickedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val toggleOptionsMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val toggleAudioRoutesMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val somethingClickedEvent = MutableLiveData<Event<Boolean>>()

    val chatAllowed = !LinphoneApplication.corePreferences.disableChat

    private val vibrator = coreContext.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val onKeyClick: NumpadDigitListener = object : NumpadDigitListener {
        override fun handleClick(key: Char) {
            coreContext.core.playDtmf(key, 1)
            somethingClickedEvent.value = Event(true)
            coreContext.core.currentCall?.sendDtmf(key)

            if (vibrator.hasVibrator() && LinphoneApplication.corePreferences.dtmfKeypadVibration) {
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
            if (state == Call.State.StreamsRunning) isVideoUpdateInProgress.value = false

            if (coreContext.isVideoCallOrConferenceActive() && !PermissionHelper.get().hasCameraPermission()) {
                askPermissionEvent.value = Event(Manifest.permission.CAMERA)
            }

            updateUI()
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            updateSpeakerState()
            updateBluetoothHeadsetState()
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            updateAudioRoutesState()
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
        audioRoutesVisibility.value = false

        isRecording.value = currentCall?.isRecording
        isVideoUpdateInProgress.value = false
        showSwitchCamera.value = coreContext.showSwitchCameraButton()

        updateUI()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun updateUnreadChatCount() {
        unreadMessagesCount.value = coreContext.core.unreadChatMessageCountFromActiveLocals
    }

    fun toggleMuteMicrophone() {
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.RECORD_AUDIO)
            return
        }

        somethingClickedEvent.value = Event(true)
        val micEnabled = coreContext.core.micEnabled()
        coreContext.core.enableMic(!micEnabled)
        updateMuteMicState()
    }

    fun toggleSpeaker() {
        somethingClickedEvent.value = Event(true)
        val audioDevice = coreContext.core.outputAudioDevice
        if (audioDevice?.type == AudioDevice.Type.Speaker) {
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
            core.isInConference -> core.terminateConference()
            else -> core.terminateAllCalls()
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

        if (conference != null && core.isInConference) {
            val params = core.createConferenceParams()
            val videoEnabled = conference.currentParams.videoEnabled()
            params.enableVideo(!videoEnabled)
            Log.i("[Controls VM] Conference current param for video is $videoEnabled")
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

    fun toggleOptionsMenu() {
        somethingClickedEvent.value = Event(true)
        optionsVisibility.value = optionsVisibility.value != true
        toggleOptionsMenuEvent.value = Event(optionsVisibility.value ?: true)
    }

    fun toggleNumpadVisibility() {
        somethingClickedEvent.value = Event(true)
        numpadVisibility.value = numpadVisibility.value != true
    }

    fun toggleRoutesMenu() {
        somethingClickedEvent.value = Event(true)
        audioRoutesVisibility.value = audioRoutesVisibility.value != true
        toggleAudioRoutesMenuEvent.value = Event(audioRoutesVisibility.value ?: true)
    }

    fun toggleRecording(closeMenu: Boolean) {
        somethingClickedEvent.value = Event(true)
        val currentCall = coreContext.core.currentCall
        if (currentCall != null) {
            if (currentCall.isRecording) {
                currentCall.stopRecording()
            } else {
                currentCall.startRecording()
            }
        }
        isRecording.value = currentCall?.isRecording
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
        val currentCallVideoEnabled = core.currentCall?.currentParams?.videoEnabled() ?: false

        val params = core.createConferenceParams()
        params.enableVideo(currentCallVideoEnabled)
        Log.i("[Call] Setting videoEnabled to [$currentCallVideoEnabled] in conference params")

        val conference = core.createConferenceWithParams(params)
        for (call in core.calls) {
            conference?.addParticipant(call)
        }

        toggleOptionsMenu()
    }

    fun forceEarpieceAudioRoute() {
        somethingClickedEvent.value = Event(true)
        for (audioDevice in coreContext.core.audioDevices) {
            if (audioDevice.type == AudioDevice.Type.Earpiece) {
                Log.i("[Call] Found earpiece audio device [${audioDevice.deviceName}], routing audio to it")
                coreContext.core.outputAudioDevice = audioDevice
                return
            }
        }
        Log.e("[Call] Couldn't find earpiece audio device")
    }

    fun forceSpeakerAudioRoute() {
        somethingClickedEvent.value = Event(true)
        for (audioDevice in coreContext.core.audioDevices) {
            if (audioDevice.type == AudioDevice.Type.Speaker) {
                Log.i("[Call] Found speaker audio device [${audioDevice.deviceName}], routing audio to it")
                coreContext.core.outputAudioDevice = audioDevice
                return
            }
        }
        Log.e("[Call] Couldn't find speaker audio device")
    }

    fun forceBluetoothAudioRoute() {
        somethingClickedEvent.value = Event(true)
        for (audioDevice in coreContext.core.audioDevices) {
            if ((audioDevice.type == AudioDevice.Type.Bluetooth) && audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                Log.i("[Call] Found bluetooth audio device [${audioDevice.deviceName}], routing audio to it")
                coreContext.core.outputAudioDevice = audioDevice
                return
            }
        }
        Log.e("[Call] Couldn't find bluetooth audio device")
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
        isMuteMicrophoneEnabled.value = currentCall != null || coreContext.core.isInConference
        updateConferenceState()
    }

    fun updateMuteMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || !coreContext.core.micEnabled()
        isMuteMicrophoneEnabled.value = coreContext.core.currentCall != null || coreContext.core.isInConference
    }

    private fun updateSpeakerState() {
        val audioDevice = coreContext.core.outputAudioDevice
        isSpeakerSelected.value = audioDevice?.type == AudioDevice.Type.Speaker
    }

    private fun updateAudioRoutesState() {
        var bluetoothDeviceAvailable = false
        for (audioDevice in coreContext.core.audioDevices) {
            if (audioDevice.type == AudioDevice.Type.Bluetooth) {
                bluetoothDeviceAvailable = true
                break
            }
        }
        audioRoutesEnabled.value = bluetoothDeviceAvailable
        if (!bluetoothDeviceAvailable) {
            audioRoutesVisibility.value = false
        }
    }

    private fun updateBluetoothHeadsetState() {
        val audioDevice = coreContext.core.outputAudioDevice
        isBluetoothHeadsetSelected.value = audioDevice?.type == AudioDevice.Type.Bluetooth
    }

    private fun updateVideoAvailable() {
        val core = coreContext.core
        val currentCall = core.currentCall
        isVideoAvailable.value = (core.videoCaptureEnabled() || core.videoPreviewEnabled()) &&
                ((currentCall != null && !currentCall.mediaInProgress()) ||
                        (core.conference != null && core.isInConference))
    }

    private fun updateVideoEnabled() {
        val enabled = coreContext.isVideoCallOrConferenceActive()
        isVideoEnabled.value = enabled
    }

    private fun updateConferenceState() {
        val core = coreContext.core
        isConferencingAvailable.value = core.callsNb > max(1, core.conferenceSize) && !core.soundResourcesLocked()
    }
}

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
package org.linphone.activities.main.conference.viewmodels

import android.Manifest
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.AudioRouteUtils
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class ConferenceWaitingRoomViewModel : ViewModel() {
    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerSelected = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isSwitchCameraAvailable = MutableLiveData<Boolean>()

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val cancelConferenceJoiningEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val joinConferenceEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            Log.i("[Conference Waiting Room] Audio device changed: ${audioDevice.deviceName}")
            updateSpeakerState()
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            Log.i("[Conference Waiting Room] Audio devices list updated")
            if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
                AudioRouteUtils.routeAudioToHeadset()
            }
        }
    }

    init {
        val core = coreContext.core
        core.addListener(listener)

        isVideoAvailable.value = core.videoCaptureEnabled() || core.videoPreviewEnabled()
        isVideoEnabled.value = core.videoActivationPolicy.automaticallyInitiate && PermissionHelper.get().hasCameraPermission()
        isSwitchCameraAvailable.value = isVideoEnabled.value == true && coreContext.showSwitchCameraButton()
        if (isVideoEnabled.value == true) {
            core.enableVideoPreview(true)
        }

        updateMicState()
        updateSpeakerState()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun cancel() {
        cancelConferenceJoiningEvent.value = Event(true)
    }

    fun start() {
        joinConferenceEvent.value = Event(true)
    }

    fun toggleMuteMicrophone() {
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.RECORD_AUDIO)
            return
        }

        // TODO: currently doesn't work in SDK if there is no call
        val micEnabled = coreContext.core.micEnabled()
        coreContext.core.enableMic(!micEnabled)

        updateMicState()
    }

    fun updateMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || !coreContext.core.micEnabled()
    }

    fun toggleSpeaker() {
        // TODO: currently doesn't work in SDK if there is no call
        if (AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()) {
            forceEarpieceAudioRoute()
        } else {
            forceSpeakerAudioRoute()
        }
        updateSpeakerState()
    }

    fun updateSpeakerState() {
        isSpeakerSelected.value = AudioRouteUtils.isSpeakerAudioRouteCurrentlyUsed()
    }

    fun toggleVideo() {
        if (!PermissionHelper.get().hasCameraPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.CAMERA)
            return
        }

        isVideoEnabled.value = isVideoEnabled.value == false
        isSwitchCameraAvailable.value = isVideoEnabled.value == true && coreContext.showSwitchCameraButton()
        coreContext.core.enableVideoPreview(isVideoEnabled.value == true)
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }

    private fun forceEarpieceAudioRoute() {
        if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
            Log.i("[Conference Waiting Room] Headset found, route audio to it instead of earpiece")
            AudioRouteUtils.routeAudioToHeadset()
        } else {
            AudioRouteUtils.routeAudioToEarpiece()
        }
    }

    private fun forceSpeakerAudioRoute() {
        AudioRouteUtils.routeAudioToSpeaker()
    }
}

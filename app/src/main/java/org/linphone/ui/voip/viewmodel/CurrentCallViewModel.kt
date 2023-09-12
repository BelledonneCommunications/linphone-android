/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package org.linphone.ui.voip.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.voip.model.AudioDeviceModel
import org.linphone.utils.AudioRouteUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CurrentCallViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Current Call ViewModel]"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val isHeadsetEnabled = MutableLiveData<Boolean>()

    val isBluetoothEnabled = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val pipMode = MutableLiveData<Boolean>()

    val halfOpenedFolded = MutableLiveData<Boolean>()

    // To synchronize chronometers in UI
    val callDuration = MutableLiveData<Int>()

    val showAudioDevicesListEvent: MutableLiveData<Event<ArrayList<AudioDeviceModel>>> by lazy {
        MutableLiveData<Event<ArrayList<AudioDeviceModel>>>()
    }

    // ZRTP related

    val isRemoteDeviceTrusted = MutableLiveData<Boolean>()

    val showZrtpSasDialogEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    // Extras actions

    val isActionsMenuExpanded = MutableLiveData<Boolean>()

    val toggleExtraActionsBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var call: Call

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onEncryptionChanged(call: Call, on: Boolean, authenticationToken: String?) {
            updateEncryption()
        }

        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State?, message: String) {
            if (CurrentCallViewModel@call != call) {
                return
            }
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")

            if (LinphoneUtils.isCallOutgoing(call.state)) {
                isVideoEnabled.postValue(call.params.isVideoEnabled)
            } else {
                val videoEnabled = call.currentParams.isVideoEnabled
                if (videoEnabled && isVideoEnabled.value == false) {
                    Log.i("$TAG Video enabled, routing audio to speaker")
                    AudioRouteUtils.routeAudioToSpeaker(call)
                }
                isVideoEnabled.postValue(videoEnabled)

                // Toggle full screen OFF when remote disables video
                if (!videoEnabled && fullScreenMode.value == true) {
                    fullScreenMode.postValue(false)
                }
            }
        }

        @WorkerThread
        override fun onAudioDeviceChanged(call: Call, audioDevice: AudioDevice) {
            Log.i("$TAG Audio device changed [${audioDevice.id}]")
            updateOutputAudioDevice(audioDevice)
        }
    }

    init {
        isVideoEnabled.value = false
        isMicrophoneMuted.value = false
        fullScreenMode.value = false
        isActionsMenuExpanded.value = false

        coreContext.postOnCoreThread { core ->
            val currentCall = core.currentCall ?: core.calls.firstOrNull()

            if (currentCall != null) {
                call = currentCall
                Log.i("$TAG Found call [$call]")
                configureCall(call)
            } else {
                Log.e("$TAG Failed to find call!")
            }

            showSwitchCamera.postValue(coreContext.showSwitchCameraButton())
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.removeListener(callListener)
            }
        }
    }

    @UiThread
    fun answer() {
        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                Log.i("$TAG Answering call [$call]")
                coreContext.answerCall(call)
            }
        }
    }

    @UiThread
    fun hangUp() {
        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                Log.i("$TAG Terminating call [$call]")
                call.terminate()
            }
        }
    }

    @UiThread
    fun updateZrtpSas(verified: Boolean) {
        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.authenticationTokenVerified = verified
            }
        }
    }

    @UiThread
    fun toggleMuteMicrophone() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: request record audio permission
            return
        }

        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.microphoneMuted = !call.microphoneMuted
                isMicrophoneMuted.postValue(call.microphoneMuted)
            }
        }
    }

    @UiThread
    fun changeAudioOutputDevice() {
        val routeAudioToSpeaker = isSpeakerEnabled.value != true

        coreContext.postOnCoreThread { core ->
            val audioDevices = core.audioDevices
            val list = arrayListOf<AudioDeviceModel>()
            for (device in audioDevices) {
                // Only list output audio devices
                if (!device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) continue

                val isSpeaker = device.type == AudioDevice.Type.Speaker
                val isHeadset = device.type == AudioDevice.Type.Headset || device.type == AudioDevice.Type.Headphones
                val isBluetooth = device.type == AudioDevice.Type.Bluetooth
                val model = AudioDeviceModel(device, device.id, isSpeaker, isHeadset, isBluetooth) {
                    // onSelected
                    coreContext.postOnCoreThread {
                        Log.i("$TAG Selected audio device with ID [${device.id}]")
                        if (::call.isInitialized) {
                            when {
                                isHeadset -> AudioRouteUtils.routeAudioToHeadset(call)
                                isBluetooth -> AudioRouteUtils.routeAudioToBluetooth(call)
                                isSpeaker -> AudioRouteUtils.routeAudioToSpeaker(call)
                                else -> AudioRouteUtils.routeAudioToEarpiece(call)
                            }
                        }
                    }
                }
                list.add(model)
                Log.i("$TAG Found audio device [$device]")
            }

            if (list.size > 2) {
                Log.i("$TAG Found more than two devices, showing list to let user choose")
                showAudioDevicesListEvent.postValue(Event(list))
            } else {
                Log.i(
                    "$TAG Found less than two devices, simply switching between earpiece & speaker"
                )
                if (::call.isInitialized) {
                    if (routeAudioToSpeaker) {
                        AudioRouteUtils.routeAudioToSpeaker(call)
                    } else {
                        AudioRouteUtils.routeAudioToEarpiece(call)
                    }
                }
            }
        }
    }

    @UiThread
    fun toggleVideo() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: request video permission
            return
        }

        coreContext.postOnCoreThread { core ->
            if (::call.isInitialized) {
                val params = core.createCallParams(call)
                if (call.conference != null) {
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
                    params?.isVideoEnabled = params?.isVideoEnabled == false
                    Log.i(
                        "$TAG Updating call with video enabled set to ${params?.isVideoEnabled}"
                    )
                }
                call.update(params)
            }
        }
    }

    @UiThread
    fun switchCamera() {
        coreContext.postOnCoreThread {
            coreContext.switchCamera()
        }
    }

    @UiThread
    fun toggleFullScreen() {
        if (fullScreenMode.value == false && isVideoEnabled.value == false) return
        fullScreenMode.value = fullScreenMode.value != true
    }

    @UiThread
    fun toggleExpandActionsMenu() {
        toggleExtraActionsBottomSheetEvent.value = Event(true)
    }

    @WorkerThread
    private fun showZrtpSasDialog(authToken: String) {
        val toRead: String
        val toListen: String
        when (call.dir) {
            Call.Dir.Incoming -> {
                toRead = authToken.substring(0, 2)
                toListen = authToken.substring(2)
            }
            else -> {
                toRead = authToken.substring(2)
                toListen = authToken.substring(0, 2)
            }
        }
        showZrtpSasDialogEvent.postValue(Event(Pair(toRead, toListen)))
    }

    @WorkerThread
    private fun updateEncryption(): Boolean {
        when (call.currentParams.mediaEncryption) {
            MediaEncryption.ZRTP -> {
                val authToken = call.authenticationToken
                val deviceIsTrusted = call.authenticationTokenVerified && authToken != null
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is ${if (deviceIsTrusted) "trusted" else "not trusted yet"}"
                )
                isRemoteDeviceTrusted.postValue(deviceIsTrusted)
                contact.value?.showTrust?.postValue(deviceIsTrusted)

                if (!deviceIsTrusted && authToken.orEmpty().isNotEmpty()) {
                    Log.i("$TAG Showing ZRTP SAS confirmation dialog")
                    showZrtpSasDialog(authToken!!.uppercase(Locale.getDefault()))
                }

                return deviceIsTrusted
            }
            MediaEncryption.SRTP, MediaEncryption.DTLS -> {
            }
            else -> {
            }
        }
        return false
    }

    @WorkerThread
    private fun configureCall(call: Call) {
        call.addListener(callListener)

        if (LinphoneUtils.isCallOutgoing(call.state)) {
            isVideoEnabled.postValue(call.params.isVideoEnabled)
        } else {
            isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
        }

        isMicrophoneMuted.postValue(call.microphoneMuted)
        val audioDevice = call.outputAudioDevice
        updateOutputAudioDevice(audioDevice)

        isOutgoing.postValue(call.dir == Call.Dir.Outgoing)

        val address = call.remoteAddress.clone()
        address.clean()
        displayedAddress.postValue(address.asStringUriOnly())

        val isDeviceTrusted = updateEncryption()
        val friend = call.core.findFriend(address)
        if (friend != null) {
            displayedName.postValue(friend.name)
            val model = ContactAvatarModel(friend)
            model.showTrust.postValue(isDeviceTrusted)
            contact.postValue(model)
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = LinphoneUtils.getDisplayName(address)
            fakeFriend.addAddress(address)
            val model = ContactAvatarModel(fakeFriend)
            model.showTrust.postValue(isDeviceTrusted)
            contact.postValue(model)
            displayedName.postValue(fakeFriend.name)
        }

        callDuration.postValue(call.duration)
    }

    private fun updateOutputAudioDevice(audioDevice: AudioDevice?) {
        isSpeakerEnabled.postValue(audioDevice?.type == AudioDevice.Type.Speaker)
        isHeadsetEnabled.postValue(
            audioDevice?.type == AudioDevice.Type.Headphones || audioDevice?.type == AudioDevice.Type.Headset
        )
        isBluetoothEnabled.postValue(audioDevice?.type == AudioDevice.Type.Bluetooth)
    }
}

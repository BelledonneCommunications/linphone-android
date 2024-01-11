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
package org.linphone.ui.call.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Alert
import org.linphone.core.AlertListenerStub
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.ChatRoom.SecurityLevel
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.tools.Log
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.call.model.ConferenceModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.history.model.NumpadModel
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CurrentCallViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Current Call ViewModel]"

        // Keys are hardcoded in SDK
        private const val ALERT_NETWORK_TYPE_KEY = "network-type"
        private const val ALERT_NETWORK_TYPE_WIFI = "wifi"
        private const val ALERT_NETWORK_TYPE_CELLULAR = "mobile"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()

    val canBePaused = MutableLiveData<Boolean>()

    val isPaused = MutableLiveData<Boolean>()

    val isPausedByRemote = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val isHeadsetEnabled = MutableLiveData<Boolean>()

    val isBluetoothEnabled = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val pipMode = MutableLiveData<Boolean>()

    val halfOpenedFolded = MutableLiveData<Boolean>()

    val isZrtpPq = MutableLiveData<Boolean>()

    val isMediaEncrypted = MutableLiveData<Boolean>()

    val hideVideo = MutableLiveData<Boolean>()

    val incomingCallTitle: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val isRemoteRecordingEvent: MutableLiveData<Event<Pair<Boolean, String>>> by lazy {
        MutableLiveData<Event<Pair<Boolean, String>>>()
    }

    val goToInitiateBlindTransferEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToEndedCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val requestRecordAudioPermission: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val requestCameraPermission: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

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

    // Conference

    val conferenceModel = ConferenceModel()

    val goToConferenceEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Extras actions

    val toggleExtraActionsBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showNumpadBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val transferInProgressEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val transferFailedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val numpadModel: NumpadModel

    val appendDigitToSearchBarEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val removedCharacterAtCurrentPositionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Alerts related

    val showLowWifiSignalEvent = MutableLiveData<Event<Boolean>>()

    val showLowCellularSignalEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var currentCall: Call

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onEncryptionChanged(call: Call, on: Boolean, authenticationToken: String?) {
            updateEncryption()
        }

        override fun onRemoteRecording(call: Call, recording: Boolean) {
            Log.i("$TAG Remote recording changed: $recording")
            isRemoteRecordingEvent.postValue(Event(Pair(recording, displayedName.value.orEmpty())))
        }

        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")
            if (LinphoneUtils.isCallOutgoing(call.state)) {
                isVideoEnabled.postValue(call.params.isVideoEnabled)
            } else if (LinphoneUtils.isCallEnding(call.state)) {
                // If current call is being terminated but there is at least one other call, switch
                val core = call.core
                val callsCount = core.callsNb
                Log.i(
                    "$TAG Call is being ended, check for another current call (currently [$callsCount] calls)"
                )
                if (callsCount > 0) {
                    val newCurrentCall = core.currentCall ?: core.calls.firstOrNull()
                    if (newCurrentCall != null) {
                        Log.i(
                            "$TAG From now on current call will be [${newCurrentCall.remoteAddress.asStringUriOnly()}]"
                        )
                        configureCall(newCurrentCall)
                    } else {
                        Log.e(
                            "$TAG Failed to get a valid call to display, go to ended call fragment"
                        )
                        updateCallDuration()
                        goToEndedCallEvent.postValue(Event(true))
                    }
                } else {
                    updateCallDuration()
                    Log.i("$TAG Call is ending, go to ended call fragment")
                    // Show that call was ended for a few seconds, then leave
                    // TODO FIXME: do not show it when call is being ended due to user terminating the call
                    goToEndedCallEvent.postValue(Event(true))
                }
            } else {
                val videoEnabled = call.currentParams.isVideoEnabled
                if (videoEnabled && isVideoEnabled.value == false) {
                    if (corePreferences.routeAudioToSpeakerWhenVideoIsEnabled) {
                        Log.i("$TAG Video is now enabled, routing audio to speaker")
                        AudioUtils.routeAudioToSpeaker(call)
                    }
                }
                isVideoEnabled.postValue(videoEnabled)

                // Toggle full screen OFF when remote disables video
                if (!videoEnabled && fullScreenMode.value == true) {
                    Log.w("$TAG Video is not longer enabled, leaving full screen mode")
                    fullScreenMode.postValue(false)
                }

                if (call.state == Call.State.Connected) {
                    if (call.conference != null) {
                        Log.i("$TAG Call is in Connected state and conference isn't null")
                        conferenceModel.configureFromCall(call)
                        goToConferenceEvent.postValue(Event(true))
                    } else {
                        conferenceModel.destroy()
                    }
                }
            }

            isPaused.postValue(isCallPaused())
            isPausedByRemote.postValue(call.state == Call.State.PausedByRemote)
            canBePaused.postValue(canCallBePaused())
        }

        @WorkerThread
        override fun onAudioDeviceChanged(call: Call, audioDevice: AudioDevice) {
            Log.i("$TAG Audio device changed [${audioDevice.id}]")
            updateOutputAudioDevice(audioDevice)
        }
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (::currentCall.isInitialized) {
                if (call != currentCall) {
                    if (call == currentCall.core.currentCall) {
                        Log.w(
                            "$TAG Current call has changed, now is [${call.remoteAddress.asStringUriOnly()}] with state [$state]"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    } else if (LinphoneUtils.isCallIncoming(call.state)) {
                        Log.w(
                            "$TAG A call is being received [${call.remoteAddress.asStringUriOnly()}], using it as current call unless declined"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    }
                }
            } else {
                Log.w(
                    "$TAG There was no current call (shouldn't be possible), using [${call.remoteAddress.asStringUriOnly()}] anyway"
                )
                configureCall(call)
            }
        }

        @WorkerThread
        override fun onNewAlertTriggered(core: Core, alert: Alert) {
            val call = alert.call
            if (call == null) {
                Log.w(
                    "$TAG Alert of type [${alert.type}] triggered but with null call, ignoring..."
                )
                return
            }

            val remote = call.remoteAddress.asStringUriOnly()
            Log.w("$TAG Alert of type [${alert.type}] triggered for call from [$remote]")
            alert.addListener(alertListener)

            if (call != currentCall) {
                Log.w("$TAG Terminated alert wasn't for current call, do not display it")
                return
            }

            if (alert.type == Alert.Type.QoSLowSignal) {
                when (val networkType = alert.informations?.getString(ALERT_NETWORK_TYPE_KEY)) {
                    ALERT_NETWORK_TYPE_WIFI -> {
                        Log.i("$TAG Triggered low signal alert is for Wi-Fi")
                        showLowWifiSignalEvent.postValue(Event(true))
                    }
                    ALERT_NETWORK_TYPE_CELLULAR -> {
                        Log.i("$TAG Triggered low signal alert is for cellular")
                        showLowCellularSignalEvent.postValue(Event(true))
                    }
                    else -> {
                        Log.w(
                            "$TAG Unexpected type of signal [$networkType] found in alert information"
                        )
                    }
                }
            }
        }

        @WorkerThread
        override fun onTransferStateChanged(core: Core, transfered: Call, state: Call.State) {
            Log.i(
                "$TAG Transferred call [${transfered.remoteAddress.asStringUriOnly()}] state changed [$state]"
            )

            // TODO FIXME: Remote is call being transferred, not transferee !
            if (state == Call.State.OutgoingProgress) {
                val displayName = coreContext.contactsManager.findDisplayName(
                    transfered.remoteAddress
                )
                transferInProgressEvent.postValue(Event(displayName))
            } else if (LinphoneUtils.isCallEnding(state)) {
                val displayName = coreContext.contactsManager.findDisplayName(
                    transfered.remoteAddress
                )
                transferFailedEvent.postValue(Event(displayName))
            }
        }
    }

    private val alertListener = object : AlertListenerStub() {
        @WorkerThread
        override fun onTerminated(alert: Alert) {
            val call = alert.call
            if (call == null) {
                Log.w(
                    "$TAG Alert of type [${alert.type}] dismissed but with null call, ignoring..."
                )
                return
            }

            val remote = call.remoteAddress.asStringUriOnly()
            Log.w("$TAG Alert of type [${alert.type}] dismissed for call from [$remote]")
            alert.removeListener(this)

            if (call != currentCall) {
                Log.w("$TAG Terminated alert wasn't for current call, do not display it")
                return
            }

            if (alert.type == Alert.Type.QoSLowSignal) {
                when (val signalType = alert.informations?.getString(ALERT_NETWORK_TYPE_KEY)) {
                    ALERT_NETWORK_TYPE_WIFI -> {
                        Log.i("$TAG Wi-Fi signal no longer low")
                        showLowWifiSignalEvent.postValue(Event(false))
                    }
                    ALERT_NETWORK_TYPE_CELLULAR -> {
                        Log.i("$TAG Cellular signal no longer low")
                        showLowCellularSignalEvent.postValue(Event(false))
                    }
                    else -> {
                        Log.w(
                            "$TAG Unexpected type of signal [$signalType] found in alert information"
                        )
                    }
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            hideVideo.postValue(!core.isVideoEnabled)
            showSwitchCamera.postValue(coreContext.showSwitchCameraButton())

            val call = core.currentCall ?: core.calls.firstOrNull()

            if (call != null) {
                Log.i("$TAG Found call [${call.remoteAddress.asStringUriOnly()}]")
                configureCall(call)
            } else {
                Log.e("$TAG Failed to find call!")
            }
        }

        isVideoEnabled.value = false
        fullScreenMode.value = false
        isMicrophoneMuted.value = ActivityCompat.checkSelfPermission(
            coreContext.context,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED

        numpadModel = NumpadModel(
            { digit -> // onDigitClicked
                appendDigitToSearchBarEvent.value = Event(digit)
                coreContext.postOnCoreThread {
                    if (::currentCall.isInitialized) {
                        Log.i("$TAG Sending DTMF [${digit.first()}]")
                        currentCall.sendDtmf(digit.first())
                    }
                }
            },
            { // OnBackspaceClicked
                removedCharacterAtCurrentPositionEvent.value = Event(true)
            },
            { // OnCallClicked
            }
        )
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
            conferenceModel.destroy()
            contact.value?.destroy()

            if (::currentCall.isInitialized) {
                currentCall.removeListener(callListener)
            }
        }
    }

    @UiThread
    fun answer() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.i("$TAG Answering call [$currentCall]")
                coreContext.answerCall(currentCall)
            }
        }
    }

    @UiThread
    fun hangUp() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.i("$TAG Terminating call [${currentCall.remoteAddress.asStringUriOnly()}]")
                currentCall.terminate()
            }
        }
    }

    @UiThread
    fun updateZrtpSas(verified: Boolean) {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                currentCall.authenticationTokenVerified = verified
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
            requestRecordAudioPermission.postValue(Event(true))
            return
        }

        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                currentCall.microphoneMuted = !currentCall.microphoneMuted
                isMicrophoneMuted.postValue(currentCall.microphoneMuted)
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

                val name = when (device.type) {
                    AudioDevice.Type.Earpiece -> {
                        AppUtils.getString(R.string.call_audio_device_type_earpiece)
                    }
                    AudioDevice.Type.Speaker -> {
                        AppUtils.getString(R.string.call_audio_device_type_speaker)
                    }
                    AudioDevice.Type.Headset -> {
                        AppUtils.getString(R.string.call_audio_device_type_headset)
                    }
                    AudioDevice.Type.Headphones -> {
                        AppUtils.getString(R.string.call_audio_device_type_headphones)
                    }
                    AudioDevice.Type.Bluetooth -> {
                        AppUtils.getFormattedString(
                            R.string.call_audio_device_type_bluetooth,
                            device.deviceName
                        )
                    }
                    AudioDevice.Type.HearingAid -> {
                        AppUtils.getFormattedString(
                            R.string.call_audio_device_type_hearing_aid,
                            device.deviceName
                        )
                    }
                    else -> device.deviceName
                }
                val currentDevice = currentCall.outputAudioDevice
                val isCurrentlyInUse = device.type == currentDevice?.type && device.deviceName == currentDevice?.deviceName
                val model = AudioDeviceModel(device, name, device.type, isCurrentlyInUse) {
                    // onSelected
                    coreContext.postOnCoreThread {
                        Log.i("$TAG Selected audio device with ID [${device.id}]")
                        if (::currentCall.isInitialized) {
                            when (device.type) {
                                AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> AudioUtils.routeAudioToHeadset(
                                    currentCall
                                )
                                AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid -> AudioUtils.routeAudioToBluetooth(
                                    currentCall
                                )
                                AudioDevice.Type.Speaker -> AudioUtils.routeAudioToSpeaker(
                                    currentCall
                                )
                                else -> AudioUtils.routeAudioToEarpiece(currentCall)
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
                if (::currentCall.isInitialized) {
                    if (routeAudioToSpeaker) {
                        AudioUtils.routeAudioToSpeaker(currentCall)
                    } else {
                        AudioUtils.routeAudioToEarpiece(currentCall)
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
            requestCameraPermission.postValue(Event(true))
            return
        }

        coreContext.postOnCoreThread { core ->
            if (::currentCall.isInitialized) {
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
                    params?.isVideoEnabled = params?.isVideoEnabled == false
                    Log.i(
                        "$TAG Updating call with video enabled set to ${params?.isVideoEnabled}"
                    )
                }
                currentCall.update(params)
            }
        }
    }

    @UiThread
    fun switchCamera() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Switching camera")
            coreContext.switchCamera()
        }
    }

    @UiThread
    fun toggleRecording() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                if (currentCall.params.isRecording) {
                    Log.i("$TAG Stopping call recording")
                    currentCall.stopRecording()
                } else {
                    Log.i("$TAG Starting call recording")
                    currentCall.startRecording()
                }
                val recording = currentCall.params.isRecording
                isRecording.postValue(recording)
            }
        }
    }

    @UiThread
    fun togglePause() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                when (isCallPaused()) {
                    true -> {
                        Log.i("$TAG Resuming call [${currentCall.remoteAddress.asStringUriOnly()}]")
                        currentCall.resume()
                    }
                    false -> {
                        Log.i("$TAG Pausing call [${currentCall.remoteAddress.asStringUriOnly()}]")
                        currentCall.pause()
                    }
                }
            }
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

    @UiThread
    fun showNumpad() {
        showNumpadBottomSheetEvent.value = Event(true)
    }

    @UiThread
    fun transferClicked() {
        coreContext.postOnCoreThread { core ->
            if (core.callsNb == 1) {
                Log.i("$TAG Only one call, initiate blind call transfer")
                goToInitiateBlindTransferEvent.postValue(Event(true))
            } else {
                val callToTransferTo = core.calls.findLast {
                    it.state == Call.State.Paused && it != currentCall
                }
                if (callToTransferTo == null) {
                    Log.e(
                        "$TAG Couldn't find a call in Paused state to transfer current call to"
                    )
                    return@postOnCoreThread
                }

                Log.i(
                    "$TAG Doing an attended transfer between currently displayed call [${currentCall.remoteAddress.asStringUriOnly()}] and paused call [${callToTransferTo.remoteAddress.asStringUriOnly()}]"
                )
                if (callToTransferTo.transferToAnother(currentCall) != 0) {
                    Log.e("$TAG Failed to make attended transfer!")
                } else {
                    Log.i("$TAG Attended transfer is successful")
                }
            }
        }
    }

    @WorkerThread
    fun blindTransferCallTo(to: Address) {
        if (::currentCall.isInitialized) {
            Log.i(
                "$TAG Call [${currentCall.remoteAddress.asStringUriOnly()}] is being blindly transferred to [${to.asStringUriOnly()}]"
            )
            if (currentCall.transferTo(to) == 0) {
                Log.i("$TAG Blind call transfer is successful")
            } else {
                Log.e("$TAG Failed to make blind call transfer!")
                val displayName = coreContext.contactsManager.findDisplayName(to)
                transferFailedEvent.postValue(Event(displayName))
            }
        }
    }

    @UiThread
    fun showZrtpSasDialogIfPossible() {
        coreContext.postOnCoreThread {
            if (currentCall.currentParams.mediaEncryption == MediaEncryption.ZRTP) {
                val authToken = currentCall.authenticationToken
                val isDeviceTrusted = currentCall.authenticationTokenVerified && authToken != null
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is ${if (isDeviceTrusted) "trusted" else "not trusted yet"}"
                )
                if (!authToken.isNullOrEmpty()) {
                    showZrtpSasDialog(authToken)
                }
            }
        }
    }

    @WorkerThread
    private fun showZrtpSasDialog(authToken: String) {
        val upperCaseAuthToken = authToken.uppercase(Locale.getDefault())
        val toRead: String
        val toListen: String
        when (currentCall.dir) {
            Call.Dir.Incoming -> {
                toRead = upperCaseAuthToken.substring(0, 2)
                toListen = upperCaseAuthToken.substring(2)
            }
            else -> {
                toRead = upperCaseAuthToken.substring(2)
                toListen = upperCaseAuthToken.substring(0, 2)
            }
        }
        showZrtpSasDialogEvent.postValue(Event(Pair(toRead, toListen)))
    }

    @WorkerThread
    private fun updateEncryption(): Boolean {
        when (currentCall.currentParams.mediaEncryption) {
            MediaEncryption.ZRTP -> {
                val authToken = currentCall.authenticationToken
                val isDeviceTrusted = currentCall.authenticationTokenVerified && authToken != null
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is ${if (isDeviceTrusted) "trusted" else "not trusted yet"}"
                )
                isRemoteDeviceTrusted.postValue(isDeviceTrusted)
                val securityLevel = if (isDeviceTrusted) SecurityLevel.Safe else SecurityLevel.Encrypted
                val avatarModel = contact.value
                if (avatarModel != null) {
                    avatarModel.trust.postValue(securityLevel)
                    contact.postValue(avatarModel!!)
                } else {
                    Log.e("$TAG No avatar model found!")
                }

                isMediaEncrypted.postValue(true)
                // When Post Quantum is available, ZRTP is Post Quantum
                isZrtpPq.postValue(coreContext.core.postQuantumAvailable)

                if (!isDeviceTrusted && !authToken.isNullOrEmpty()) {
                    Log.i("$TAG Showing ZRTP SAS confirmation dialog")
                    showZrtpSasDialog(authToken)
                }

                return isDeviceTrusted
            }
            MediaEncryption.SRTP, MediaEncryption.DTLS -> {
                isMediaEncrypted.postValue(true)
                isZrtpPq.postValue(false)
            }
            else -> {
                isMediaEncrypted.postValue(false)
                isZrtpPq.postValue(false)
            }
        }
        return false
    }

    @WorkerThread
    private fun configureCall(call: Call) {
        Log.i("$TAG Configuring call [$call] as current")
        contact.value?.destroy()

        currentCall = call
        call.addListener(callListener)

        if (call.conference != null) {
            conferenceModel.configureFromCall(call)
            goToConferenceEvent.postValue(Event(true))
        } else {
            conferenceModel.destroy()
        }

        if (call.dir == Call.Dir.Incoming) {
            if (call.core.accountList.size > 1) {
                val displayName = LinphoneUtils.getDisplayName(call.toAddress)
                incomingCallTitle.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_incoming_for_account,
                        displayName
                    )
                )
            } else {
                incomingCallTitle.postValue(AppUtils.getString(R.string.call_incoming))
            }
        }

        if (LinphoneUtils.isCallOutgoing(call.state)) {
            isVideoEnabled.postValue(call.params.isVideoEnabled)
        } else {
            isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
        }

        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "$TAG RECORD_AUDIO permission wasn't granted yet, considering microphone as muted!"
            )
            isMicrophoneMuted.postValue(false)
        } else {
            isMicrophoneMuted.postValue(call.microphoneMuted)
        }

        val audioDevice = call.outputAudioDevice
        updateOutputAudioDevice(audioDevice)

        isOutgoing.postValue(call.dir == Call.Dir.Outgoing)

        isPaused.postValue(isCallPaused())
        isPausedByRemote.postValue(call.state == Call.State.PausedByRemote)
        canBePaused.postValue(canCallBePaused())

        val address = call.remoteAddress.clone()
        address.clean()
        displayedAddress.postValue(address.asStringUriOnly())

        val conferenceInfo = coreContext.core.findConferenceInformationFromUri(
            call.remoteAddress
        )
        val model = if (conferenceInfo != null) {
            coreContext.contactsManager.getContactAvatarModelForConferenceInfo(conferenceInfo)
        } else {
            // Do not use contact avatar model from ContactsManager
            // coreContext.contactsManager.getContactAvatarModelForAddress(call.remoteAddress)
            val friend = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            if (friend != null) {
                ContactAvatarModel(friend)
            } else {
                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.name = LinphoneUtils.getDisplayName(address)
                fakeFriend.address = call.remoteAddress
                ContactAvatarModel(fakeFriend)
            }
        }
        updateEncryption()

        contact.postValue(model)
        displayedName.postValue(model.friend.name)

        isRecording.postValue(call.params.isRecording)
        isRemoteRecordingEvent.postValue(
            Event(Pair(call.remoteParams?.isRecording ?: false, displayedName.value.orEmpty()))
        )

        callDuration.postValue(call.duration)
    }

    @WorkerThread
    fun updateCallDuration() {
        if (::currentCall.isInitialized) {
            callDuration.postValue(currentCall.duration)
        }
    }

    private fun updateOutputAudioDevice(audioDevice: AudioDevice?) {
        isSpeakerEnabled.postValue(audioDevice?.type == AudioDevice.Type.Speaker)
        isHeadsetEnabled.postValue(
            audioDevice?.type == AudioDevice.Type.Headphones || audioDevice?.type == AudioDevice.Type.Headset
        )
        isBluetoothEnabled.postValue(audioDevice?.type == AudioDevice.Type.Bluetooth)
    }

    @WorkerThread
    private fun isCallPaused(): Boolean {
        if (::currentCall.isInitialized) {
            return when (currentCall.state) {
                Call.State.Paused, Call.State.Pausing -> true
                else -> false
            }
        }
        return false
    }

    @WorkerThread
    private fun canCallBePaused(): Boolean {
        return ::currentCall.isInitialized && !currentCall.mediaInProgress() && when (currentCall.state) {
            Call.State.StreamsRunning, Call.State.Pausing, Call.State.Paused -> true
            else -> false
        }
    }
}

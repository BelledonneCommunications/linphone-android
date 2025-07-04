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
package org.linphone.ui.main.meetings.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Conference
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaDirection
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class MeetingWaitingRoomViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Meeting Waiting Room ViewModel]"
    }

    val subject = MutableLiveData<String>()

    val dateTime = MutableLiveData<String>()

    val selfAvatar = MutableLiveData<ContactAvatarModel>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val isHeadsetEnabled = MutableLiveData<Boolean>()

    val isBluetoothEnabled = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isSwitchCameraAvailable = MutableLiveData<Boolean>()

    val hideVideo = MutableLiveData<Boolean>()

    val joining = MutableLiveData<Boolean>()

    val conferenceInfoFoundEvent = MutableLiveData<Event<Boolean>>()

    val showAudioDevicesListEvent: MutableLiveData<Event<ArrayList<AudioDeviceModel>>> by lazy {
        MutableLiveData<Event<ArrayList<AudioDeviceModel>>>()
    }

    val leaveWaitingRoomEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val conferenceCreationError: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var conferenceAddress: Address
    private lateinit var conferenceInfo: ConferenceInfo

    private lateinit var selectedOutputAudioDevice: AudioDevice

    private var earpieceAudioDevice: AudioDevice? = null
    private var speakerAudioDevice: AudioDevice? = null
    private var bluetoothAudioDevice: AudioDevice? = null

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (::conferenceAddress.isInitialized && conferenceAddress.weakEqual(call.remoteAddress)) {
                when (state) {
                    Call.State.End -> {
                        Log.i("$TAG Call has ended, leaving waiting room fragment")
                        joining.postValue(false)
                        leaveWaitingRoomEvent.postValue(Event(true))
                    }
                    Call.State.Error -> {
                        Log.w("$TAG Call has failed, leaving waiting room fragment")
                        joining.postValue(false)
                        leaveWaitingRoomEvent.postValue(Event(true))
                    }
                    else -> {}
                }
            }
        }

        @WorkerThread
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State?
        ) {
            val remoteAddress = conference.conferenceAddress
            if (::conferenceAddress.isInitialized && remoteAddress != null && conferenceAddress.weakEqual(
                    remoteAddress
                )
            ) {
                Log.i("$TAG Conference state changed: [$state]")
                if (conference.state == Conference.State.Created) {
                    leaveWaitingRoomEvent.postValue(Event(true))
                } else if (conference.state == Conference.State.CreationFailed) {
                    conferenceCreationError.postValue(Event(true))
                    joining.postValue(false)
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            val audioDevices = core.audioDevices
            earpieceAudioDevice = audioDevices.find { it.type == AudioDevice.Type.Earpiece }
            speakerAudioDevice = audioDevices.find { it.type == AudioDevice.Type.Speaker }
            bluetoothAudioDevice = audioDevices.find {
                it.hasCapability(
                    AudioDevice.Capabilities.CapabilityPlay
                ) && (it.type == AudioDevice.Type.Bluetooth || it.type == AudioDevice.Type.HearingAid)
            }

            hideVideo.postValue(!core.isVideoEnabled)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun findConferenceInfo(uri: String) {
        coreContext.postOnCoreThread { core ->
            val address = Factory.instance().createAddress(uri)
            if (address != null) {
                conferenceAddress = address
                val callFound = core.calls.find {
                    it.remoteAddress.weakEqual(conferenceAddress)
                }
                joining.postValue(callFound != null)

                val conferenceInfoFound = core.findConferenceInformationFromUri(address)
                if (conferenceInfoFound != null) {
                    Log.i("$TAG Conference info with SIP URI [$uri] was found")
                    conferenceInfo = conferenceInfoFound
                    configureConferenceInfo()
                    configureWaitingRoom()
                    conferenceInfoFoundEvent.postValue(Event(true))
                } else {
                    Log.e("$TAG Conference info with SIP URI [$uri] couldn't be found!")
                    showRedToastEvent.postValue(
                        Event(
                            Pair(
                                R.string.meeting_info_not_found_toast,
                                R.drawable.warning_circle
                            )
                        )
                    )
                    conferenceInfoFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse SIP URI [$uri] as Address!")
                conferenceInfoFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun setFrontCamera() {
        coreContext.postOnCoreThread { core ->
            for (camera in core.videoDevicesList) {
                if (camera.contains("Front")) {
                    Log.i("$TAG Found front facing camera [$camera], using it")
                    coreContext.core.videoDevice = camera
                    return@postOnCoreThread
                }
            }

            val first = core.videoDevicesList.firstOrNull()
            if (first != null) {
                Log.w("$TAG No front facing camera found, using first one available [$first]")
                coreContext.core.videoDevice = first
            }
        }
    }

    @UiThread
    fun join() {
        coreContext.postOnCoreThread { core ->
            if (::conferenceInfo.isInitialized) {
                Log.i("$TAG Stopping video preview")
                core.nativePreviewWindowId = null
                core.isVideoPreviewEnabled = false

                val conferenceUri = conferenceInfo.uri
                if (conferenceUri == null) {
                    Log.e("$TAG Conference Info doesn't have a conference SIP URI to call!")
                    return@postOnCoreThread
                }

                val params = core.createCallParams(null)
                params ?: return@postOnCoreThread

                params.isVideoEnabled = true
                params.videoDirection = if (isVideoEnabled.value == true) MediaDirection.SendRecv else MediaDirection.RecvOnly
                params.isMicEnabled = isMicrophoneMuted.value == false
                if (::selectedOutputAudioDevice.isInitialized) {
                    params.outputAudioDevice = selectedOutputAudioDevice
                }
                params.account = core.defaultAccount
                joining.postValue(true)
                coreContext.startCall(conferenceUri, params)
            }
        }
    }

    @UiThread
    fun cancel() {
        coreContext.postOnCoreThread { core ->
            if (::conferenceAddress.isInitialized) {
                val found = core.calls.find {
                    it.remoteAddress.weakEqual(conferenceAddress)
                }

                if (found != null) {
                    coreContext.terminateCall(found)
                } else {
                    Log.e(
                        "$TAG No call found matching conference address [${conferenceAddress.asStringUriOnly()}]"
                    )
                }
            } else {
                Log.e("$TAG No conference address stored!")
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
    fun toggleVideo() {
        isVideoEnabled.value = isVideoEnabled.value == false
        if (isVideoEnabled.value == true) {
            coreContext.postOnCoreThread {
                if (corePreferences.routeAudioToSpeakerWhenVideoIsEnabled) {
                    // If setting says to use speaker when video is enabled, use speaker instead of earpiece
                    if (!::selectedOutputAudioDevice.isInitialized || selectedOutputAudioDevice.type == AudioDevice.Type.Earpiece) {
                        val speaker = speakerAudioDevice
                        if (speaker != null) {
                            selectedOutputAudioDevice = speaker
                            updateOutputAudioDevice(speaker)
                        }
                    }
                }
            }
        }
    }

    @UiThread
    fun toggleMuteMicrophone() {
        isMicrophoneMuted.value = isMicrophoneMuted.value == false
    }

    @UiThread
    fun changeAudioOutputDevice() {
        val routeAudioToSpeaker = isSpeakerEnabled.value != true

        coreContext.postOnCoreThread { core ->
            val audioDevices = core.audioDevices
            val list = arrayListOf<AudioDeviceModel>()
            var earpieceAudioDevice: AudioDevice? = null
            var speakerAudioDevice: AudioDevice? = null

            for (device in audioDevices) {
                // Only list output audio devices
                if (!device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) continue
                when (device.type) {
                    AudioDevice.Type.Earpiece -> {
                        earpieceAudioDevice = device
                    }
                    AudioDevice.Type.Speaker -> {
                        speakerAudioDevice = device
                    }
                    else -> {}
                }

                val name = LinphoneUtils.getAudioDeviceName(device)
                val currentDevice = if (::selectedOutputAudioDevice.isInitialized) {
                    selectedOutputAudioDevice
                } else {
                    core.outputAudioDevice ?: core.defaultOutputAudioDevice
                }
                val isCurrentlyInUse = device.type == currentDevice?.type && device.deviceName == currentDevice?.deviceName
                val model = AudioDeviceModel(device, name, device.type, isCurrentlyInUse, true) {
                    // onSelected
                    coreContext.postOnCoreThread {
                        Log.i("$TAG Selected audio device with ID [${device.id}]")
                        selectedOutputAudioDevice = device
                        updateOutputAudioDevice(device)
                    }
                }
                list.add(model)
                Log.i("$TAG Found audio device [${device.id}]")
            }

            if (list.size > 2) {
                Log.i("$TAG Found more than two devices, showing list to let user choose")
                showAudioDevicesListEvent.postValue(Event(list))
            } else {
                Log.i(
                    "$TAG Found less than two devices, simply switching between earpiece & speaker"
                )
                val newAudioDevice = if (routeAudioToSpeaker) {
                    speakerAudioDevice
                } else {
                    earpieceAudioDevice ?: speakerAudioDevice
                }
                if (newAudioDevice != null) {
                    selectedOutputAudioDevice = newAudioDevice
                    updateOutputAudioDevice(newAudioDevice)
                }
            }
        }
    }

    @WorkerThread
    private fun configureConferenceInfo() {
        if (::conferenceInfo.isInitialized) {
            subject.postValue(conferenceInfo.subject)

            val timestamp = conferenceInfo.dateTime
            val duration = conferenceInfo.duration
            val date = TimestampUtils.toString(
                timestamp,
                onlyDate = true,
                shortDate = false,
                hideYear = false
            )
            val startTime = TimestampUtils.timeToString(timestamp)
            val end = timestamp + (duration * 60)
            val endTime = TimestampUtils.timeToString(end)
            dateTime.postValue("$date | $startTime - $endTime")

            val localAddress = coreContext.core.defaultAccount?.params?.identityAddress
            val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                localAddress
            )
            selfAvatar.postValue(avatarModel)
        }
    }

    @WorkerThread
    private fun configureWaitingRoom() {
        val core = coreContext.core

        val cameraPermissionGranted = ActivityCompat.checkSelfPermission(
            coreContext.context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val videoEnabled = core.isVideoEnabled && cameraPermissionGranted
        isVideoEnabled.postValue(videoEnabled)

        isSwitchCameraAvailable.postValue(coreContext.showSwitchCameraButton())

        isMicrophoneMuted.postValue(!core.isMicEnabled)

        initOutputAudioDevice(videoEnabled)
    }

    @WorkerThread
    private fun initOutputAudioDevice(videoEnabled: Boolean) {
        val core = coreContext.core

        val audioDevice = if (corePreferences.routeAudioToSpeakerWhenVideoIsEnabled && videoEnabled) {
            // If setting says to use speaker when video is enabled, use speaker instead of earpiece
            val defaultDevice = core.outputAudioDevice ?: core.defaultOutputAudioDevice
            if (defaultDevice?.type == AudioDevice.Type.Earpiece) {
                speakerAudioDevice
            } else {
                defaultDevice
            }
        } else {
            core.outputAudioDevice ?: core.defaultOutputAudioDevice
        }
        if (audioDevice != null) {
            selectedOutputAudioDevice = audioDevice
            updateOutputAudioDevice(audioDevice)
        }
    }

    @WorkerThread
    private fun updateOutputAudioDevice(audioDevice: AudioDevice) {
        Log.i("$TAG Selected output audio device is [${audioDevice.id}]")
        isSpeakerEnabled.postValue(audioDevice.type == AudioDevice.Type.Speaker)
        isHeadsetEnabled.postValue(
            audioDevice.type == AudioDevice.Type.Headphones || audioDevice.type == AudioDevice.Type.Headset
        )
        isBluetoothEnabled.postValue(audioDevice.type == AudioDevice.Type.Bluetooth)
    }
}

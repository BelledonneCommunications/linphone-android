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
package org.linphone.activities.main.settings.viewmodels

import androidx.databinding.ViewDataBinding
import androidx.lifecycle.MutableLiveData
import java.lang.NumberFormatException
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.AudioDevice
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.EcCalibratorStatus
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class AudioSettingsViewModel : GenericSettingsViewModel() {
    val askAudioRecordPermissionForEchoCancellerCalibrationEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }
    val askAudioRecordPermissionForEchoTesterEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val echoCancellationListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isEchoCancellationEnabled = newValue
        }
    }
    val echoCancellation = MutableLiveData<Boolean>()
    val listener = object : CoreListenerStub() {
        override fun onEcCalibrationResult(core: Core, status: EcCalibratorStatus, delayMs: Int) {
            if (status == EcCalibratorStatus.InProgress) return
            echoCancellerCalibrationFinished(status, delayMs)
        }
    }

    val echoCancellerCalibrationListener = object : SettingListenerStub() {
        override fun onClicked() {
            if (PermissionHelper.get().hasRecordAudioPermission()) {
                startEchoCancellerCalibration()
            } else {
                askAudioRecordPermissionForEchoCancellerCalibrationEvent.value = Event(true)
            }
        }
    }
    val echoCalibration = MutableLiveData<String>()

    val echoTesterListener = object : SettingListenerStub() {
        override fun onClicked() {
            if (PermissionHelper.get().hasRecordAudioPermission()) {
                if (echoTesterIsRunning) {
                    stopEchoTester()
                } else {
                    startEchoTester()
                }
            } else {
                askAudioRecordPermissionForEchoTesterEvent.value = Event(true)
            }
        }
    }
    private var echoTesterIsRunning = false
    val echoTesterStatus = MutableLiveData<String>()

    val adaptiveRateControlListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isAdaptiveRateControlEnabled = newValue
        }
    }
    val adaptiveRateControl = MutableLiveData<Boolean>()

    val inputAudioDeviceListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            val values = inputAudioDeviceValues.value.orEmpty()
            if (values.size > position) {
                core.defaultInputAudioDevice = values[position]
            }
        }
    }
    val inputAudioDeviceIndex = MutableLiveData<Int>()
    val inputAudioDeviceLabels = MutableLiveData<ArrayList<String>>()
    private val inputAudioDeviceValues = MutableLiveData<ArrayList<AudioDevice>>()

    val outputAudioDeviceListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            val values = outputAudioDeviceValues.value.orEmpty()
            if (values.size > position) {
                core.defaultOutputAudioDevice = values[position]
            }
        }
    }
    val outputAudioDeviceIndex = MutableLiveData<Int>()
    val outputAudioDeviceLabels = MutableLiveData<ArrayList<String>>()
    private val outputAudioDeviceValues = MutableLiveData<ArrayList<AudioDevice>>()

    val preferBluetoothDevicesListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.routeAudioToBluetoothIfAvailable = newValue
        }
    }
    val preferBluetoothDevices = MutableLiveData<Boolean>()

    val codecBitrateListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            for (payloadType in core.audioPayloadTypes) {
                if (payloadType.isVbr) {
                    payloadType.normalBitrate = codecBitrateValues[position]
                }
            }
        }
    }
    val codecBitrateIndex = MutableLiveData<Int>()
    val codecBitrateLabels = MutableLiveData<ArrayList<String>>()
    private val codecBitrateValues = arrayListOf(10, 15, 20, 36, 64, 128)

    val microphoneGainListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                core.micGainDb = newValue.toFloat()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val microphoneGain = MutableLiveData<Float>()

    val playbackGainListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                core.playbackGainDb = newValue.toFloat()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val playbackGain = MutableLiveData<Float>()

    val audioCodecs = MutableLiveData<ArrayList<ViewDataBinding>>()

    init {
        echoCancellation.value = core.isEchoCancellationEnabled
        adaptiveRateControl.value = core.isAdaptiveRateControlEnabled
        echoCalibration.value = if (core.isEchoCancellationEnabled) {
            prefs.getString(R.string.audio_settings_echo_cancellation_calibration_value).format(prefs.echoCancellerCalibration)
        } else {
            prefs.getString(R.string.audio_settings_echo_canceller_calibration_summary)
        }
        echoTesterStatus.value = prefs.getString(R.string.audio_settings_echo_tester_summary)
        preferBluetoothDevices.value = prefs.routeAudioToBluetoothIfAvailable
        initInputAudioDevicesList()
        initOutputAudioDevicesList()
        initCodecBitrateList()
        microphoneGain.value = core.micGainDb
        playbackGain.value = core.playbackGainDb
    }

    fun startEchoCancellerCalibration() {
        if (echoTesterIsRunning) {
            stopEchoTester()
        }

        core.addListener(listener)
        core.startEchoCancellerCalibration()
        echoCalibration.value = prefs.getString(R.string.audio_settings_echo_cancellation_calibration_started)
    }

    fun echoCancellerCalibrationFinished(status: EcCalibratorStatus, delay: Int) {
        core.removeListener(listener)

        when (status) {
            EcCalibratorStatus.InProgress -> {
                echoCalibration.value = prefs.getString(R.string.audio_settings_echo_cancellation_calibration_started)
            }
            EcCalibratorStatus.DoneNoEcho -> {
                echoCalibration.value = prefs.getString(R.string.audio_settings_echo_cancellation_calibration_no_echo)
            }
            EcCalibratorStatus.Done -> {
                echoCalibration.value = prefs.getString(R.string.audio_settings_echo_cancellation_calibration_value).format(delay)
            }
            EcCalibratorStatus.Failed -> {
                echoCalibration.value = prefs.getString(R.string.audio_settings_echo_cancellation_calibration_failed)
            }
        }

        echoCancellation.value = status != EcCalibratorStatus.DoneNoEcho
    }

    fun startEchoTester() {
        echoTesterIsRunning = true
        echoTesterStatus.value = prefs.getString(R.string.audio_settings_echo_tester_summary_is_running)
        core.startEchoTester(0)
    }

    fun stopEchoTester() {
        echoTesterIsRunning = false
        echoTesterStatus.value = prefs.getString(R.string.audio_settings_echo_tester_summary_is_stopped)
        core.stopEchoTester()
    }

    private fun initInputAudioDevicesList() {
        val labels = arrayListOf<String>()
        val values = arrayListOf<AudioDevice>()
        var index = 0
        val default = core.defaultInputAudioDevice

        for (audioDevice in core.extendedAudioDevices) {
            if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityRecord)) {
                labels.add(audioDevice.id)
                values.add(audioDevice)
                if (audioDevice.id == default?.id) {
                    inputAudioDeviceIndex.value = index
                }
                index += 1
            }
        }
        inputAudioDeviceLabels.value = labels
        inputAudioDeviceValues.value = values
    }

    private fun initOutputAudioDevicesList() {
        val labels = arrayListOf<String>()
        val values = arrayListOf<AudioDevice>()
        var index = 0
        val default = core.defaultOutputAudioDevice

        for (audioDevice in core.extendedAudioDevices) {
            if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                labels.add(audioDevice.id)
                values.add(audioDevice)
                if (audioDevice.id == default?.id) {
                    outputAudioDeviceIndex.value = index
                }
                index += 1
            }
        }
        outputAudioDeviceLabels.value = labels
        outputAudioDeviceValues.value = values
    }

    private fun initCodecBitrateList() {
        val labels = arrayListOf<String>()
        for (value in codecBitrateValues) {
            labels.add("$value kbits/s")
        }
        codecBitrateLabels.value = labels

        var currentValue = 36
        for (payloadType in core.audioPayloadTypes) {
            if (payloadType.isVbr && payloadType.normalBitrate in codecBitrateValues) {
                currentValue = payloadType.normalBitrate
                break
            }
        }
        codecBitrateIndex.value = codecBitrateValues.indexOf(currentValue)
    }
}

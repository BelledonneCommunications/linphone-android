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
package org.linphone.utils

import android.telecom.CallAudioState
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.compatibility.Compatibility
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.telecom.TelecomHelper

class AudioRouteUtils {
    companion object {
        private fun applyAudioRouteChange(
            call: Call?,
            types: List<AudioDevice.Type>,
            output: Boolean = true
        ) {
            val currentCall = if (coreContext.core.callsNb > 0) {
                call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            } else {
                Log.w("[Audio Route Helper] No call found, setting audio route on Core")
                null
            }
            val conference = coreContext.core.conference
            val capability = if (output)
                AudioDevice.Capabilities.CapabilityPlay
            else
                AudioDevice.Capabilities.CapabilityRecord
            val preferredDriver = if (output) {
                coreContext.core.defaultOutputAudioDevice?.driverName
            } else {
                coreContext.core.defaultInputAudioDevice?.driverName
            }

            val extendedAudioDevices = coreContext.core.extendedAudioDevices
            Log.i("[Audio Route Helper] Looking for an ${if (output) "output" else "input"} audio device with capability [$capability], driver name [$preferredDriver] and type [$types] in extended audio devices list (size ${extendedAudioDevices.size})")
            val foundAudioDevice = extendedAudioDevices.find {
                it.driverName == preferredDriver && types.contains(it.type) && it.hasCapability(capability)
            }
            val audioDevice = if (foundAudioDevice == null) {
                Log.w("[Audio Route Helper] Failed to find an audio device with capability [$capability], driver name [$preferredDriver] and type [$types]")
                extendedAudioDevices.find {
                    types.contains(it.type) && it.hasCapability(capability)
                }
            } else {
                foundAudioDevice
            }

            if (audioDevice == null) {
                Log.e("[Audio Route Helper] Couldn't find audio device with capability [$capability] and type [$types]")
                for (device in extendedAudioDevices) {
                    // TODO: switch to debug?
                    Log.i("[Audio Route Helper] Extended audio device: [${device.deviceName} (${device.driverName}) ${device.type} / ${device.capabilities}]")
                }
                return
            }
            if (conference != null && conference.isIn) {
                Log.i("[Audio Route Helper] Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName} (${audioDevice.driverName})], routing conference audio to it")
                if (output) conference.outputAudioDevice = audioDevice
                else conference.inputAudioDevice = audioDevice
            } else if (currentCall != null) {
                Log.i("[Audio Route Helper] Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName} (${audioDevice.driverName})], routing call audio to it")
                if (output) currentCall.outputAudioDevice = audioDevice
                else currentCall.inputAudioDevice = audioDevice
            } else {
                Log.i("[Audio Route Helper] Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName} (${audioDevice.driverName})], changing core default audio device")
                if (output) coreContext.core.outputAudioDevice = audioDevice
                else coreContext.core.inputAudioDevice = audioDevice
            }
        }

        private fun changeCaptureDeviceToMatchAudioRoute(call: Call?, types: List<AudioDevice.Type>) {
            when (types.first()) {
                AudioDevice.Type.Bluetooth -> {
                    if (isBluetoothAudioRecorderAvailable()) {
                        Log.i("[Audio Route Helper] Bluetooth device is able to record audio, also change input audio device")
                        applyAudioRouteChange(call, arrayListOf(AudioDevice.Type.Bluetooth), false)
                    }
                }
                AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> {
                    if (isHeadsetAudioRecorderAvailable()) {
                        Log.i("[Audio Route Helper] Headphones/Headset device is able to record audio, also change input audio device")
                        applyAudioRouteChange(call, (arrayListOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset)), false)
                    }
                }
                AudioDevice.Type.Earpiece, AudioDevice.Type.Speaker -> {
                    Log.i("[Audio Route Helper] Audio route requested to Earpiece or Speaker, setting input to Microphone")
                    applyAudioRouteChange(call, (arrayListOf(AudioDevice.Type.Microphone)), false)
                }
                else -> {
                    Log.w("[Audio Route Helper] Unexpected audio device type: ${types.first()}")
                }
            }
        }

        private fun routeAudioTo(
            call: Call?,
            types: List<AudioDevice.Type>,
            skipTelecom: Boolean = false
        ) {
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls.firstOrNull()
            if (currentCall != null && !skipTelecom && TelecomHelper.exists()) {
                Log.i("[Audio Route Helper] Call provided & Telecom Helper exists, trying to dispatch audio route change through Telecom API")
                val connection = TelecomHelper.get().findConnectionForCallId(currentCall.callLog.callId.orEmpty())
                if (connection != null) {
                    val route = when (types.first()) {
                        AudioDevice.Type.Earpiece -> CallAudioState.ROUTE_EARPIECE
                        AudioDevice.Type.Speaker -> CallAudioState.ROUTE_SPEAKER
                        AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> CallAudioState.ROUTE_WIRED_HEADSET
                        AudioDevice.Type.Bluetooth, AudioDevice.Type.BluetoothA2DP -> CallAudioState.ROUTE_BLUETOOTH
                        else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    }
                    Log.i("[Audio Route Helper] Telecom Helper & matching connection found, dispatching audio route change through it")
                    // We will be called here again by NativeCallWrapper.onCallAudioStateChanged()
                    // but this time with skipTelecom = true
                    if (!Compatibility.changeAudioRouteForTelecomManager(connection, route)) {
                        Log.w("[Audio Route Helper] Connection is already using this route internally, make the change!")
                        applyAudioRouteChange(currentCall, types)
                        changeCaptureDeviceToMatchAudioRoute(currentCall, types)
                    }
                } else {
                    Log.w("[Audio Route Helper] Telecom Helper found but no matching connection!")
                    applyAudioRouteChange(currentCall, types)
                    changeCaptureDeviceToMatchAudioRoute(currentCall, types)
                }
            } else {
                applyAudioRouteChange(call, types)
                changeCaptureDeviceToMatchAudioRoute(call, types)
            }
        }

        fun routeAudioToEarpiece(call: Call? = null, skipTelecom: Boolean = false) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Earpiece), skipTelecom)
        }

        fun routeAudioToSpeaker(call: Call? = null, skipTelecom: Boolean = false) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Speaker), skipTelecom)
        }

        fun routeAudioToBluetooth(call: Call? = null, skipTelecom: Boolean = false) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Bluetooth), skipTelecom)
        }

        fun routeAudioToHeadset(call: Call? = null, skipTelecom: Boolean = false) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset), skipTelecom)
        }

        fun isSpeakerAudioRouteCurrentlyUsed(call: Call? = null): Boolean {
            val currentCall = if (coreContext.core.callsNb > 0) {
                call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            } else {
                Log.w("[Audio Route Helper] No call found, checking audio route on Core")
                null
            }
            val conference = coreContext.core.conference

            val audioDevice = if (conference != null && conference.isIn)
                conference.outputAudioDevice
            else if (currentCall != null)
                currentCall.outputAudioDevice
            else
                coreContext.core.outputAudioDevice

            if (audioDevice == null) return false
            Log.i("[Audio Route Helper] Playback audio currently in use is [${audioDevice.deviceName} (${audioDevice.driverName}) ${audioDevice.type}]")
            return audioDevice.type == AudioDevice.Type.Speaker
        }

        fun isBluetoothAudioRouteCurrentlyUsed(call: Call? = null): Boolean {
            if (coreContext.core.callsNb == 0) {
                Log.w("[Audio Route Helper] No call found, so bluetooth audio route isn't used")
                return false
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            val conference = coreContext.core.conference

            val audioDevice = if (conference != null && conference.isIn)
                conference.outputAudioDevice
            else
                currentCall.outputAudioDevice

            if (audioDevice == null) return false
            Log.i("[Audio Route Helper] Playback audio device currently in use is [${audioDevice.deviceName} (${audioDevice.driverName}) ${audioDevice.type}]")
            return audioDevice.type == AudioDevice.Type.Bluetooth
        }

        fun isBluetoothAudioRouteAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Bluetooth &&
                    audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
                ) {
                    Log.i("[Audio Route Helper] Found bluetooth audio device [${audioDevice.deviceName} (${audioDevice.driverName})]")
                    return true
                }
            }
            return false
        }

        private fun isBluetoothAudioRecorderAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Bluetooth &&
                    audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityRecord)
                ) {
                    Log.i("[Audio Route Helper] Found bluetooth audio recorder [${audioDevice.deviceName} (${audioDevice.driverName})]")
                    return true
                }
            }
            return false
        }

        fun isHeadsetAudioRouteAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if ((audioDevice.type == AudioDevice.Type.Headset || audioDevice.type == AudioDevice.Type.Headphones) &&
                    audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
                ) {
                    Log.i("[Audio Route Helper] Found headset/headphones audio device [${audioDevice.deviceName} (${audioDevice.driverName})]")
                    return true
                }
            }
            return false
        }

        private fun isHeadsetAudioRecorderAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if ((audioDevice.type == AudioDevice.Type.Headset || audioDevice.type == AudioDevice.Type.Headphones) &&
                    audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityRecord)
                ) {
                    Log.i("[Audio Route Helper] Found headset/headphones audio recorder [${audioDevice.deviceName} (${audioDevice.driverName})]")
                    return true
                }
            }
            return false
        }

        fun getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage(): String? {
            // In case no headphones/headset/hearing aid/bluetooth is connected, use speaker sound card to play recordings, otherwise use earpiece
            // If none are available, default one will be used
            var headphonesCard: String? = null
            var bluetoothCard: String? = null
            var speakerCard: String? = null
            var earpieceCard: String? = null
            for (device in coreContext.core.audioDevices) {
                if (device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                    when (device.type) {
                        AudioDevice.Type.Headphones, AudioDevice.Type.Headset, AudioDevice.Type.HearingAid -> {
                            headphonesCard = device.id
                        }
                        AudioDevice.Type.Bluetooth -> {
                            bluetoothCard = device.id
                        }
                        AudioDevice.Type.Speaker -> {
                            speakerCard = device.id
                        }
                        AudioDevice.Type.Earpiece -> {
                            earpieceCard = device.id
                        }
                        else -> {}
                    }
                }
            }
            Log.i("[Audio Route Helper] Found headset/headphones/hearingAid sound card [$headphonesCard], bluetooth sound card [$bluetoothCard], speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]")
            return headphonesCard ?: bluetoothCard ?: speakerCard ?: earpieceCard
        }

        fun getAudioRecordingDeviceForVoiceMessage(): AudioDevice? {
            // In case no headphones/headset/hearing aid/bluetooth is connected, use microphone
            // If none are available, default one will be used
            var bluetoothAudioDevice: AudioDevice? = null
            var headsetAudioDevice: AudioDevice? = null
            var builtinMicrophone: AudioDevice? = null
            for (device in coreContext.core.audioDevices) {
                if (device.hasCapability(AudioDevice.Capabilities.CapabilityRecord)) {
                    when (device.type) {
                        AudioDevice.Type.Bluetooth -> {
                            bluetoothAudioDevice = device
                        }
                        AudioDevice.Type.Headset, AudioDevice.Type.HearingAid, AudioDevice.Type.Headphones -> {
                            headsetAudioDevice = device
                        }
                        AudioDevice.Type.Microphone -> {
                            builtinMicrophone = device
                        }
                        else -> {}
                    }
                }
            }
            Log.i("[Audio Route Helper] Found headset/headphones/hearingAid [${headsetAudioDevice?.id}], bluetooth [${bluetoothAudioDevice?.id}] and builtin microphone [${builtinMicrophone?.id}]")
            return headsetAudioDevice ?: bluetoothAudioDevice ?: builtinMicrophone
        }
    }
}

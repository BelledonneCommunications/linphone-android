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
            val listSize = types.size
            val stringBuilder = StringBuilder()
            var index = 0
            while (index < listSize) {
                stringBuilder.append(types[index].name)
                if (index < listSize - 1) {
                    stringBuilder.append("/")
                }
                index++
            }
            val typesNames = stringBuilder.toString()

            if (coreContext.core.callsNb == 0) {
                Log.e("[Audio Route Helper] No call found, aborting [$typesNames] audio route change")
                return
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            val conference = coreContext.core.conference
            val capability = if (output)
                AudioDevice.Capabilities.CapabilityPlay
            else
                AudioDevice.Capabilities.CapabilityRecord

            for (audioDevice in coreContext.core.audioDevices) {
                if (types.contains(audioDevice.type) && audioDevice.hasCapability(capability)) {
                    if (conference != null && conference.isIn) {
                        Log.i("[Audio Route Helper] Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName}], routing conference audio to it")
                        if (output) conference.outputAudioDevice = audioDevice
                        else conference.inputAudioDevice = audioDevice
                    } else {
                        Log.i("[Audio Route Helper] Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName}], routing call audio to it")
                        if (output) currentCall.outputAudioDevice = audioDevice
                        else currentCall.inputAudioDevice = audioDevice
                    }
                    return
                }
            }
            Log.e("[Audio Route Helper] Couldn't find [$typesNames] audio device")
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
                        Log.i("[Audio Route Helper] Headphones/headset device is able to record audio, also change input audio device")
                        applyAudioRouteChange(call, (arrayListOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset)), false)
                    }
                }
            }
        }

        private fun routeAudioTo(
            call: Call?,
            types: List<AudioDevice.Type>,
            skipTelecom: Boolean = false
        ) {
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            if ((call != null || currentCall != null) && !skipTelecom && TelecomHelper.exists()) {
                val callToUse = call ?: currentCall
                Log.i("[Audio Route Helper] Call provided & Telecom Helper exists, trying to dispatch audio route change through Telecom API")
                val connection = TelecomHelper.get().findConnectionForCallId(callToUse.callLog.callId)
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
                    Compatibility.changeAudioRouteForTelecomManager(connection, route)
                } else {
                    Log.w("[Audio Route Helper] Telecom Helper found but no matching connection!")
                    applyAudioRouteChange(callToUse, types)
                    changeCaptureDeviceToMatchAudioRoute(callToUse, types)
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
            if (coreContext.core.callsNb == 0) {
                Log.w("[Audio Route Helper] No call found, so speaker audio route isn't used")
                return false
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            val conference = coreContext.core.conference

            val audioDevice = if (conference != null && conference.isIn) conference.outputAudioDevice else currentCall.outputAudioDevice
            Log.i("[Audio Route Helper] Playback audio device currently in use is [${audioDevice?.deviceName}] with type (${audioDevice?.type})")
            return audioDevice?.type == AudioDevice.Type.Speaker
        }

        fun isBluetoothAudioRouteCurrentlyUsed(call: Call? = null): Boolean {
            if (coreContext.core.callsNb == 0) {
                Log.w("[Audio Route Helper] No call found, so bluetooth audio route isn't used")
                return false
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            val conference = coreContext.core.conference

            val audioDevice = if (conference != null && conference.isIn) conference.outputAudioDevice else currentCall.outputAudioDevice
            Log.i("[Audio Route Helper] Playback audio device currently in use is [${audioDevice?.deviceName}] with type (${audioDevice?.type})")
            return audioDevice?.type == AudioDevice.Type.Bluetooth
        }

        fun isBluetoothAudioRouteAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Bluetooth &&
                    audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
                ) {
                    Log.i("[Audio Route Helper] Found bluetooth audio device [${audioDevice.deviceName}]")
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
                    Log.i("[Audio Route Helper] Found bluetooth audio recorder [${audioDevice.deviceName}]")
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
                    Log.i("[Audio Route Helper] Found headset/headphones audio device [${audioDevice.deviceName}]")
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
                    Log.i("[Audio Route Helper] Found headset/headphones audio recorder [${audioDevice.deviceName}]")
                    return true
                }
            }
            return false
        }
    }
}

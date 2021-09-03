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

import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.tools.Log

class AudioRouteUtils {
    companion object {
        private fun routeAudioTo(types: List<AudioDevice.Type>, call: Call? = null) {
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

            for (audioDevice in coreContext.core.audioDevices) {
                if (types.contains(audioDevice.type) && audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                    if (conference != null && conference.isIn) {
                        Log.i("[Audio Route Helper] Found [${audioDevice.type}] audio device [${audioDevice.deviceName}], routing conference audio to it")
                        conference.outputAudioDevice = audioDevice
                    } else {
                        Log.i("[Audio Route Helper] Found [${audioDevice.type}] audio device [${audioDevice.deviceName}], routing call audio to it")
                        currentCall.outputAudioDevice = audioDevice
                    }
                    return
                }
            }
            Log.e("[Audio Route Helper] Couldn't find [$typesNames] audio device")
        }

        fun routeAudioToEarpiece(call: Call? = null) {
            routeAudioTo(arrayListOf(AudioDevice.Type.Earpiece), call)
        }

        fun routeAudioToSpeaker(call: Call? = null) {
            routeAudioTo(arrayListOf(AudioDevice.Type.Speaker), call)
        }

        fun routeAudioToBluetooth(call: Call? = null) {
            routeAudioTo(arrayListOf(AudioDevice.Type.Bluetooth), call)
        }

        fun routeAudioToHeadset(call: Call? = null) {
            routeAudioTo(arrayListOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset), call)
        }

        fun isSpeakerAudioRouteCurrentlyUsed(call: Call? = null): Boolean {
            if (coreContext.core.callsNb == 0) {
                Log.w("[Audio Route Helper] No call found, so speaker audio route isn't used")
                return false
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            val conference = coreContext.core.conference

            val audioDevice = if (conference != null && conference.isIn) conference.outputAudioDevice else currentCall.outputAudioDevice
            Log.i("[Audio Route Helper] Audio device currently in use is [${audioDevice?.deviceName}] with type (${audioDevice?.type})")
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
            Log.i("[Audio Route Helper] Audio device currently in use is [${audioDevice?.deviceName}] with type (${audioDevice?.type})")
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
    }
}

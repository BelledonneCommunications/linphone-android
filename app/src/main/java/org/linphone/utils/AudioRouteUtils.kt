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
        fun routeAudioToEarpiece(call: Call? = null) {
            if (coreContext.core.callsNb == 0) {
                Log.e("[Audio Route Helper] No call found, aborting earpiece audio route change")
                return
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]

            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Earpiece) {
                    Log.i("[Audio Route Helper] Found earpiece audio device [${audioDevice.deviceName}], routing audio to it")
                    currentCall.outputAudioDevice = audioDevice
                    return
                }
            }
            Log.e("[Audio Route Helper] Couldn't find earpiece audio device")
        }

        fun routeAudioToSpeaker(call: Call? = null) {
            if (coreContext.core.callsNb == 0) {
                Log.e("[Audio Route Helper] No call found, aborting speaker audio route change")
                return
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]

            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Speaker) {
                    Log.i("[Audio Route Helper] Found speaker audio device [${audioDevice.deviceName}], routing audio to it")
                    currentCall.outputAudioDevice = audioDevice
                    return
                }
            }
            Log.e("[Audio Route Helper] Couldn't find speaker audio device")
        }

        fun routeAudioToBluetooth(call: Call? = null) {
            if (coreContext.core.callsNb == 0) {
                Log.e("[Audio Route Helper] No call found, aborting bluetooth audio route change")
                return
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]

            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Bluetooth) {
                    if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                        Log.i("[Audio Route Helper] Found bluetooth audio device [${audioDevice.deviceName}], routing audio to it")
                        currentCall.outputAudioDevice = audioDevice
                        return
                    }
                }
            }
            Log.e("[Audio Route Helper] Couldn't find bluetooth audio device")
        }

        fun routeAudioToHeadset(call: Call? = null) {
            if (coreContext.core.callsNb == 0) {
                Log.e("[Audio Route Helper] No call found, aborting headset audio route change")
                return
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]

            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Headphones || audioDevice.type == AudioDevice.Type.Headset) {
                    if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                        Log.i("[Audio Route Helper] Found headset audio device [${audioDevice.deviceName}], routing audio to it")
                        currentCall.outputAudioDevice = audioDevice
                        return
                    }
                }
            }
            Log.e("[Audio Route Helper] Couldn't find headset audio device")
        }

        fun isBluetoothAudioRouteCurrentlyUsed(call: Call? = null): Boolean {
            if (coreContext.core.callsNb == 0) {
                Log.w("[Audio Route Helper] No call found, so bluetooth audio route isn't used")
                return false
            }
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]

            val audioDevice = currentCall.outputAudioDevice
            Log.i("[Audio Route Helper] Audio device currently in use is [${audioDevice?.deviceName}]")
            return audioDevice?.type == AudioDevice.Type.Bluetooth
        }

        fun isBluetoothAudioRouteAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if (audioDevice.type == AudioDevice.Type.Bluetooth &&
                        audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                    Log.i("[Audio Route Helper] Found bluetooth audio device [${audioDevice.deviceName}]")
                    return true
                }
            }
            return false
        }

        fun isHeadsetAudioRouteAvailable(): Boolean {
            for (audioDevice in coreContext.core.audioDevices) {
                if ((audioDevice.type == AudioDevice.Type.Headset || audioDevice.type == AudioDevice.Type.Headphones) &&
                        audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                    Log.i("[Audio Route Helper] Found headset/headphones audio device [${audioDevice.deviceName}]")
                    return true
                }
            }
            return false
        }
    }
}

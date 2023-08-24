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
package org.linphone.utils

import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.tools.Log

class AudioRouteUtils {
    companion object {
        private const val TAG = "[Audio Route Utils]"

        @WorkerThread
        fun isSpeakerAudioRouteCurrentlyUsed(call: Call? = null): Boolean {
            val currentCall = if (coreContext.core.callsNb > 0) {
                call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            } else {
                Log.w("$TAG No call found, checking audio route on Core")
                null
            }
            val conference = coreContext.core.conference

            val audioDevice = if (conference != null && conference.isIn) {
                conference.outputAudioDevice
            } else if (currentCall != null) {
                currentCall.outputAudioDevice
            } else {
                coreContext.core.outputAudioDevice
            }

            if (audioDevice == null) return false
            Log.i(
                "$TAG Playback audio device currently in use is [${audioDevice.deviceName} (${audioDevice.driverName}) ${audioDevice.type}]"
            )
            return audioDevice.type == AudioDevice.Type.Speaker
        }

        @WorkerThread
        fun routeAudioToEarpiece(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Earpiece))
        }

        @WorkerThread
        fun routeAudioToSpeaker(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Speaker))
        }

        @WorkerThread
        private fun routeAudioTo(
            call: Call?,
            types: List<AudioDevice.Type>
        ) {
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls.firstOrNull()
            if (currentCall != null) {
                applyAudioRouteChange(currentCall, types)
                changeCaptureDeviceToMatchAudioRoute(currentCall, types)
            } else {
                applyAudioRouteChange(call, types)
                changeCaptureDeviceToMatchAudioRoute(call, types)
            }
        }

        @WorkerThread
        private fun applyAudioRouteChange(
            call: Call?,
            types: List<AudioDevice.Type>,
            output: Boolean = true
        ) {
            val currentCall = if (coreContext.core.callsNb > 0) {
                call ?: coreContext.core.currentCall ?: coreContext.core.calls[0]
            } else {
                Log.w("$TAG No call found, setting audio route on Core")
                null
            }

            val capability = if (output) {
                AudioDevice.Capabilities.CapabilityPlay
            } else {
                AudioDevice.Capabilities.CapabilityRecord
            }
            val preferredDriver = if (output) {
                coreContext.core.defaultOutputAudioDevice?.driverName
            } else {
                coreContext.core.defaultInputAudioDevice?.driverName
            }

            val extendedAudioDevices = coreContext.core.extendedAudioDevices
            Log.i(
                "$TAG Looking for an ${if (output) "output" else "input"} audio device with capability [$capability], driver name [$preferredDriver] and type [$types] in extended audio devices list (size ${extendedAudioDevices.size})"
            )
            val foundAudioDevice = extendedAudioDevices.find {
                it.driverName == preferredDriver && types.contains(it.type) && it.hasCapability(
                    capability
                )
            }
            val audioDevice = if (foundAudioDevice == null) {
                Log.w(
                    "$TAG Failed to find an audio device with capability [$capability], driver name [$preferredDriver] and type [$types]"
                )
                extendedAudioDevices.find {
                    types.contains(it.type) && it.hasCapability(capability)
                }
            } else {
                foundAudioDevice
            }

            if (audioDevice == null) {
                Log.e(
                    "$TAG Couldn't find audio device with capability [$capability] and type [$types]"
                )
                for (device in extendedAudioDevices) {
                    Log.i(
                        "$TAG Extended audio device: [${device.deviceName} (${device.driverName}) ${device.type} / ${device.capabilities}]"
                    )
                }
                return
            }
            if (currentCall != null) {
                Log.i(
                    "$TAG Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName} (${audioDevice.driverName})], routing call audio to it"
                )
                if (output) {
                    currentCall.outputAudioDevice = audioDevice
                } else {
                    currentCall.inputAudioDevice = audioDevice
                }
            } else {
                Log.i(
                    "$TAG Found [${audioDevice.type}] ${if (output) "playback" else "recorder"} audio device [${audioDevice.deviceName} (${audioDevice.driverName})], changing core default audio device"
                )
                if (output) {
                    coreContext.core.outputAudioDevice = audioDevice
                } else {
                    coreContext.core.inputAudioDevice = audioDevice
                }
            }
        }

        @WorkerThread
        private fun changeCaptureDeviceToMatchAudioRoute(call: Call?, types: List<AudioDevice.Type>) {
            when (types.first()) {
                AudioDevice.Type.Earpiece, AudioDevice.Type.Speaker -> {
                    Log.i(
                        "$TAG Audio route requested to Earpiece or Speaker, setting input to Microphone"
                    )
                    applyAudioRouteChange(call, (arrayListOf(AudioDevice.Type.Microphone)), false)
                }
                else -> {
                    Log.w("$TAG Unexpected audio device type: ${types.first()}")
                }
            }
        }
    }
}

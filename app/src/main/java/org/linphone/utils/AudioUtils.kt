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

import android.content.Context
import android.media.AudioManager
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.tools.Log

class AudioUtils {
    companion object {
        private const val TAG = "[Audio Utils]"

        @WorkerThread
        fun routeAudioToEarpiece(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Earpiece))
        }

        @WorkerThread
        fun routeAudioToSpeaker(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Speaker))
        }

        @WorkerThread
        fun routeAudioToBluetooth(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Bluetooth))
        }

        @WorkerThread
        fun routeAudioToHearingAid(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.HearingAid))
        }

        @WorkerThread
        fun routeAudioToEitherBluetoothOrHearingAid(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid))
        }

        @WorkerThread
        fun routeAudioToHeadset(call: Call? = null) {
            routeAudioTo(
                call,
                arrayListOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset)
            )
        }

        @WorkerThread
        fun routeAudioToHdmi(call: Call? = null) {
            routeAudioTo(call, arrayListOf(AudioDevice.Type.Hdmi))
        }

        @WorkerThread
        private fun routeAudioTo(
            call: Call?,
            types: List<AudioDevice.Type>
        ) {
            val currentCall = call ?: coreContext.core.currentCall ?: coreContext.core.calls.firstOrNull()
            if (currentCall != null) {
                applyAudioRouteChange(currentCall, types)
            } else {
                applyAudioRouteChange(null, types)
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
            applyAudioRouteChangeInLinphone(currentCall, types, output)
        }

        fun applyAudioRouteChangeInLinphone(
            call: Call?,
            types: List<AudioDevice.Type>,
            output: Boolean = true
        ): Boolean {
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
                "$TAG Looking for an [${if (output) "output" else "input"}] audio device with capability [$capability], driver name [$preferredDriver] and type [$types] in extended audio devices list (size ${extendedAudioDevices.size})"
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
                return false
            }
            if (call != null) {
                Log.i(
                    "$TAG Found [${audioDevice.type}] [${if (output) "playback" else "recorder"}] audio device [${audioDevice.deviceName} (${audioDevice.driverName})], routing call audio to it"
                )
                if (output) {
                    call.outputAudioDevice = audioDevice
                } else {
                    call.inputAudioDevice = audioDevice
                }
            } else {
                Log.i(
                    "$TAG Found [${audioDevice.type}] [${if (output) "playback" else "recorder"}] audio device [${audioDevice.deviceName} (${audioDevice.driverName})], changing core default audio device"
                )
                if (output) {
                    coreContext.core.outputAudioDevice = audioDevice
                } else {
                    coreContext.core.inputAudioDevice = audioDevice
                }
            }
            return true
        }

        @WorkerThread
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
                        AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> {
                            headphonesCard = device.id
                        }
                        AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid -> {
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
            Log.i(
                "$TAG Found headset/headphones/hearingAid sound card [$headphonesCard], bluetooth sound card [$bluetoothCard], speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]"
            )
            return if (coreContext.isConnectedToAndroidAuto) {
                Log.w(
                    "$TAG Device seems to be connected to Android Auto, do not use bluetooth sound card, priority order is headphone > speaker > earpiece"
                )
                headphonesCard ?: speakerCard ?: earpieceCard
            } else {
                Log.i(
                    "$TAG Device doesn't seem to be connected to Android Auto, use headphone > bluetooth > speaker > earpiece sound card in priority order"
                )
                headphonesCard ?: bluetoothCard ?: speakerCard ?: earpieceCard
            }
        }

        @WorkerThread
        fun getAudioRecordingDeviceIdForVoiceMessage(): AudioDevice? {
            // In case no headset/hearing aid/bluetooth is connected, use microphone sound card
            // If none are available, default one will be used
            var headsetCard: AudioDevice? = null
            var bluetoothCard: AudioDevice? = null
            var microphoneCard: AudioDevice? = null
            for (device in coreContext.core.audioDevices) {
                if (device.hasCapability(AudioDevice.Capabilities.CapabilityRecord)) {
                    when (device.type) {
                        AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> {
                            headsetCard = device
                        }
                        AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid -> {
                            bluetoothCard = device
                        }
                        AudioDevice.Type.Microphone -> {
                            microphoneCard = device
                        }
                        else -> {}
                    }
                }
            }
            Log.i(
                "$TAG Found headset/headphones sound card [$headsetCard], bluetooth/hearingAid sound card [$bluetoothCard] and microphone card [$microphoneCard]"
            )
            return headsetCard ?: bluetoothCard ?: microphoneCard
        }

        @AnyThread
        fun acquireAudioFocusForVoiceRecordingOrPlayback(context: Context): AudioFocusRequestCompat {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttrs = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                .build()

            val request =
                AudioFocusRequestCompat.Builder(
                    AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                    .setAudioAttributes(audioAttrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
            when (AudioManagerCompat.requestAudioFocus(audioManager, request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.i("$TAG Voice recording/playback audio focus request granted")
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.w("$TAG Voice recording/playback audio focus request failed")
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    Log.w("$TAG Voice recording/playback audio focus request delayed")
                }
            }
            return request
        }

        @AnyThread
        fun releaseAudioFocusForVoiceRecordingOrPlayback(
            context: Context,
            request: AudioFocusRequestCompat
        ) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
            Log.i("$TAG Voice recording/playback audio focus request abandoned")
        }

        @AnyThread
        fun isMediaVolumeLow(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i("$TAG Current media volume value is $currentVolume, max value is $maxVolume")
            return currentVolume <= maxVolume * 0.5
        }
    }
}

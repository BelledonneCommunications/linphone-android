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
package org.linphone.telecom

import android.telecom.DisconnectCause
import androidx.annotation.WorkerThread
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class TelecomCallControlCallback(
    private val call: Call,
    private val callControl: CallControlScope,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "[Telecom Call Control Callback]"
    }

    private var availableEndpoints: List<CallEndpointCompat> = arrayListOf()
    private var currentEndpoint = CallEndpointCompat.TYPE_UNKNOWN

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State?, message: String) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")
            if (state == Call.State.Connected) {
                if (call.dir == Call.Dir.Incoming) {
                    val isVideo = LinphoneUtils.isVideoEnabled(call)
                    val type = if (isVideo) {
                        CallAttributesCompat.Companion.CALL_TYPE_VIDEO_CALL
                    } else {
                        CallAttributesCompat.Companion.CALL_TYPE_AUDIO_CALL
                    }
                    scope.launch {
                        Log.i("$TAG Answering ${if (isVideo) "video" else "audio"} call")
                        callControl.answer(type)
                    }
                } else {
                    scope.launch {
                        Log.i("$TAG Setting call active")
                        callControl.setActive()
                    }
                }
            } else if (state == Call.State.End) {
                scope.launch {
                    Log.i("$TAG Disconnecting call because it has ended")
                    callControl.disconnect(DisconnectCause(DisconnectCause.LOCAL))
                }
            } else if (state == Call.State.Error) {
                scope.launch {
                    Log.w("$TAG Disconnecting call due to error [$message]")
                    try {
                        // For some reason DisconnectCause.ERROR triggers an IllegalArgumentException
                        callControl.disconnect(DisconnectCause(DisconnectCause.REJECTED))
                    } catch (ise: IllegalArgumentException) {
                        Log.e("$TAG Couldn't terminate call control with REJECTED cause: $ise")
                    }
                }
            } else if (state == Call.State.Pausing) {
                scope.launch {
                    Log.i("$TAG Pausing call")
                    callControl.setInactive()
                }
            } else if (state == Call.State.Resuming) {
                scope.launch {
                    Log.i("$TAG Resuming call")
                    callControl.setActive()
                }
            }
        }
    }

    init {
        // NEVER CALL ANY METHOD FROM callControl OBJECT IN HERE!
        Log.i("$TAG Created callback for call")
        coreContext.postOnCoreThread {
            call.addListener(callListener)
        }
    }

    fun onCallControlCallbackSet() {
        Log.i(
            "$TAG Callback have been set for call, Telecom call ID is [${callControl.getCallId()}]"
        )

        callControl.availableEndpoints.onEach { list ->
            Log.i("$TAG New available audio endpoints list")
            if (availableEndpoints != list) {
                Log.i(
                    "$TAG List size of available audio endpoints has changed, reload sound devices in SDK"
                )
                coreContext.postOnCoreThread { core ->
                    core.reloadSoundDevices()
                    Log.i("$TAG Sound devices reloaded")
                }
            }

            availableEndpoints = list
            for (endpoint in list) {
                Log.i("$TAG Available audio endpoint [${endpoint.name}]")
            }
        }.launchIn(scope)

        callControl.currentCallEndpoint.onEach { endpoint ->
            Log.i("$TAG We're asked to use [${endpoint.name}] audio endpoint")
            // Change audio route in SDK, this way the usual listener will trigger
            // and we'll be able to update the UI accordingly
            val route = arrayListOf<AudioDevice.Type>()
            when (endpoint.type) {
                CallEndpointCompat.Companion.TYPE_EARPIECE -> {
                    route.add(AudioDevice.Type.Earpiece)
                }
                CallEndpointCompat.Companion.TYPE_SPEAKER -> {
                    route.add(AudioDevice.Type.Speaker)
                }
                CallEndpointCompat.Companion.TYPE_BLUETOOTH -> {
                    route.add(AudioDevice.Type.Bluetooth)
                }
                CallEndpointCompat.Companion.TYPE_WIRED_HEADSET -> {
                    route.add(AudioDevice.Type.Headphones)
                    route.add(AudioDevice.Type.Headset)
                }
            }
            if (route.isNotEmpty()) {
                currentEndpoint = endpoint.type
                coreContext.postOnCoreThread {
                    if (!AudioUtils.applyAudioRouteChangeInLinphone(call, route)) {
                        Log.w("$TAG Failed to apply audio route change, trying again in 200ms")
                        coreContext.postOnCoreThreadDelayed({
                            AudioUtils.applyAudioRouteChangeInLinphone(call, route)
                        }, 200)
                    }
                }
            }
        }.launchIn(scope)

        callControl.isMuted.onEach { muted ->
            coreContext.postOnCoreThread {
                val callState = call.state
                Log.i(
                    "$TAG We're asked to [${if (muted) "mute" else "unmute"}] the call in state [$callState]"
                )
                // Only follow un-mute requests for not outgoing calls (such as joining a conference muted)
                // and if connected to Android Auto that has a way to let user mute/unmute from the car directly.
                if (muted || (!LinphoneUtils.isCallOutgoing(callState, false) && coreContext.isConnectedToAndroidAuto)) {
                    call.microphoneMuted = muted
                    coreContext.refreshMicrophoneMuteStateEvent.postValue(Event(true))
                } else {
                    if (coreContext.isConnectedToAndroidAuto) {
                        Log.w(
                            "$TAG Not following unmute request because call is in state [$callState]"
                        )
                    } else {
                        Log.w(
                            "$TAG Not following unmute request because user isn't connected to Android Auto and call is in state [$callState]"
                        )
                    }
                }
            }
        }.launchIn(scope)
    }

    fun applyAudioRouteToCallWithId(routes: List<AudioDevice.Type>): Boolean {
        Log.i("$TAG Looking for audio endpoint with type [${routes.first()}]")

        var wiredHeadsetFound = false
        for (endpoint in availableEndpoints) {
            Log.i(
                "$TAG Found audio endpoint [${endpoint.name}] with type [${endpoint.type}]"
            )
            val matches = when (endpoint.type) {
                CallEndpointCompat.Companion.TYPE_EARPIECE -> {
                    routes.find { it == AudioDevice.Type.Earpiece }
                }
                CallEndpointCompat.Companion.TYPE_SPEAKER -> {
                    routes.find { it == AudioDevice.Type.Speaker }
                }
                CallEndpointCompat.Companion.TYPE_BLUETOOTH -> {
                    routes.find { it == AudioDevice.Type.Bluetooth }
                }
                CallEndpointCompat.Companion.TYPE_WIRED_HEADSET -> {
                    wiredHeadsetFound = true
                    routes.find { it == AudioDevice.Type.Headset || it == AudioDevice.Type.Headphones }
                }
                else -> null
            }

            if (matches != null) {
                Log.i(
                    "$TAG Found matching audio endpoint [${endpoint.name}], trying to use it"
                )
                if (currentEndpoint == endpoint.type) {
                    Log.w("$TAG Endpoint already in use, skipping")
                    continue
                }

                scope.launch {
                    Log.i("$TAG Requesting audio endpoint change with [${endpoint.name}]")
                    var result: CallControlResult = callControl.requestEndpointChange(endpoint)
                    var attempts = 1
                    while (result is CallControlResult.Error && attempts <= 10) {
                        delay(100)
                        Log.i(
                            "$TAG Previous attempt failed [$result], requesting again audio endpoint change with [${endpoint.name}]"
                        )
                        result = callControl.requestEndpointChange(endpoint)
                        attempts += 1
                    }

                    if (result is CallControlResult.Error) {
                        Log.e("$TAG Failed to change endpoint audio device, error [$result]")
                    } else {
                        Log.i(
                            "$TAG It took [$attempts] attempt(s) to change endpoint audio device..."
                        )
                        currentEndpoint = endpoint.type
                    }
                }

                return true
            }
        }

        if (routes.size == 1 && routes[0] == AudioDevice.Type.Earpiece && wiredHeadsetFound) {
            Log.e("$TAG User asked for earpiece but endpoint doesn't exists!")
        } else {
            Log.e("$TAG No matching endpoint found")
        }
        return false
    }
}

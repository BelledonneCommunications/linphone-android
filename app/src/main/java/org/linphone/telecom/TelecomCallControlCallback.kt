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
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlCallback
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
import org.linphone.utils.AudioRouteUtils
import org.linphone.utils.LinphoneUtils

class TelecomCallControlCallback constructor(
    private val call: Call,
    private val callControl: CallControlScope,
    private val scope: CoroutineScope
) : CallControlCallback {
    companion object {
        private const val TAG = "[Telecom Call Control Callback]"
    }

    private var availableEndpoints: List<CallEndpointCompat> = arrayListOf()

    private val callListener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State?, message: String) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")
            if (state == Call.State.Connected) {
                if (call.dir == Call.Dir.Incoming) {
                    scope.launch {
                        val type = if (call.currentParams.isVideoEnabled) {
                            CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                        } else {
                            CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                        }
                        Log.i("$TAG Answering call with type [$type]")
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
                    Log.i("$TAG Disconnecting call")
                    callControl.disconnect(DisconnectCause(DisconnectCause.REMOTE))
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
            if (availableEndpoints.size != list.size) {
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
                else -> null
            }
            if (route.isNotEmpty()) {
                coreContext.postOnCoreThread {
                    AudioRouteUtils.applyAudioRouteChangeInLinphone(call, route)
                }
            }
        }.launchIn(scope)

        callControl.isMuted.onEach { muted ->
            Log.i("$TAG We're asked to ${if (muted) "mute" else "unmute"} the call")
            coreContext.postOnCoreThread {
                call.microphoneMuted = muted
            }
        }.launchIn(scope)
    }

    fun applyAudioRouteToCallWithId(routes: List<AudioDevice.Type>) {
        Log.i("$TAG Looking for audio endpoint with type [${routes.first()}]")

        for (endpoint in availableEndpoints) {
            Log.i(
                "$TAG Found audio endpoint [${endpoint.name}] with type [${endpoint.type}]"
            )
            val found = when (endpoint.type) {
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
                    routes.find { it == AudioDevice.Type.Headset || it == AudioDevice.Type.Headphones }
                }
                else -> null
            }

            if (found != null) {
                Log.i(
                    "$TAG Found matching audio endpoint [${endpoint.name}], trying to use it"
                )

                scope.launch {
                    Log.i("$TAG Requesting audio endpoint change with [${endpoint.name}]")
                    var audioRouteUpdated = callControl.requestEndpointChange(endpoint)
                    var attempts = 1
                    while (!audioRouteUpdated && attempts <= 10) {
                        delay(100)
                        Log.i("$TAG Requesting audio endpoint change with [${endpoint.name}]")
                        audioRouteUpdated = callControl.requestEndpointChange(endpoint)
                        attempts += 1
                    }

                    if (!audioRouteUpdated) {
                        Log.e("$TAG Failed to change endpoint audio device!")
                    } else {
                        Log.i("$TAG It took [$attempts] to change endpoint audio device...")
                    }
                }
            } else {
                Log.w("$TAG No matching audio endpoint found...")
            }
        }
    }

    override suspend fun onAnswer(callType: Int): Boolean {
        Log.i("$TAG We're asked to answer the call with type [$callType]")
        coreContext.postOnCoreThread {
            if (LinphoneUtils.isCallIncoming(call.state)) {
                Log.i("$TAG Answering call")
                coreContext.answerCall(call) // TODO: use call type
            }
        }
        return true
    }

    override suspend fun onDisconnect(disconnectCause: DisconnectCause): Boolean {
        Log.i("$TAG We're asked to terminate the call with reason [$disconnectCause]")
        coreContext.postOnCoreThread {
            Log.i("$TAG Terminating call [${call.remoteAddress.asStringUriOnly()}]")
            call.terminate() // TODO: use cause
        }
        return true
    }

    override suspend fun onSetActive(): Boolean {
        Log.i("$TAG We're asked to resume the call")
        coreContext.postOnCoreThread {
            Log.i("$TAG Resuming call")
            call.resume()
        }
        return true
    }

    override suspend fun onSetInactive(): Boolean {
        Log.i("$TAG We're asked to pause the call")
        coreContext.postOnCoreThread {
            Log.i("$TAG Pausing call")
            call.pause()
        }
        return true
    }
}

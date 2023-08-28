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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.tools.Log

class TelecomCallControlCallback constructor(
    private val call: Call,
    private val callControl: CallControlScope,
    private val scope: CoroutineScope
) : CallControlCallback {
    companion object {
        private const val TAG = "[Telecom Call Control Callback]"
    }

    private val callListener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State?, message: String) {
            Log.i("$TAG Call state changed [$state]")
            if (state == Call.State.Connected) {
                if (call.dir == Call.Dir.Incoming) {
                    scope.launch {
                        Log.i("$TAG Answering call")
                        callControl.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL) // TODO
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
            for (endpoint in list) {
                Log.i("$TAG Available audio endpoint [${endpoint.name}]")
            }
        }.launchIn(scope)

        callControl.currentCallEndpoint.onEach { endpoint ->
            Log.i("$TAG We're asked to use [${endpoint.name}] audio endpoint")
        }.launchIn(scope)

        callControl.isMuted.onEach { muted ->
            Log.i("$TAG We're asked to ${if (muted) "mute" else "unmute"} the call")
            call.microphoneMuted = muted
        }.launchIn(scope)
    }

    override suspend fun onAnswer(callType: Int): Boolean {
        Log.i("$TAG We're asked to answer the call")
        coreContext.postOnCoreThread {
            if (call.state == Call.State.IncomingReceived || call.state == Call.State.IncomingEarlyMedia) {
                Log.i("$TAG Answering call")
                call.accept()
            }
        }
        return true
    }

    override suspend fun onDisconnect(disconnectCause: DisconnectCause): Boolean {
        Log.i("$TAG We're asked to terminate the call with reason [$disconnectCause]")
        coreContext.postOnCoreThread {
            Log.i("$TAG Terminating call")
            call.terminate()
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

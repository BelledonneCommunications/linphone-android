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
package org.linphone.telecom

import android.annotation.TargetApi
import android.graphics.drawable.Icon
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.StatusHints
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.utils.AudioRouteUtils

@TargetApi(29)
class NativeCallWrapper(var callId: String) : Connection() {
    init {
        val properties = connectionProperties or PROPERTY_SELF_MANAGED
        connectionProperties = properties

        val capabilities = connectionCapabilities or CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD
        connectionCapabilities = capabilities

        audioModeIsVoip = true
        statusHints = StatusHints(
            "",
            Icon.createWithResource(coreContext.context, R.drawable.linphone_logo_tinted),
            Bundle()
        )
    }

    override fun onStateChanged(state: Int) {
        Log.i("[Connection] Telecom state changed [${intStateToString(state)}] for call with id: $callId")
        super.onStateChanged(state)
    }

    override fun onAnswer(videoState: Int) {
        Log.i("[Connection] Answering telecom call with id: $callId")
        getCall()?.accept() ?: selfDestroy()
    }

    override fun onHold() {
        Log.i("[Connection] Pausing telecom call with id: $callId")
        getCall()?.pause() ?: selfDestroy()
        setOnHold()
    }

    override fun onUnhold() {
        Log.i("[Connection] Resuming telecom call with id: $callId")
        getCall()?.resume() ?: selfDestroy()
        setActive()
    }

    override fun onCallAudioStateChanged(state: CallAudioState) {
        Log.i("[Connection] Audio state changed: $state")

        val call = getCall()
        if (call != null) {
            if (getState() != STATE_ACTIVE && getState() != STATE_DIALING) {
                Log.w("[Connection] Call state isn't STATE_ACTIVE or STATE_DIALING, ignoring mute mic & audio route directive from TelecomManager")
                return
            }

            if (state.isMuted != call.microphoneMuted) {
                Log.w("[Connection] Connection audio state asks for changing in mute: ${state.isMuted}, currently is ${call.microphoneMuted}")
                if (state.isMuted) {
                    Log.w("[Connection] Muting microphone")
                    call.microphoneMuted = true
                }
            }

            when (state.route) {
                CallAudioState.ROUTE_EARPIECE -> AudioRouteUtils.routeAudioToEarpiece(call, true)
                CallAudioState.ROUTE_SPEAKER -> AudioRouteUtils.routeAudioToSpeaker(call, true)
                CallAudioState.ROUTE_BLUETOOTH -> AudioRouteUtils.routeAudioToBluetooth(call, true)
                CallAudioState.ROUTE_WIRED_HEADSET -> AudioRouteUtils.routeAudioToHeadset(call, true)
            }
        } else {
            selfDestroy()
        }
    }

    override fun onPlayDtmfTone(c: Char) {
        Log.i("[Connection] Sending DTMF [$c] in telecom call with id: $callId")
        getCall()?.sendDtmf(c) ?: selfDestroy()
    }

    override fun onDisconnect() {
        Log.i("[Connection] Terminating telecom call with id: $callId")
        getCall()?.terminate() ?: selfDestroy()
    }

    override fun onAbort() {
        Log.i("[Connection] Aborting telecom call with id: $callId")
        getCall()?.terminate() ?: selfDestroy()
    }

    override fun onReject() {
        Log.i("[Connection] Rejecting telecom call with id: $callId")
        getCall()?.terminate() ?: selfDestroy()
    }

    override fun onSilence() {
        Log.i("[Connection] Call with id: $callId asked to be silenced")
        coreContext.core.stopRinging()
    }

    fun stateAsString(): String {
        return stateToString(state)
    }

    private fun getCall(): Call? {
        return coreContext.core.getCallByCallid(callId)
    }

    private fun selfDestroy() {
        if (coreContext.core.callsNb == 0) {
            Log.e("[Connection] No call in Core, destroy connection")
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }
    }

    private fun intStateToString(state: Int): String {
        return when (state) {
            STATE_INITIALIZING -> "STATE_INITIALIZING"
            STATE_NEW -> "STATE_NEW"
            STATE_RINGING -> "STATE_RINGING"
            STATE_DIALING -> "STATE_DIALING"
            STATE_ACTIVE -> "STATE_ACTIVE"
            STATE_HOLDING -> "STATE_HOLDING"
            STATE_DISCONNECTED -> "STATE_DISCONNECTED"
            STATE_PULLING_CALL -> "STATE_PULLING_CALL"
            else -> "STATE_UNKNOWN"
        }
    }
}

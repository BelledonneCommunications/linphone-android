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

import android.telecom.Connection
import android.telecom.DisconnectCause
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.tools.Log

class NativeCallWrapper(var callId: String) : Connection() {
    init {
        var capabilities = connectionCapabilities
        capabilities = capabilities or CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD or
                CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL or CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL or
                CAPABILITY_CAN_PAUSE_VIDEO
        connectionCapabilities = capabilities
    }

    override fun onStateChanged(state: Int) {
        Log.i("[Connection] Telecom state changed [$state] for call with id: $callId")
        super.onStateChanged(state)
    }

    override fun onAnswer(videoState: Int) {
        Log.i("[Connection] Answering telecom call with id: $callId")
        getCall()?.accept()
    }

    override fun onHold() {
        Log.i("[Connection] Pausing telecom call with id: $callId")
        getCall()?.pause()
        setOnHold()
    }

    override fun onUnhold() {
        Log.i("[Connection] Resuming telecom call with id: $callId")
        getCall()?.resume()
        setActive()
    }

    override fun onPlayDtmfTone(c: Char) {
        Log.i("[Connection] Seding DTMF [$c] in telecom call with id: $callId")
        getCall()?.sendDtmf(c)
    }

    override fun onDisconnect() {
        Log.i("[Connection] Terminating telecom call with id: $callId")
        getCall()?.terminate()
    }

    override fun onAbort() {
        Log.i("[Connection] Aborting telecom call with id: $callId")
        getCall()?.terminate()
    }

    override fun onReject() {
        Log.i("[Connection] Rejecting telecom call with id: $callId")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        getCall()?.terminate()
    }

    private fun getCall(): Call? {
        return coreContext.core.getCallByCallid(callId)
    }
}

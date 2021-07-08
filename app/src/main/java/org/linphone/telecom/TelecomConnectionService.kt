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

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.telecom.*
import android.telecom.TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class TelecomConnectionService : ConnectionService() {
    private val connections = arrayListOf<NativeCallWrapper>()

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("[Telecom Connection Service] call [${call.callLog.callId}] state changed: $state")
            when (call.state) {
                Call.State.OutgoingProgress -> {
                    for (connection in connections) {
                        if (connection.callId.isEmpty()) {
                            connection.callId = call.callLog.callId
                        }
                    }
                }
                Call.State.StreamsRunning -> onCallConnected(call)
                Call.State.End, Call.State.Released -> onCallEnded(call)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.i("[Telecom Connection Service] onCreate()")
        coreContext.core.addListener(listener)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i("[Telecom Connection Service] onUnbind()")
        coreContext.core.removeListener(listener)

        return super.onUnbind(intent)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val accountHandle = request.accountHandle
        val componentName = ComponentName(applicationContext, this.javaClass)

        return if (accountHandle != null && componentName == accountHandle.componentName) {
            Log.i("[Telecom Connection Service] Creating outgoing connection")

            var callId = request.extras.getString("Call-ID")
            val displayName = request.extras.getString("DisplayName")
            if (callId == null) {
                callId = coreContext.core.currentCall?.callLog?.callId ?: ""
            }

            val connection = NativeCallWrapper(callId)
            connection.audioModeIsVoip = true
            connection.setDialing()

            val providedHandle = request.address
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED)
            connection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
            Log.i("[Telecom Connection Service] Address is $providedHandle")

            connection.videoProvider = NativeVideoProvider()
            val videoState = request.extras.getInt(EXTRA_START_CALL_WITH_VIDEO_STATE)
            Log.i("[Telecom Connection Service] Video state is ${LinphoneUtils.videoStateToString(videoState)}")
            connection.videoState = videoState

            connections.add(connection)
            connection
        } else {
            Log.e("[Telecom Connection Service] Error: $accountHandle $componentName")
            Connection.createFailedConnection(
                DisconnectCause(
                    DisconnectCause.ERROR,
                    "Invalid inputs: $accountHandle $componentName"
                )
            )
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val accountHandle = request.accountHandle
        val componentName = ComponentName(applicationContext, this.javaClass)

        return if (accountHandle != null && componentName == accountHandle.componentName) {
            Log.i("[Telecom Connection Service] Creating incoming connection")

            var callId = request.extras.getString("Call-ID")
            val displayName = request.extras.getString("DisplayName")
            if (callId == null) {
                callId = coreContext.core.currentCall?.callLog?.callId ?: ""
            }

            val connection = NativeCallWrapper(callId)
            connection.audioModeIsVoip = true
            connection.setRinging()

            val providedHandle =
                request.extras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED)
            connection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
            Log.i("[Telecom Connection Service] Address is $providedHandle")

            connection.videoProvider = NativeVideoProvider()
            val videoState = request.extras.getInt(EXTRA_START_CALL_WITH_VIDEO_STATE)
            Log.i("[Telecom Connection Service] Video state is ${LinphoneUtils.videoStateToString(videoState)}")
            connection.videoState = videoState

            connections.add(connection)
            connection
        } else {
            Log.e("[Telecom Connection Service] Error: $accountHandle $componentName")
            Connection.createFailedConnection(
                DisconnectCause(
                    DisconnectCause.ERROR,
                    "Invalid inputs: $accountHandle $componentName"
                )
            )
        }
    }

    private fun getConnectionForCallId(callId: String): NativeCallWrapper? {
        return connections.find { connection ->
            connection.callId == callId
        }
    }

    private fun onCallEnded(call: Call) {
        val connection = getConnectionForCallId(call.callLog.callId)
        connection ?: return

        connections.remove(connection)
        (connection.videoProvider as? NativeVideoProvider)?.destroy()
        connection.setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        connection.destroy()
    }

    private fun onCallConnected(call: Call) {
        val connection = getConnectionForCallId(call.callLog.callId)
        connection ?: return

        if (connection.state != Connection.STATE_HOLDING) {
            connection.setActive()
        }
    }
}

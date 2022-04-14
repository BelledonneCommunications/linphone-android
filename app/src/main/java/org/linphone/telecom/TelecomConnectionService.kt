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
import android.os.Handler
import android.telecom.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class TelecomConnectionService : ConnectionService() {
    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("[Telecom Connection Service] onCallStateChanged from listener")
            onCallStateChanged(call, state, core)
        }

        override fun onLastCallEnded(core: Core) {
            val connectionsCount = TelecomHelper.get().connections.size
            if (connectionsCount > 0) {
                Log.w("[Telecom Connection Service] Last call ended, there is $connectionsCount connections still alive")
                for (connection in TelecomHelper.get().connections) {
                    Log.w("[Telecom Connection Service] Destroying zombie connection ${connection.callId}")
                    connection.setDisconnected(DisconnectCause(DisconnectCause.OTHER))
                    connection.destroy()
                }
            }
        }
    }

    private fun onCallStateChanged(
        call: Call?,
        state: Call.State?,
        core: Core
    ) {
        Log.i("[Telecom Connection Service] call [${call?.callLog?.callId}] state changed: $state")
        when (call?.state) {
            Call.State.OutgoingProgress -> {
                for (connection in TelecomHelper.get().connections) {
                    if (connection.callId.isEmpty()) {
                        Log.i("[Telecom Connection Service] Updating connection with call ID: ${call.callLog.callId}")
                        connection.callId = core.currentCall?.callLog?.callId ?: ""
                    }
                }
            }
            Call.State.Error -> onCallError(call)
            Call.State.End, Call.State.Released -> onCallEnded(call)
            Call.State.StreamsRunning -> onCallConnected(call)
        }
    }

    override fun onCreate() {
        coreContext.core.addListener(listener)
        super.onCreate()
        Log.i("[Telecom Connection Service] onCreate()")
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
        if (coreContext.core.callsNb == 0) {
            Log.w("[Telecom Connection Service] No call in Core, aborting outgoing connection!")
            return Connection.createCanceledConnection()
        }

        val accountHandle = request.accountHandle
        val componentName = ComponentName(applicationContext, this.javaClass)
        return if (accountHandle != null && componentName == accountHandle.componentName) {
            Log.i("[Telecom Connection Service] Creating outgoing connection")

            val extras = request.extras
            var callId = extras.getString("Call-ID")
            val displayName = extras.getString("DisplayName")
            if (callId == null) {
                callId = coreContext.core.currentCall?.callLog?.callId ?: ""
            }
            Log.i("[Telecom Connection Service] Outgoing connection is for call [$callId] with display name [$displayName]")

            // Prevents user dialing back from native dialer app history
            if (callId.isEmpty() && displayName.isNullOrEmpty()) {
                Log.e("[Telecom Connection Service] Looks like a call was made from native dialer history, aborting")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.OTHER))
            }

            val connection = NativeCallWrapper(callId)
            if (connection.state != Connection.STATE_ACTIVE) {
                connection.setDialing()
            }

            val providedHandle = request.address
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED)
            connection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
            Log.i("[Telecom Connection Service] Address is $providedHandle")

            TelecomHelper.get().connections.add(connection)
            // CLB: Added setting the call state changed from creating, because the call can be directly accepted, and the state wouldn't be set correctly.
            Handler().postDelayed(
                {
                    Log.i("[Telecom Connection Service] onCallStateChanged delayed")
                    onCallStateChanged(coreContext.core.currentCall, coreContext.core.currentCall?.state, coreContext.core)
                },
                100
            )
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
        if (coreContext.core.callsNb == 0) {
            Log.w("[Telecom Connection Service] No call in Core, aborting incoming connection!")
            return Connection.createCanceledConnection()
        }

        val accountHandle = request.accountHandle
        val componentName = ComponentName(applicationContext, this.javaClass)
        return if (accountHandle != null && componentName == accountHandle.componentName) {
            Log.i("[Telecom Connection Service] Creating incoming connection")

            val extras = request.extras
            val incomingExtras = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
            var callId = incomingExtras?.getString("Call-ID")
            val displayName = incomingExtras?.getString("DisplayName")
            if (callId == null) {
                callId = coreContext.core.currentCall?.callLog?.callId ?: ""
            }
            Log.i("[Telecom Connection Service] Incoming connection is for call [$callId] with display name [$displayName]")

            val connection = NativeCallWrapper(callId)
            connection.setRinging()

            val providedHandle =
                incomingExtras?.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED)
            connection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
            Log.i("[Telecom Connection Service] Address is $providedHandle")

            TelecomHelper.get().connections.add(connection)
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

    private fun onCallError(call: Call) {
        val callId = call.callLog.callId
        val connection = TelecomHelper.get().findConnectionForCallId(callId.orEmpty())
        if (connection == null) {
            Log.e("[Telecom Connection Service] Failed to find connection for call id: $callId")
            return
        }

        TelecomHelper.get().connections.remove(connection)
        connection.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
        connection.destroy()
    }

    private fun onCallEnded(call: Call) {
        val callId = call.callLog.callId
        val connection = TelecomHelper.get().findConnectionForCallId(callId.orEmpty())
        if (connection == null) {
            Log.e("[Telecom Connection Service] Failed to find connection for call id: $callId")
            return
        }

        TelecomHelper.get().connections.remove(connection)
        val reason = call.reason
        Log.i("[Telecom Connection Service] Call [$callId] ended with reason: $reason, destroying connection")
        connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        connection.destroy()
    }

    private fun onCallConnected(call: Call) {
        val callId = call.callLog.callId
        val connection = TelecomHelper.get().findConnectionForCallId(callId.orEmpty())
        if (connection == null) {
            Log.e("[Telecom Connection Service] Retrying failed to find connection for call id: $callId")
            // CLB: Added post delayed to try again if at first the connection wasn't found.
            Handler().postDelayed(
                {
                    val connection = TelecomHelper.get().findConnectionForCallId(callId.orEmpty())
                    if (connection == null) {
                        Log.e("[Telecom Connection Service] Failed to find connection for call id: $callId")
                        return@postDelayed
                    }
                    Log.i("[Telecom Connection Service] found connection for call id delayed: $callId")

                    if (connection.state != Connection.STATE_HOLDING) {
                        connection.setActive()
                    }
                },
                500
            )
            return
        }

        if (connection.state != Connection.STATE_HOLDING) {
            connection.setActive()
        }
    }
}

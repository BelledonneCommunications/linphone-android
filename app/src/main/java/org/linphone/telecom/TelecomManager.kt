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

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class TelecomManager @WorkerThread constructor(context: Context) {
    companion object {
        private const val TAG = "[Telecom Manager]"
    }

    private val callsManager = CallsManager(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val map = HashMap<String, TelecomCallControlCallback>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallCreated(core: Core, call: Call) {
            Log.i("$TAG Call created: $call")

            val address = call.remoteAddress
            val friend = coreContext.contactsManager.findContactByAddress(address)
            val displayName = friend?.name ?: LinphoneUtils.getDisplayName(address)
            val uri = Uri.parse(address.asStringUriOnly())
            val direction = if (call.dir == Call.Dir.Outgoing) {
                CallAttributesCompat.DIRECTION_OUTGOING
            } else {
                CallAttributesCompat.DIRECTION_INCOMING
            }
            val type = CallAttributesCompat.CALL_TYPE_AUDIO_CALL or CallAttributesCompat.CALL_TYPE_VIDEO_CALL
            val capabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE or CallAttributesCompat.SUPPORTS_TRANSFER

            val callAttributes = CallAttributesCompat(
                displayName,
                uri,
                direction,
                type,
                capabilities
            )
            scope.launch {
                callsManager.addCall(callAttributes) {
                    val callbacks = TelecomCallControlCallback(call, this, scope)

                    coreContext.postOnCoreThread {
                        val callId = call.callLog.callId.orEmpty()
                        if (callId.isNotEmpty()) {
                            Log.i("$TAG Storing our callbacks for call ID [$callId]")
                            map[callId] = callbacks
                        }
                    }

                    setCallback(callbacks)
                    // We must first call setCallback on callControlScope before using it
                    callbacks.onCallControlCallbackSet()
                }
            }
        }
    }

    init {
        callsManager.registerAppWithTelecom(
            CallsManager.Companion.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )
        Log.i("$TAG App has been registered with Telecom")
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        Log.i("$TAG Core has been started")
        core.addListener(coreListener)
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        Log.i("$TAG Core is being stopped")
        core.removeListener(coreListener)
    }

    @WorkerThread
    fun applyAudioRouteToCallWithId(routes: List<AudioDevice.Type>, callId: String): Boolean {
        Log.i(
            "$TAG Looking for audio endpoint with type [${routes.first()}] for call with ID [$callId]"
        )
        val callControlCallback = map[callId]
        if (callControlCallback == null) {
            Log.w("$TAG Failed to find callbacks for call with ID [$callId]")
            return false
        }

        callControlCallback.applyAudioRouteToCallWithId(routes)
        return true
    }
}

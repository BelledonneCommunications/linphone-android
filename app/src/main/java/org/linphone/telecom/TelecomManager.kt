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
import androidx.annotation.WorkerThread
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallException
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
import androidx.core.net.toUri

class TelecomManager
    @WorkerThread
    constructor(context: Context) {
    companion object {
        private const val TAG = "[Telecom Manager]"
    }

    private val callsManager = CallsManager(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val map = HashMap<String, TelecomCallControlCallback>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (state == Call.State.IncomingReceived || state == Call.State.OutgoingProgress) {
                onCallCreated(call)
            }
        }

        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            currentlyFollowedCalls = 0
        }
    }

    private var currentlyFollowedCalls: Int = 0

    init {
        val hasTelecomFeature =
            context.packageManager.hasSystemFeature("android.software.telecom")
        Log.i(
            "$TAG android.software.telecom feature is [${if (hasTelecomFeature) "available" else "not available"}]"
        )

        try {
            callsManager.registerAppWithTelecom(
                CallsManager.CAPABILITY_BASELINE or
                    CallsManager.Companion.CAPABILITY_SUPPORTS_VIDEO_CALLING
            )
            Log.i("$TAG App has been registered with Telecom")
        } catch (e: Exception) {
            Log.e("$TAG Can't init TelecomManager: $e")
        }
    }

    @WorkerThread
    fun getCurrentlyFollowedCalls(): Int {
        return currentlyFollowedCalls
    }

    @WorkerThread
    fun onCallCreated(call: Call) {
        Log.i("$TAG Call to [${call.remoteAddress.asStringUriOnly()}] created in state [${call.state}]")

        val address = call.callLog.remoteAddress
        val uri = address.asStringUriOnly().toUri()

        val direction = if (call.dir == Call.Dir.Outgoing) {
            CallAttributesCompat.DIRECTION_OUTGOING
        } else {
            CallAttributesCompat.DIRECTION_INCOMING
        }

        val capabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE or CallAttributesCompat.SUPPORTS_TRANSFER

        val conferenceInfo = LinphoneUtils.getConferenceInfoIfAny(call)
        val displayName = if (call.conference != null || conferenceInfo != null) {
            conferenceInfo?.subject ?: call.conference?.subject ?: LinphoneUtils.getDisplayName(address)
        } else {
            val friend = coreContext.contactsManager.findContactByAddress(address)
            friend?.name ?: LinphoneUtils.getDisplayName(address)
        }

        val isVideo = LinphoneUtils.isVideoEnabled(call)
        val type = if (isVideo) {
            CallAttributesCompat.Companion.CALL_TYPE_VIDEO_CALL
        } else {
            CallAttributesCompat.Companion.CALL_TYPE_AUDIO_CALL
        }

        scope.launch {
            try {
                val callAttributes = CallAttributesCompat(
                    displayName,
                    uri,
                    direction,
                    type,
                    capabilities
                )
                Log.i("$TAG Adding call to Telecom's CallsManager with attributes [$callAttributes]")

                callsManager.addCall(
                    callAttributes,
                    { callType -> // onAnswer
                        Log.i("$TAG We're asked to answer the call with type [$callType]")
                        coreContext.postOnCoreThread {
                            if (LinphoneUtils.isCallIncoming(call.state)) {
                                Log.i("$TAG Answering call")
                                coreContext.answerCall(call)
                            }
                        }
                    },
                    { disconnectCause -> // onDisconnect
                        Log.i(
                            "$TAG We're asked to terminate the call with reason [$disconnectCause]"
                        )
                        coreContext.postOnCoreThread {
                            coreContext.terminateCall(call)
                        }
                        currentlyFollowedCalls -= 1
                    },
                    { // onSetActive
                        Log.i("$TAG We're asked to resume the call")
                        coreContext.postOnCoreThread {
                            Log.i("$TAG Resuming call")
                            call.resume()
                        }
                    },
                    { // onSetInactive
                        Log.i("$TAG We're asked to pause the call")
                        coreContext.postOnCoreThread {
                            Log.i("$TAG Pausing call")
                            call.pause()
                        }
                    }
                ) {
                    val callbacks = TelecomCallControlCallback(call, this, scope)

                    // We must first call setCallback on callControlScope before using it
                    callbacks.onCallControlCallbackSet()
                    currentlyFollowedCalls += 1
                    Log.i("$TAG Call added to Telecom's CallsManager")

                    coreContext.postOnCoreThread {
                        val callId = call.callLog.callId.orEmpty()
                        if (callId.isNotEmpty()) {
                            Log.i("$TAG Storing our callbacks for call ID [$callId]")
                            map[callId] = callbacks
                        }
                    }
                }
            } catch (ce: CallException) {
                Log.e("$TAG Failed to add call to Telecom's CallsManager: $ce")
            } catch (se: SecurityException) {
                Log.e("$TAG Security exception trying to add call to Telecom's CallsManager: $se")
            } catch (ise: IllegalArgumentException) {
                Log.e("$TAG Illegal argument exception trying to add call to Telecom's CallsManager: $ise")
            } catch (e: Exception) {
                Log.e("$TAG Exception trying to add call to Telecom's CallsManager: $e")
            }
        }
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

        return callControlCallback.applyAudioRouteToCallWithId(routes)
    }
}

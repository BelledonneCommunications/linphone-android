/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.integration.calls

import android.content.Context
import android.graphics.drawable.Icon
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.StatusHints
import android.telecom.TelecomManager
import androidx.annotation.AnyThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.Reason
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.utils.AudioUtils
import org.linphone.utils.LinphoneUtils

/**
 * One Linphone call as a managed Telecom call: the system phone app shows it, rings for it, logs it and controls
 * it; this class carries everything between the two.
 *
 * Telecom calls the on*() callbacks on the main thread; Linphone's API is used on its core thread only
 * (coreContext.postOnCoreThread). The Connection setters may be called from either.
 */
class ManagedConnection(
    private val context: Context,
    val handle: PhoneAccountHandle,
    val incoming: Boolean
) : Connection() {
    companion object {
        private const val TAG = "[Managed Connection]"
    }

    /** the Linphone call, once bound (core thread) */
    @Volatile var call: Call? = null
        private set

    @Volatile private var ended = false

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            onLinphoneState(call, state)
        }
    }

    init {
        setAudioModeIsVoip(true)
        var caps = CAPABILITY_HOLD or CAPABILITY_SUPPORT_HOLD or CAPABILITY_MUTE
        if (IntegrationPrefs.rejectWithMessage) caps = caps or CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION
        if (IntegrationPrefs.allowDeflect && incoming) caps = caps or CAPABILITY_SUPPORT_DEFLECT
        // public from Android 17 (API 37); before, Telecom offers no transfer for self-provided calls
        if (IntegrationPrefs.allowTransfer && Build.VERSION.SDK_INT >= 37) {
            caps = caps or CAPABILITY_TRANSFER or CAPABILITY_TRANSFER_CONSULTATIVE
        }
        connectionCapabilities = caps
    }

    // ---- binding -----------------------------------------------------------------------------------------------

    /** attach the Linphone call: from here on its state drives this Connection */
    @WorkerThread
    fun bind(call: Call) {
        if (this.call === call) return
        this.call = call
        ManagedCalls.register(call, this)
        call.addListener(callListener)

        val remote = call.remoteAddress
        setAddress(telecomUri(remote), TelecomManager.PRESENTATION_ALLOWED)
        val friend = coreContext.contactsManager.findContactByAddress(remote)
        val name = friend?.name?.takeIf { it.isNotBlank() } ?: LinphoneUtils.getDisplayName(remote)
        setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)

        if (IntegrationPrefs.showStatusHints) {
            val account = ManagedAccounts.accountOf(coreContext.core, handle)
            val label = account?.let { ManagedAccounts.label(context, it) } ?: context.getString(R.string.app_name)
            statusHints = StatusHints(
                context.getString(R.string.integration_call_via, label),
                Icon.createWithResource(context, R.drawable.linphone_notification),
                null
            )
        }
        Log.i("$TAG Bound to call [${call.callLog.callId}] in state [${call.state}]")
        // the state it is in already (an outgoing call may have moved on before it was bound)
        onLinphoneState(call, call.state)
    }

    /** the address the system shows and matches against contacts: tel: for phone numbers, the SIP URI otherwise */
    @WorkerThread
    private fun telecomUri(address: Address): Uri {
        val user = address.username.orEmpty()
        val number = user.removePrefix("+")
        if (user.isNotEmpty() && number.isNotEmpty() && number.isDigitsOnly()) {
            return Uri.fromParts("tel", user, null)
        }
        return LinphoneUtils.getAddressAsCleanStringUriOnly(address).toUri()
    }

    // ---- Linphone -> Telecom ---------------------------------------------------------------------------------

    @WorkerThread
    private fun onLinphoneState(call: Call, state: Call.State) {
        if (ended) return
        when (state) {
            Call.State.OutgoingInit, Call.State.OutgoingProgress -> setDialing()
            // 180 without media: the system plays the ringback; with early media (183): the network's own audio
            Call.State.OutgoingRinging -> setRingbackRequested(true)
            Call.State.OutgoingEarlyMedia -> setRingbackRequested(false)
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> setRinging()
            Call.State.Connected, Call.State.StreamsRunning, Call.State.UpdatedByRemote -> {
                setRingbackRequested(false)
                setActive()
                updateProperties(call)
            }
            Call.State.Paused -> setOnHold()
            Call.State.End, Call.State.Error -> end(call)
            Call.State.Released -> end(call)
            else -> {}
        }
    }

    /** HD voice (a wideband codec) and Wi-Fi: the badges of the system call screen */
    @WorkerThread
    private fun updateProperties(call: Call) {
        // HD and Wi-Fi properties are public from Android 11 (API 30)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        var props = 0
        if (IntegrationPrefs.reportHdAudio) {
            val clockRate = call.currentParams.usedAudioPayloadType?.clockRate ?: 0
            if (clockRate >= 16000) props = props or PROPERTY_HIGH_DEF_AUDIO
        }
        if (IntegrationPrefs.reportWifi && onWifi()) props = props or PROPERTY_WIFI
        if (props != connectionProperties) connectionProperties = props
    }

    @AnyThread
    private fun onWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @WorkerThread
    private fun end(call: Call) {
        if (ended) return
        ended = true
        call.removeListener(callListener)
        ManagedCalls.unregister(call)
        val cause = disconnectCause(call)
        Log.i("$TAG Call [${call.callLog.callId}] ended (reason [${call.reason}], SIP ${call.errorInfo.protocolCode}): $cause")
        setDisconnected(cause)
        destroy()
    }

    /** ended without a Linphone call (it could not be started, or was gone already) */
    @AnyThread
    fun fail(code: Int, label: CharSequence? = null) {
        if (ended) return
        ended = true
        setDisconnected(DisconnectCause(code, label, label, null))
        destroy()
    }

    /**
     * What the system shows (and plays) when the call ends. Managed calls may use every cause; the SIP status
     * gives the label (busy, unavailable, not found, declined, ...).
     */
    @WorkerThread
    private fun disconnectCause(call: Call): DisconnectCause {
        val log = call.callLog
        val sip = call.errorInfo.protocolCode
        val phrase = call.errorInfo.phrase
        val reason = call.reason

        fun cause(code: Int, label: Int? = null, tone: Int? = null): DisconnectCause {
            val text = label?.let { context.getString(it) }
            val why = "${reason.name} $sip"
            // no tone given: the system's default for the code
            return if (tone == null) {
                DisconnectCause(code, text, text ?: phrase, why)
            } else {
                DisconnectCause(code, text, text ?: phrase, why, tone)
            }
        }
        if (incoming) {
            return when {
                // answered or declined on another device of the same account
                log.status == Call.Status.AcceptedElsewhere -> cause(DisconnectCause.ANSWERED_ELSEWHERE)
                log.status == Call.Status.DeclinedElsewhere -> cause(DisconnectCause.REJECTED)
                log.status == Call.Status.Missed || (reason == Reason.NotAnswered && !wasAnswered(call)) ->
                    cause(DisconnectCause.MISSED)
                log.status == Call.Status.Declined || reason == Reason.Declined || reason == Reason.Busy ->
                    cause(DisconnectCause.REJECTED)
                call.state == Call.State.Error -> cause(DisconnectCause.ERROR, R.string.integration_end_error)
                else -> cause(if (endedLocally) DisconnectCause.LOCAL else DisconnectCause.REMOTE)
            }
        }
        return when {
            endedLocally && !failedRemotely(sip) -> cause(DisconnectCause.LOCAL)
            sip == 486 || sip == 600 || reason == Reason.Busy ->
                cause(DisconnectCause.BUSY, R.string.integration_end_busy, ToneGenerator.TONE_SUP_BUSY)
            sip == 603 || reason == Reason.Declined ->
                cause(DisconnectCause.REMOTE, R.string.integration_end_declined, ToneGenerator.TONE_SUP_BUSY)
            sip == 404 || sip == 484 || sip == 604 || reason == Reason.NotFound || reason == Reason.AddressIncomplete ->
                cause(DisconnectCause.ERROR, R.string.integration_end_not_found, ToneGenerator.TONE_SUP_ERROR)
            sip == 480 || sip == 408 || reason == Reason.TemporarilyUnavailable || reason == Reason.NotAnswered ->
                cause(DisconnectCause.REMOTE, R.string.integration_end_unavailable, ToneGenerator.TONE_SUP_CONGESTION)
            sip == 401 || sip == 403 || sip == 407 || reason == Reason.Forbidden || reason == Reason.Unauthorized ->
                cause(DisconnectCause.ERROR, R.string.integration_end_forbidden, ToneGenerator.TONE_SUP_ERROR)
            sip in 500..599 || reason == Reason.IOError || reason == Reason.ServerTimeout ->
                cause(DisconnectCause.ERROR, R.string.integration_end_network, ToneGenerator.TONE_SUP_CONGESTION)
            call.state == Call.State.Error -> cause(DisconnectCause.ERROR, R.string.integration_end_error, ToneGenerator.TONE_SUP_ERROR)
            else -> cause(DisconnectCause.REMOTE)
        }
    }

    /** this side hung up (Telecom asked, or Linphone's own UI) */
    @Volatile private var endedLocally = false

    private fun failedRemotely(sip: Int) = sip in 400..699

    private fun wasAnswered(call: Call) = call.callLog.duration > 0

    // ---- Telecom -> Linphone ---------------------------------------------------------------------------------

    private fun onCall(name: String, block: (Call) -> Unit) {
        Log.i("$TAG Telecom asks: $name")
        coreContext.postOnCoreThread {
            val c = call
            if (c == null) {
                Log.w("$TAG [$name]: no call bound")
            } else {
                block(c)
            }
        }
    }

    override fun onAnswer() = onAnswer(0)

    override fun onAnswer(videoState: Int) = onCall("answer") { call ->
        if (!LinphoneUtils.isCallIncoming(call.state)) return@onCall
        // the system call screen shows no video: the call is answered as an audio call
        val params = coreContext.core.createCallParams(call)
        params?.isVideoEnabled = false
        if (params != null) call.acceptWithParams(params) else call.accept()
    }

    override fun onReject() = onCall("reject") { call ->
        endedLocally = true
        call.decline(if (IntegrationPrefs.rejectAsBusy) Reason.Busy else Reason.Declined)
    }

    override fun onReject(rejectReason: Int) = onCall("reject ($rejectReason)") { call ->
        endedLocally = true
        // REJECT_REASON_UNWANTED (a spam call): declined for good; otherwise as the user chose in the settings
        val reason = when {
            rejectReason == android.telecom.Call.REJECT_REASON_UNWANTED -> Reason.Declined
            IntegrationPrefs.rejectAsBusy -> Reason.Busy
            else -> Reason.Declined
        }
        call.decline(reason)
    }

    override fun onReject(replyMessage: String?) = onCall("reject with a message") { call ->
        endedLocally = true
        if (!replyMessage.isNullOrBlank()) ManagedCalls.sendReply(call, replyMessage)
        call.decline(if (IntegrationPrefs.rejectAsBusy) Reason.Busy else Reason.Declined)
    }

    override fun onDisconnect() = onCall("hang up") { call ->
        endedLocally = true
        call.terminate()
    }

    override fun onAbort() = onCall("abort") { call ->
        endedLocally = true
        call.terminate()
    }

    override fun onHold() = onCall("hold") { call -> call.pause() }

    override fun onUnhold() = onCall("unhold") { call -> call.resume() }

    override fun onPlayDtmfTone(c: Char) = onCall("DTMF") { call -> call.sendDtmf(c) }

    override fun onSilence() = onCall("silence") { _ -> coreContext.core.stopRinging() }

    override fun onDeflect(address: Uri) = onCall("deflect") { call ->
        val target = ManagedCalls.addressFor(handle, address) ?: return@onCall
        endedLocally = true
        Log.i("$TAG Deflecting to [${target.asStringUriOnly()}]")
        call.redirectTo(target)
    }

    override fun onTransfer(number: Uri, isConfirmationRequired: Boolean) = onCall("transfer") { call ->
        val target = ManagedCalls.addressFor(handle, number) ?: return@onCall
        Log.i("$TAG Blind transfer to [${target.asStringUriOnly()}]")
        call.transferTo(target)
    }

    override fun onTransfer(otherConnection: Connection) = onCall("consultative transfer") { call ->
        val other = (otherConnection as? ManagedConnection)?.call
        if (other == null) {
            Log.w("$TAG The other connection is not a Linphone call")
            return@onCall
        }
        call.transferToAnother(other)
    }

    override fun onMuteStateChanged(isMuted: Boolean) = onCall("mute $isMuted") { call ->
        call.microphoneMuted = isMuted
    }

    // the audio route the system picked: Linphone plays to the same device
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        if (!IntegrationPrefs.followSystemAudioRoute) return
        val types = when (callEndpoint.endpointType) {
            CallEndpoint.TYPE_EARPIECE -> listOf(AudioDevice.Type.Earpiece)
            CallEndpoint.TYPE_SPEAKER -> listOf(AudioDevice.Type.Speaker)
            CallEndpoint.TYPE_BLUETOOTH -> listOf(AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid)
            CallEndpoint.TYPE_WIRED_HEADSET -> listOf(AudioDevice.Type.Headset, AudioDevice.Type.Headphones)
            else -> return
        }
        onCall("audio route ${callEndpoint.endpointName}") { call ->
            AudioUtils.applyAudioRouteChangeInLinphone(call, types)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(state: CallAudioState) {
        // Android 14 and later report the route through onCallEndpointChanged
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!IntegrationPrefs.followSystemAudioRoute) return
        val types = when (state.route) {
            CallAudioState.ROUTE_EARPIECE -> listOf(AudioDevice.Type.Earpiece)
            CallAudioState.ROUTE_SPEAKER -> listOf(AudioDevice.Type.Speaker)
            CallAudioState.ROUTE_BLUETOOTH -> listOf(AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid)
            CallAudioState.ROUTE_WIRED_HEADSET -> listOf(AudioDevice.Type.Headset, AudioDevice.Type.Headphones)
            else -> return
        }
        onCall("audio route ${state.route}") { call ->
            AudioUtils.applyAudioRouteChangeInLinphone(call, types)
            call.microphoneMuted = state.isMuted
        }
    }
}

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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import java.util.concurrent.ConcurrentHashMap
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.utils.LinphoneUtils

/**
 * Managed calls: Linphone calls that run through the system phone app (see [ManagedConnection]).
 *
 *  - incoming: Linphone's IncomingReceived is handed to Telecom (addNewIncomingCall); Telecom creates the
 *    Connection through [org.linphone.telecom.TelecomConnectionService], which binds it by the SIP Call-ID
 *  - outgoing from the system dialer: TelecomConnectionService asks for a Connection, the Linphone call is started
 *    on the account of the chosen calling account
 *  - outgoing from Linphone's own UI: placed through Telecom (placeCall), so it runs in the system as well
 *
 * Which calls are managed: [ManagedAccounts.isManagedFor].
 */
object ManagedCalls {
    private const val TAG = "[Managed Calls]"

    /** in the incoming call extras: the SIP Call-ID of the Linphone call the Connection is for */
    const val EXTRA_CALL_ID = "org.linphone.integration.extra.CALL_ID"

    /** in the outgoing call extras: the exact SIP address Linphone's UI wants to call */
    const val EXTRA_SIP_ADDRESS = "org.linphone.integration.extra.SIP_ADDRESS"

    /** Call-ID -> its Connection */
    private val connections = ConcurrentHashMap<String, ManagedConnection>()

    /** incoming calls handed to Telecom whose Connection has not been created yet */
    private val offered: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** managed calls that ended recently (their Released state comes after the Connection is gone) */
    private val ended: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** the Call-IDs of every live Linphone call (managed or not) */
    private val live: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** the Connection an outgoing call being started now belongs to (bound in its OutgoingInit) */
    @Volatile private var pendingOutgoing: ManagedConnection? = null

    private lateinit var appContext: Context

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            val id = call.callLog.callId.orEmpty()
            when (state) {
                Call.State.OutgoingInit -> {
                    live.add(id)
                    pendingOutgoing?.let { conn ->
                        pendingOutgoing = null
                        conn.bind(call)
                    }
                }
                Call.State.IncomingReceived -> live.add(id)
                Call.State.Released -> {
                    live.remove(id)
                    offered.remove(id)
                }
                else -> {}
            }
        }

        @WorkerThread
        override fun onAccountAdded(core: Core, account: Account) = refresh(core)

        @WorkerThread
        override fun onAccountRemoved(core: Core, account: Account) = refresh(core)

        @WorkerThread
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            if (state == RegistrationState.Ok || state == RegistrationState.Failed ||
                state == RegistrationState.Cleared || state == RegistrationState.None
            ) {
                refresh(core)
            }
        }
    }

    @WorkerThread
    fun start(context: Context, core: Core) {
        appContext = context.applicationContext
        core.addListener(coreListener)
        refresh(core)
    }

    @WorkerThread
    fun stop(core: Core) {
        core.removeListener(coreListener)
    }

    /** the calling accounts, and Linphone's own ringing (off while the system rings for managed calls) */
    @WorkerThread
    fun refresh(core: Core) {
        if (!::appContext.isInitialized) return
        ManagedAccounts.refresh(appContext, core)
        // off only when the system really rings: the calling account must be enabled (in the mode "always the
        // system phone app" a disabled one makes the system refuse the call, which then rings in Linphone)
        val account = LinphoneUtils.getDefaultAccount()
        val managed = account != null && IntegrationPrefs.callMode != IntegrationPrefs.CallMode.LINPHONE &&
            ManagedAccounts.isEnabled(appContext, ManagedAccounts.handleOf(appContext, account))
        if (core.callRingingDisabled() != managed) {
            Log.i("$TAG Linphone's own ringing ${if (managed) "off (the system rings)" else "on"}")
            core.disableCallRinging(managed)
        }
    }

    // ---- bookkeeping -------------------------------------------------------------------------------------------

    @AnyThread
    fun register(call: Call, conn: ManagedConnection) {
        val id = call.callLog.callId.orEmpty()
        offered.remove(id)
        connections[id] = conn
    }

    @AnyThread
    fun unregister(call: Call) {
        val id = call.callLog.callId.orEmpty()
        if (connections.remove(id) != null) {
            if (ended.size > 50) ended.clear()
            ended.add(id)
        }
    }

    @AnyThread
    fun wasManaged(callId: String?): Boolean = callId != null && (callId in ended || isManaged(callId))

    /** the call runs (or is about to run) in the system phone app: Linphone shows nothing for it */
    @AnyThread
    fun isManaged(call: Call): Boolean {
        val id = call.callLog.callId.orEmpty()
        if (connections.containsKey(id) || id in offered) return true
        // being started for a Connection right now (inviteAddressWithParams runs the OutgoingInit listeners
        // before it returns, in whatever order they were added)
        return pendingOutgoing != null && call.dir == Call.Dir.Outgoing && call.state == Call.State.OutgoingInit
    }

    @AnyThread
    fun isManaged(callId: String?): Boolean = callId != null && (connections.containsKey(callId) || callId in offered)

    /** every live call is managed (and there is one): Linphone's call screen has nothing to show */
    @AnyThread
    fun allCallsManaged(): Boolean {
        val ids = live.toList()
        return ids.isNotEmpty() && ids.all { isManaged(it) }
    }

    // ---- incoming -------------------------------------------------------------------------------------------

    /** whether this incoming call goes to the system phone app (the same answer wherever it is asked) */
    @WorkerThread
    fun shouldManage(call: Call): Boolean {
        if (!::appContext.isInitialized || call.dir != Call.Dir.Incoming) return false
        if (isManaged(call)) return true
        val account = accountOfCall(call) ?: return false
        if (!ManagedAccounts.isManagedFor(appContext, account)) return false
        if (IntegrationPrefs.videoCallsInLinphone && call.remoteParams?.isVideoEnabled == true) {
            Log.i("$TAG Incoming video call: Linphone's call screen")
            return false
        }
        return true
    }

    /** hand an incoming call to Telecom; false: it stays Linphone's (self-managed) */
    @WorkerThread
    fun offerIncoming(call: Call): Boolean {
        if (!shouldManage(call)) return false
        val id = call.callLog.callId.orEmpty()
        if (id in offered || connections.containsKey(id)) return true
        val account = accountOfCall(call) ?: return false
        val handle = ManagedAccounts.handleOf(appContext, account)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, telecomUriOf(call.remoteAddress))
            putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, Bundle().apply { putString(EXTRA_CALL_ID, id) })
        }
        offered.add(id)
        return try {
            appContext.getSystemService(TelecomManager::class.java).addNewIncomingCall(handle, extras)
            Log.i("$TAG Incoming call [$id] handed to the system phone app")
            true
        } catch (e: Exception) {
            offered.remove(id)
            Log.e("$TAG The system refused the incoming call [$id]: $e")
            false
        }
    }

    @MainThread
    fun createIncoming(context: Context, request: ConnectionRequest): Connection {
        val conn = ManagedConnection(context.applicationContext, request.accountHandle, true)
        val id = request.extras?.getString(EXTRA_CALL_ID)
        conn.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        conn.setRinging()
        coreContext.postOnCoreThread { core ->
            val call = id?.let { core.getCallByCallid(it) }
            if (call == null || !LinphoneUtils.isCallIncoming(call.state)) {
                Log.w("$TAG Incoming call [$id] is gone already")
                offered.remove(id)
                conn.fail(DisconnectCause.MISSED)
            } else {
                conn.bind(call)
            }
        }
        return conn
    }

    /** the system did not take the call (another call it can't hold, a policy): Linphone shows it itself */
    @MainThread
    fun incomingFailed(request: ConnectionRequest?) {
        val id = request?.extras?.getString(EXTRA_CALL_ID) ?: return
        Log.w("$TAG The system phone app could not take incoming call [$id]: Linphone's own call screen instead")
        coreContext.postOnCoreThread { core ->
            offered.remove(id)
            val call = core.getCallByCallid(id) ?: return@postOnCoreThread
            if (LinphoneUtils.isCallIncoming(call.state)) {
                coreContext.telecomManager.onCallCreated(call)
                coreContext.notificationsManager.showIncomingCallNotificationFallback(call)
            }
        }
    }

    // ---- outgoing ---------------------------------------------------------------------------------------------

    /** the system dialer (or a placeCall of ours) wants a call on one of the calling accounts */
    @MainThread
    fun createOutgoing(context: Context, request: ConnectionRequest): Connection {
        val conn = ManagedConnection(context.applicationContext, request.accountHandle, false)
        conn.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        conn.setDialing()
        val exact = request.extras?.getString(EXTRA_SIP_ADDRESS)
        val wantsVideo = VideoProfile.isVideo(request.videoState)
        coreContext.postOnCoreThread { core ->
            startOutgoing(core, conn, request.address, exact, wantsVideo)
        }
        return conn
    }

    @WorkerThread
    private fun startOutgoing(core: Core, conn: ManagedConnection, uri: Uri?, exact: String?, wantsVideo: Boolean) {
        val account = ManagedAccounts.accountOf(core, conn.handle)
        if (account == null) {
            conn.fail(DisconnectCause.ERROR, appContext.getString(R.string.integration_end_no_account))
            return
        }
        val target = exact?.let { core.interpretUrl(it, false) } ?: uri?.let { addressFor(conn.handle, it) }
        if (target == null) {
            conn.fail(DisconnectCause.ERROR, appContext.getString(R.string.integration_end_not_found))
            return
        }
        if (!core.isNetworkReachable) {
            conn.fail(DisconnectCause.ERROR, appContext.getString(R.string.integration_end_network))
            return
        }
        val params = core.createCallParams(null)
        if (params == null) {
            conn.fail(DisconnectCause.ERROR, appContext.getString(R.string.integration_end_error))
            return
        }
        params.account = account
        // the system call screen shows no video (a video call from the dialer is placed as an audio call)
        params.isVideoEnabled = false
        if (wantsVideo) Log.i("$TAG Video was asked for: placed as an audio call (the system call screen has no video)")
        params.recordFile = LinphoneUtils.getRecordingFilePathForAddress(target)
        Log.i("$TAG Calling [${target.asStringUriOnly()}] from [${account.params.identityAddress?.asStringUriOnly()}]")
        // Telecom has held the active call already (it does so for managed calls before placing a new one)
        pendingOutgoing = conn
        val call = core.inviteAddressWithParams(target, params)
        pendingOutgoing = null
        if (call == null) {
            conn.fail(DisconnectCause.ERROR, appContext.getString(R.string.integration_end_error))
        } else {
            conn.bind(call)
        }
    }

    @MainThread
    fun outgoingFailed(request: ConnectionRequest?) {
        Log.w("$TAG The system could not place the call to [${request?.address}]")
    }

    /**
     * A call Linphone's own UI starts: placed through the system when its account is managed. False: Linphone
     * starts it itself (self-managed), as upstream does.
     */
    @WorkerThread
    fun placeThroughSystem(address: Address, account: Account?, video: Boolean): Boolean {
        if (!::appContext.isInitialized || !IntegrationPrefs.placeAppCallsThroughSystem) return false
        val acc = account ?: LinphoneUtils.getDefaultAccount() ?: return false
        if (!ManagedAccounts.isManagedFor(appContext, acc)) return false
        if (video && IntegrationPrefs.videoCallsInLinphone) return false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("$TAG No CALL_PHONE permission: Linphone places the call itself")
            return false
        }
        val handle = ManagedAccounts.handleOf(appContext, acc)
        if (!ManagedAccounts.isEnabled(appContext, handle)) {
            Log.w("$TAG Calling account [${handle.id}] is not enabled: Linphone places the call itself")
            return false
        }
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            putBundle(
                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                Bundle().apply {
                putString(EXTRA_SIP_ADDRESS, address.asStringUriOnly())
            }
            )
        }
        return try {
            appContext.getSystemService(TelecomManager::class.java).placeCall(telecomUriOf(address), extras)
            Log.i("$TAG Call to [${address.asStringUriOnly()}] placed through the system phone app")
            true
        } catch (e: SecurityException) {
            Log.e("$TAG The system refused to place the call: $e")
            false
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    @WorkerThread
    private fun accountOfCall(call: Call): Account? {
        return call.params.account
            ?: LinphoneUtils.getAccountForAddress(call.toAddress)
            ?: LinphoneUtils.getDefaultAccount()
    }

    /** tel: for a phone number (the system matches it against contacts), the SIP URI otherwise */
    @WorkerThread
    fun telecomUriOf(address: Address): Uri {
        val user = address.username.orEmpty()
        val digits = user.removePrefix("+")
        return if (digits.isNotEmpty() && digits.isDigitsOnly()) {
            Uri.fromParts("tel", user, null)
        } else {
            LinphoneUtils.getAddressAsCleanStringUriOnly(address).toUri()
        }
    }

    /** the SIP address of a tel: or sip: URI on the calling account's SIP account */
    @WorkerThread
    fun addressFor(handle: PhoneAccountHandle, uri: Uri): Address? {
        val core = coreContext.core
        val account = ManagedAccounts.accountOf(core, handle) ?: LinphoneUtils.getDefaultAccount()
        return when (uri.scheme) {
            "tel" -> {
                val number = uri.schemeSpecificPart.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                if (number.isEmpty()) return null
                val normalized = account?.normalizePhoneNumber(number) ?: number
                account?.normalizeSipUri(normalized) ?: core.interpretUrl(normalized, false)
            }
            else -> core.interpretUrl(uri.toString(), false)
        }
    }

    /** a quick reply chosen in the system's reject screen: to the caller as a chat message */
    @WorkerThread
    fun sendReply(call: Call, text: String) {
        val core = coreContext.core
        val remote = call.remoteAddress
        val params = LinphoneUtils.getChatRoomParamsForOneToOneConversationWithAddress(remote)
        if (params == null) {
            Log.w("$TAG No chat possible: the reply is not sent")
            return
        }
        val local = accountOfCall(call)?.params?.identityAddress
        val room = core.searchChatRoom(params, local, null, arrayOf(remote))
            ?: core.createChatRoom(params, arrayOf(remote))
        if (room == null) {
            Log.w("$TAG Could not open a conversation with the caller")
            return
        }
        room.createMessageFromUtf8(text).send()
        Log.i("$TAG Reply sent to the caller")
    }
}

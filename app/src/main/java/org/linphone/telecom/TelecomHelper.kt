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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.TelecomManager.*
import kotlin.Exception
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PermissionHelper
import org.linphone.utils.SingletonHolder

@TargetApi(29)
class TelecomHelper private constructor(context: Context) {
    companion object : SingletonHolder<TelecomHelper, Context>(::TelecomHelper)

    private val telecomManager: TelecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    private var account: PhoneAccount = initPhoneAccount(context)

    val connections = arrayListOf<NativeCallWrapper>()

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("[Telecom Helper] Call state changed: ${call.state}")

            try {
                if (call.dir == Call.Dir.Incoming && call.state == Call.State.IncomingReceived) {
                    onIncomingCall(call)
                } else if (call.dir == Call.Dir.Outgoing && call.state == Call.State.OutgoingProgress) {
                    onOutgoingCall(call)
                }
            } catch (se: SecurityException) {
                Log.e("[Telecom Helper] Exception while trying to place call: $se")
            }
        }
    }

    init {
        coreContext.core.addListener(listener)
        Log.i("[Telecom Helper] Created")
    }

    fun destroy() {
        coreContext.core.removeListener(listener)
        Log.i("[Telecom Helper] Destroyed")
    }

    fun isIncomingCallPermitted(): Boolean {
        val incomingCallPermitted = telecomManager.isIncomingCallPermitted(account.accountHandle)
        Log.i("[Telecom Helper] Is incoming call permitted? $incomingCallPermitted")
        return incomingCallPermitted
    }

    @SuppressLint("MissingPermission")
    fun isInManagedCall(): Boolean {
        // Don't use telecomManager.isInCall as our own self-managed calls will be considered!
        val isInManagedCall = telecomManager.isInManagedCall
        Log.i("[Telecom Helper] Is in managed call? $isInManagedCall")
        return isInManagedCall
    }

    fun isAccountEnabled(): Boolean {
        val enabled = account.isEnabled
        Log.i("[Telecom Helper] Is account enabled ? $enabled")
        return enabled
    }

    @SuppressLint("MissingPermission")
    fun findExistingAccount(context: Context): PhoneAccount? {
        if (PermissionHelper.required(context).hasReadPhoneStateOrPhoneNumbersPermission()) {
            var account: PhoneAccount? = null
            val phoneAccountHandleList: List<PhoneAccountHandle> =
                telecomManager.selfManagedPhoneAccounts
            val connectionService = ComponentName(context, TelecomConnectionService::class.java)
            for (phoneAccountHandle in phoneAccountHandleList) {
                val phoneAccount: PhoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle)
                if (phoneAccountHandle.componentName == connectionService) {
                    Log.i("[Telecom Helper] Found existing phone account: $phoneAccount")
                    account = phoneAccount
                    break
                }
            }
            if (account == null) {
                Log.w("[Telecom Helper] Existing phone account not found")
            }
            return account
        } else {
            Log.e("[Telecom Helper] Can't search for existing phone account, missing permission(s)")
        }
        return null
    }

    fun updateAccount(newAccount: PhoneAccount?) {
        if (newAccount != null) {
            Log.i("[Telecom Helper] Updating account object: $newAccount")
            account = newAccount
        }
    }

    fun removeAccount() {
        if (account.isEnabled) {
            Log.w("[Telecom Helper] Unregistering phone account handler from telecom manager")
            telecomManager.unregisterPhoneAccount(account.accountHandle)
        } else {
            Log.w("[Telecom Helper] Account wasn't enabled, skipping...")
        }
    }

    fun findConnectionForCallId(callId: String): NativeCallWrapper? {
        return connections.find { connection ->
            connection.callId == callId
        }
    }

    private fun initPhoneAccount(context: Context): PhoneAccount {
        val account: PhoneAccount? = findExistingAccount(context)
        if (account == null) {
            Log.i("[Telecom Helper] Phone account not found, let's create it")
            return createAccount(context)
        }
        return account
    }

    private fun createAccount(context: Context): PhoneAccount {
        val accountHandle = PhoneAccountHandle(
            ComponentName(context, TelecomConnectionService::class.java),
            context.packageName
        )
        // Take care that identity may be parsed, otherwise Android OS may crash during startup
        // and user will have to do a factory reset...
        val identity = coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
            ?: coreContext.core.createPrimaryContactParsed()?.asStringUriOnly()
            ?: "sip:linphone.android@sip.linphone.org"

        val address = Uri.parse(identity)
            ?: throw Exception("[Telecom Helper] Identity address for phone account is null!")
        val account = PhoneAccount.builder(accountHandle, context.getString(R.string.app_name))
            .setAddress(address)
            .setIcon(Icon.createWithResource(context, R.drawable.linphone_logo_tinted))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .setHighlightColor(context.getColor(R.color.primary_color))
            .setShortDescription(context.getString(R.string.app_description))
            .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP))
            .build()

        try {
            telecomManager.registerPhoneAccount(account)
            Log.i("[Telecom Helper] Phone account created: $account")
        } catch (uoe: UnsupportedOperationException) {
            Log.e("[Telecom Helper] Unsupported Operation Exception: $uoe")
        } catch (e: Exception) {
            Log.e("[Telecom Helper] Exception: $e")
        }
        return account
    }

    private fun onIncomingCall(call: Call) {
        Log.i("[Telecom Helper] Incoming call received from ${call.remoteAddress.asStringUriOnly()}")

        val extras = prepareBundle(call)
        telecomManager.addNewIncomingCall(
            account.accountHandle,
            Bundle().apply {
                putBundle(EXTRA_INCOMING_CALL_EXTRAS, extras)
                putParcelable(EXTRA_PHONE_ACCOUNT_HANDLE, account.accountHandle)
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun onOutgoingCall(call: Call) {
        Log.i("[Telecom Helper] Outgoing call started to ${call.remoteAddress.asStringUriOnly()}")

        val extras = prepareBundle(call)
        telecomManager.placeCall(
            Uri.parse(call.remoteAddress.asStringUriOnly()),
            Bundle().apply {
                putBundle(EXTRA_OUTGOING_CALL_EXTRAS, extras)
                putParcelable(EXTRA_PHONE_ACCOUNT_HANDLE, account.accountHandle)
            }
        )
    }

    private fun prepareBundle(call: Call): Bundle {
        val extras = Bundle()
        val address = call.remoteAddress

        if (call.dir == Call.Dir.Outgoing) {
            extras.putString(
                EXTRA_CALL_BACK_NUMBER,
                call.remoteAddress.asStringUriOnly()
            )
        } else {
            extras.putParcelable(EXTRA_INCOMING_CALL_ADDRESS, Uri.parse(address.asStringUriOnly()))
        }

        extras.putString("Call-ID", call.callLog.callId)

        val contact = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)
        extras.putString("DisplayName", displayName)

        return extras
    }
}

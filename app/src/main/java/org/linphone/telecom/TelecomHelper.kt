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
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.SingletonHolder

class TelecomHelper private constructor(private val context: Context) {
    companion object : SingletonHolder<TelecomHelper, Context>(::TelecomHelper)

    private val telecomManager: TelecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    private var account: PhoneAccount = initPhoneAccount(context)

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onFirstCallStarted(core: Core) {
            val call = core.calls.firstOrNull()
            call ?: return

            if (call.dir == Call.Dir.Incoming) {
                onIncomingCall(call)
            } else {
                onOutgoingCall(call)
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

    fun isAccountEnabled(): Boolean {
        val enabled = account.isEnabled
        Log.i("[Telecom Helper] Is account enabled ? $enabled")
        return enabled
    }

    @SuppressLint("MissingPermission")
    fun findExistingAccount(context: Context): PhoneAccount? {
        var account: PhoneAccount? = null
        val phoneAccountHandleList: List<PhoneAccountHandle> = telecomManager.callCapablePhoneAccounts
        val connectionService = ComponentName(context, TelecomConnectionService::class.java)
        for (phoneAccountHandle in phoneAccountHandleList) {
            val phoneAccount: PhoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle)
            if (phoneAccountHandle.componentName == connectionService) {
                Log.i("[Telecom Helper] Found existing phone account: $phoneAccount")
                account = phoneAccount
                break
            }
        }
        return account
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
            // TODO FIXME
            //  When unregistering phone account, it's not enough to register it again,
            //  we also must start the activity for the user to enable it
            telecomManager.unregisterPhoneAccount(account.accountHandle)
        } else {
            Log.w("[Telecom Helper] Account wasn't enabled, skipping...")
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
        val identity = coreContext.core.defaultProxyConfig?.identityAddress?.asStringUriOnly() ?: ""
        val account = PhoneAccount.builder(accountHandle, context.getString(R.string.app_name))
            .setAddress(Uri.parse(identity))
            .setIcon(Icon.createWithResource(context, R.drawable.linphone_logo_tinted))
            .setSubscriptionAddress(null)
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setHighlightColor(context.getColor(R.color.primary_color))
            .setShortDescription(context.getString(R.string.app_description))
            .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP))
            .build()

        telecomManager.registerPhoneAccount(account)
        Log.i("[Telecom Helper] Phone account created: $account")
        return account
    }

    private fun onIncomingCall(call: Call) {
        Log.i("[Telecom Helper] Incoming call received")

        val extras = prepareBundle(call)
        telecomManager.addNewIncomingCall(account.accountHandle, extras)
    }

    @SuppressLint("MissingPermission")
    private fun onOutgoingCall(call: Call) {
        Log.i("[Helper Manager] Outgoing call started")

        val extras = prepareBundle(call)
        telecomManager.placeCall(Uri.parse(call.remoteAddress.asStringUriOnly()), extras)
    }

    private fun prepareBundle(call: Call): Bundle {
        val extras = Bundle()
        val address = call.remoteAddress
        val bundleKey: String

        if (call.dir == Call.Dir.Outgoing) {
            bundleKey = TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS
            extras.putString(
                TelecomManager.EXTRA_CALL_BACK_NUMBER,
                call.callLog.fromAddress.asStringUriOnly()
            )
        } else {
            bundleKey = TelecomManager.EXTRA_INCOMING_CALL_EXTRAS
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.parse(address.asStringUriOnly()))
        }

        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account.accountHandle)
        extras.putString("Call-ID", call.callLog.callId)
        extras.putString("DisplayName", call.remoteAddress.displayName)

        val b = Bundle()
        extras.putBundle(bundleKey, b)

        return extras
    }
}

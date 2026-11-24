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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.telecom.TelecomConnectionService

/**
 * The system calling accounts ("managed" PhoneAccounts, CAPABILITY_CALL_PROVIDER): one per SIP account, on
 * [TelecomConnectionService]. They show in the system dialer's "call with" choice, in Settings → Calls → Calling
 * accounts (where the user has to enable each once - Android does not let an app do it), and in the call log.
 *
 * An account stays registered while its SIP account exists: unregistering would make Android forget that the user
 * enabled it. Its description follows the SIP registration; Telecom keeps the enabled state when a handle is
 * registered again.
 */
object ManagedAccounts {
    private const val TAG = "[Managed Accounts]"

    /** handle ids of the managed accounts; upstream's connection manager account uses the bare package name */
    private const val ID_PREFIX = "managed:"

    /** what the app keeps for itself (not settings) */
    private const val STATE_SECTION = "android_integration_state"

    private fun telecom(context: Context) = context.getSystemService(TelecomManager::class.java)

    private fun component(context: Context) = ComponentName(context, TelecomConnectionService::class.java)

    @AnyThread
    fun idOf(account: Account): String {
        return account.params.identityAddress?.asStringUriOnly() ?: account.params.idkey.orEmpty()
    }

    @AnyThread
    fun handleOf(context: Context, account: Account): PhoneAccountHandle {
        return PhoneAccountHandle(component(context), ID_PREFIX + idOf(account))
    }

    @AnyThread
    fun isManagedHandle(context: Context, handle: PhoneAccountHandle?): Boolean {
        return handle != null && handle.componentName == component(context) && handle.id.startsWith(ID_PREFIX)
    }

    @WorkerThread
    fun accountOf(core: Core, handle: PhoneAccountHandle?): Account? {
        val id = handle?.id?.removePrefix(ID_PREFIX) ?: return null
        return core.accountList.firstOrNull { idOf(it) == id }
    }

    /** the system's view of the account: registered with Telecom and enabled by the user */
    @AnyThread
    fun isEnabled(context: Context, handle: PhoneAccountHandle): Boolean {
        return try {
            telecom(context).getPhoneAccount(handle)?.isEnabled == true
        } catch (e: SecurityException) {
            Log.w("$TAG Can't read phone account [${handle.id}]: $e")
            false
        }
    }

    /** whether a call on this SIP account runs through the system phone app (managed) */
    @WorkerThread
    fun isManagedFor(context: Context, account: Account?): Boolean {
        if (account == null) return false
        return when (IntegrationPrefs.callMode) {
            IntegrationPrefs.CallMode.LINPHONE -> false
            IntegrationPrefs.CallMode.SYSTEM -> true
            IntegrationPrefs.CallMode.AUTOMATIC -> isEnabled(context, handleOf(context, account))
        }
    }

    @AnyThread
    fun label(context: Context, account: Account): String {
        val custom = IntegrationPrefs.accountLabel
        if (custom.isNotBlank()) {
            return if (coreContext.core.accountList.size > 1) "$custom (${account.params.identityAddress?.username.orEmpty()})" else custom
        }
        val identity = account.params.identityAddress
        return identity?.displayName?.takeIf { it.isNotBlank() }
            ?: identity?.let { "${it.username}@${it.domain}" }
            ?: context.getString(R.string.app_name)
    }

    /** register (or update) every SIP account's calling account, and remove those of accounts that are gone */
    @WorkerThread
    fun refresh(context: Context, core: Core) {
        val tm = telecom(context) ?: return
        val wanted = HashSet<PhoneAccountHandle>()
        if (IntegrationPrefs.callMode != IntegrationPrefs.CallMode.LINPHONE) {
            for (account in core.accountList) {
                if (account.params.identityAddress == null) continue
                val handle = handleOf(context, account)
                wanted.add(handle)
                try {
                    tm.registerPhoneAccount(build(context, core, account, handle))
                } catch (e: Exception) {
                    Log.e("$TAG Failed to register phone account [${handle.id}]: $e")
                }
            }
        }
        // the handles registered before (Telecom has no public way to list an app's own accounts)
        val before = core.config.getString(STATE_SECTION, "registered_handles", "").orEmpty()
            .split("|").filter { it.isNotBlank() }
        for (id in before) {
            if (wanted.none { it.id == id }) {
                Log.i("$TAG Unregistering phone account [$id] (no such SIP account any more)")
                try {
                    tm.unregisterPhoneAccount(PhoneAccountHandle(component(context), id))
                } catch (e: Exception) {
                    Log.w("$TAG Failed to unregister phone account [$id]: $e")
                }
            }
        }
        core.config.setString(STATE_SECTION, "registered_handles", wanted.joinToString("|") { it.id })
        Log.i("$TAG ${wanted.size} calling account(s) registered")
    }

    @WorkerThread
    private fun build(context: Context, core: Core, account: Account, handle: PhoneAccountHandle): PhoneAccount {
        val identity = account.params.identityAddress!!
        var caps = PhoneAccount.CAPABILITY_CALL_PROVIDER
        if (core.isVideoEnabled) {
            caps = caps or PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or PhoneAccount.CAPABILITY_VIDEO_CALLING
        }
        val schemes = arrayListOf(PhoneAccount.SCHEME_SIP)
        if (IntegrationPrefs.acceptPhoneNumbers) schemes.add(0, PhoneAccount.SCHEME_TEL)
        val state = when (account.state) {
            RegistrationState.Ok -> context.getString(R.string.integration_account_registered)
            RegistrationState.Progress, RegistrationState.Refreshing ->
                context.getString(R.string.integration_account_registering)
            else -> context.getString(R.string.integration_account_not_registered)
        }
        val extras = Bundle().apply {
            putBoolean(PhoneAccount.EXTRA_ALWAYS_USE_VOIP_AUDIO_MODE, true)
        }
        return PhoneAccount.builder(handle, label(context, account))
            .setAddress(identity.asStringUriOnly().toUri())
            .setSubscriptionAddress(identity.asStringUriOnly().toUri())
            .setCapabilities(caps)
            .setIcon(Icon.createWithResource(context, R.drawable.linphone_notification))
            .setHighlightColor(context.getColor(R.color.main1_500))
            .setShortDescription("${identity.username}@${identity.domain} · $state")
            .setSupportedUriSchemes(schemes)
            .setExtras(extras)
            .build()
    }

    /** Settings → Calls → Calling accounts, where the user enables the accounts */
    @AnyThread
    fun enableIntent(): Intent {
        return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

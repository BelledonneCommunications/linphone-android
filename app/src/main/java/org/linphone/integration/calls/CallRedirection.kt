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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Account
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.integration.IntegrationPrefs.RedirectMode
import org.linphone.utils.LinphoneUtils

/**
 * Phone calls dialled in the system dialer, sent over a SIP account instead of the SIM (ROLE_CALL_REDIRECTION),
 * by the modes of [IntegrationPrefs.redirectMode]. Emergency calls never reach a redirection service.
 *
 * The target is the account's managed calling account when it is enabled (the call runs in the system phone app),
 * otherwise upstream's connection manager account (the call runs in Linphone's call screen).
 */
@RequiresApi(Build.VERSION_CODES.Q)
object CallRedirection {
    private const val TAG = "[Call Redirection]"

    /** the mode in force: upstream's own switch counts as "contacts with a SIP address" */
    @WorkerThread
    fun effectiveMode(): RedirectMode {
        val mode = IntegrationPrefs.redirectMode
        if (mode != RedirectMode.OFF) return mode
        return if (corePreferences.useCallRedirectionService) RedirectMode.SIP_CONTACTS else RedirectMode.OFF
    }

    /**
     * Decide about one call. [fallbackHandle] is upstream's connection manager account (it takes sip: only).
     * Returns false when the call is upstream's to handle (mode SIP_CONTACTS).
     */
    @WorkerThread
    fun handle(
        service: CallRedirectionService,
        handle: Uri,
        allowInteractiveResponse: Boolean,
        fallbackHandle: () -> PhoneAccountHandle?
    ): Boolean {
        val mode = effectiveMode()
        if (mode == RedirectMode.SIP_CONTACTS) return false
        if (mode == RedirectMode.OFF) {
            service.placeCallUnmodified()
            return true
        }
        val why = refusal(service, handle, mode)
        if (why != null) {
            Log.i("$TAG Not redirected: $why")
            service.placeCallUnmodified()
            return true
        }
        val core = coreContext.core
        val account = redirectAccount()!!
        val number = handle.schemeSpecificPart
        val confirm = mode == RedirectMode.ASK && allowInteractiveResponse
        val managed = ManagedAccounts.handleOf(service, account)
        if (ManagedAccounts.isManagedFor(service, account) && ManagedAccounts.isEnabled(service, managed)) {
            Log.i("$TAG Redirecting a phone call to calling account [${managed.id}]${if (confirm) ", the system asks first" else ""}")
            service.redirectCall(handle, managed, confirm)
            return true
        }
        val sip = account.normalizeSipUri(account.normalizePhoneNumber(number) ?: number)
            ?: core.interpretUrl(number, true)
        val fallback = fallbackHandle()
        if (sip == null || fallback == null) {
            Log.w("$TAG No SIP address for the number, or no account to redirect to")
            service.placeCallUnmodified()
            return true
        }
        Log.i("$TAG Redirecting a phone call to Linphone's call screen")
        service.redirectCall(Uri.parse(sip.asStringUriOnly()), fallback, confirm)
        return true
    }

    /** why this call stays on the SIM, or null when it is redirected */
    @WorkerThread
    private fun refusal(context: Context, handle: Uri, mode: RedirectMode): String? {
        if (handle.scheme != "tel") return "not a phone number (${handle.scheme})"
        val number = handle.schemeSpecificPart
        // service codes (*#06#, *100#, call forwarding codes) are the SIM's
        if (number.contains('*') || number.contains('#')) return "a service code"
        val digits = number.filter { it.isDigit() }
        if (digits.length < IntegrationPrefs.redirectMinDigits) return "a short number (${digits.length} digits)"
        val excluded = IntegrationPrefs.redirectExcludedPrefixes.split(',', ';', '\n')
            .map { it.trim() }.filter { it.isNotEmpty() }
        val plain = number.replace(" ", "")
        excluded.firstOrNull { plain.startsWith(it) || digits.startsWith(it.removePrefix("+")) }?.let {
            return "starts with the excluded prefix $it"
        }
        val account = redirectAccount() ?: return "no SIP account"
        if (IntegrationPrefs.redirectOnlyWhenRegistered && account.state != RegistrationState.Ok) {
            return "the SIP account is not registered (${account.state})"
        }
        if (mode == RedirectMode.WIFI) {
            if (!onWifi(context)) return "not on Wi-Fi"
            val wanted = IntegrationPrefs.redirectWifiNetworks.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (wanted.isNotEmpty()) {
                val ssid = currentSsid(context)
                    ?: return "the Wi-Fi network's name is not readable (the location permission is needed for it)"
                if (ssid !in wanted) return "on Wi-Fi [$ssid], not one of the selected networks"
            }
        }
        return null
    }

    /** the account redirected calls use: the selected one, or the default */
    @WorkerThread
    fun redirectAccount(): Account? {
        val id = IntegrationPrefs.redirectAccount
        val core = coreContext.core
        if (id.isNotEmpty()) {
            core.accountList.firstOrNull { ManagedAccounts.idOf(it) == id }?.let { return it }
        }
        return LinphoneUtils.getDefaultAccount()
    }

    @JvmStatic
    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * The name of the Wi-Fi the device is on, or null when it can't be read: Android gives it only to apps with
     * the location permission (and with location on). WifiManager.connectionInfo is deprecated but is the one
     * synchronous way that still returns it; NetworkCapabilities redact it outside a location-aware callback.
     */
    @JvmStatic
    fun currentSsid(context: Context): String? {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null

        @Suppress("DEPRECATION")
        val ssid = wm.connectionInfo?.ssid?.removeSurrounding("\"") ?: return null
        return ssid.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
    }
}

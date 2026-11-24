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
package org.linphone.integration

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.corePreferences

/**
 * The settings of the Android integration (Settings → Android integration), kept in Linphone's own config file,
 * section [android_integration], like every other setting of the app (so they are part of remote provisioning,
 * backups and the config export).
 */
object IntegrationPrefs {
    private const val S = "android_integration"

    /** how calls run: through the system phone app (managed), Linphone's call screen (self-managed), or auto */
    enum class CallMode(val key: String) {
        AUTOMATIC("automatic"),
        SYSTEM("system"),
        LINPHONE("linphone");

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key } ?: AUTOMATIC
        }
    }

    /** which outgoing phone calls the call redirection service sends over a SIP account */
    enum class RedirectMode(val key: String) {
        OFF("off"),
        /** upstream's behaviour: only numbers of contacts that have a SIP address */
        SIP_CONTACTS("sip_contacts"),
        ALWAYS("always"),
        /** the system asks every time (CallRedirectionService.redirectCall(..., confirmFirst = true)) */
        ASK("ask"),
        /** only while connected to one of [redirectWifiNetworks] */
        WIFI("wifi");

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key } ?: OFF
        }
    }

    // the app's config, available before the Core is (a sync adapter may start the process on its own)
    private val config get() = corePreferences.config

    @AnyThread
    private fun bool(key: String, default: Boolean) = config.getBool(S, key, default)

    @WorkerThread
    private fun setBool(key: String, value: Boolean) = config.setBool(S, key, value)

    @AnyThread
    private fun string(key: String, default: String) = config.getString(S, key, default) ?: default

    @WorkerThread
    private fun setString(key: String, value: String) = config.setString(S, key, value)

    // ---- calls ----------------------------------------------------------------------------------------------

    var callMode: CallMode
        @AnyThread get() = CallMode.of(string("call_mode", CallMode.AUTOMATIC.key))

        @WorkerThread set(value) = setString("call_mode", value.key)

    /** calls started in Linphone's own UI also go through the system (TelecomManager.placeCall) when managed */
    var placeAppCallsThroughSystem: Boolean
        @AnyThread get() = bool("place_app_calls_through_system", true)

        @WorkerThread set(value) = setBool("place_app_calls_through_system", value)

    /** the calling account's label in the system: empty = the SIP account's display name or user@domain */
    var accountLabel: String
        @AnyThread get() = string("account_label", "")

        @WorkerThread set(value) = setString("account_label", value)

    /** offer tel: (phone numbers) in the system dialer, not only sip: addresses (a gateway such as the MU300) */
    var acceptPhoneNumbers: Boolean
        @AnyThread get() = bool("accept_phone_numbers", true)

        @WorkerThread set(value) = setBool("accept_phone_numbers", value)

    var reportHdAudio: Boolean
        @AnyThread get() = bool("report_hd_audio", true)

        @WorkerThread set(value) = setBool("report_hd_audio", value)

    var reportWifi: Boolean
        @AnyThread get() = bool("report_wifi", true)

        @WorkerThread set(value) = setBool("report_wifi", value)

    /** "via <account>" under the caller in the system call screen */
    var showStatusHints: Boolean
        @AnyThread get() = bool("status_hints", true)

        @WorkerThread set(value) = setBool("status_hints", value)

    /** reject with a quick text reply: the text goes to the caller as a chat message (on a gateway, an SMS) */
    var rejectWithMessage: Boolean
        @AnyThread get() = bool("reject_with_message", true)

        @WorkerThread set(value) = setBool("reject_with_message", value)

    /** rejecting an incoming call answers 486 Busy Here (the caller hears busy) instead of 603 Decline */
    var rejectAsBusy: Boolean
        @AnyThread get() = bool("reject_as_busy", false)

        @WorkerThread set(value) = setBool("reject_as_busy", value)

    /** an incoming video call rings in Linphone's own call screen (the system call screen has no video) */
    var videoCallsInLinphone: Boolean
        @AnyThread get() = bool("video_calls_in_linphone", true)

        @WorkerThread set(value) = setBool("video_calls_in_linphone", value)

    /** deflect a ringing call to another number (SIP 302) from the system call screen */
    var allowDeflect: Boolean
        @AnyThread get() = bool("allow_deflect", true)

        @WorkerThread set(value) = setBool("allow_deflect", value)

    /** blind and consultative transfer from the system call screen (SIP REFER) */
    var allowTransfer: Boolean
        @AnyThread get() = bool("allow_transfer", true)

        @WorkerThread set(value) = setBool("allow_transfer", value)

    /** the audio route the system call screen picks (earpiece, speaker, Bluetooth, headset) is followed */
    var followSystemAudioRoute: Boolean
        @AnyThread get() = bool("follow_system_audio_route", true)

        @WorkerThread set(value) = setBool("follow_system_audio_route", value)

    /** self-managed calls in the system's unified call history (Android 17: isLogExcluded = false) */
    var selfManagedCallLog: Boolean
        @AnyThread get() = bool("self_managed_call_log", true)

        @WorkerThread set(value) = setBool("self_managed_call_log", value)

    // ---- contacts --------------------------------------------------------------------------------------------

    /** which contacts get the actions "Call via …" / "Message via …" in the system contacts and dialer */
    enum class ContactsMode(val key: String) {
        OFF("off"),
        /** every phone number (a gateway such as the MU300 dials them) and SIP address */
        ALL_NUMBERS("all_numbers"),
        /** only contacts' SIP addresses */
        SIP_ONLY("sip_only");

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key } ?: ALL_NUMBERS
        }
    }

    var contactsMode: ContactsMode
        @AnyThread get() = ContactsMode.of(string("contacts_mode", ContactsMode.ALL_NUMBERS.key))

        @WorkerThread set(value) = setString("contacts_mode", value.key)

    var contactsCallAction: Boolean
        @AnyThread get() = bool("contacts_call_action", true)

        @WorkerThread set(value) = setBool("contacts_call_action", value)

    var contactsMessageAction: Boolean
        @AnyThread get() = bool("contacts_message_action", true)

        @WorkerThread set(value) = setBool("contacts_message_action", value)

    // ---- call redirection (ROLE_CALL_REDIRECTION) ------------------------------------------------------------

    var redirectMode: RedirectMode
        @AnyThread get() = RedirectMode.of(string("redirect_mode", RedirectMode.OFF.key))

        @WorkerThread set(value) = setString("redirect_mode", value.key)

    /** the SIP account (identity URI) redirected calls use; empty = the default account */
    var redirectAccount: String
        @AnyThread get() = string("redirect_account", "")

        @WorkerThread set(value) = setString("redirect_account", value)

    /** Wi-Fi networks (SSIDs, one per line or comma separated) for [RedirectMode.WIFI] */
    var redirectWifiNetworks: String
        @AnyThread get() = string("redirect_wifi_networks", "")

        @WorkerThread set(value) = setString("redirect_wifi_networks", value)

    /** never redirect numbers starting with one of these (comma separated), e.g. premium or service numbers */
    var redirectExcludedPrefixes: String
        @AnyThread get() = string("redirect_excluded_prefixes", "")

        @WorkerThread set(value) = setString("redirect_excluded_prefixes", value)

    /** numbers shorter than this (short codes, operator services) stay on the SIM */
    var redirectMinDigits: Int
        @AnyThread get() = config.getInt(S, "redirect_min_digits", 6)

        @WorkerThread set(value) = config.setInt(S, "redirect_min_digits", value)

    /** only redirect while the SIP account is registered (otherwise the call would fail) */
    var redirectOnlyWhenRegistered: Boolean
        @AnyThread get() = bool("redirect_only_when_registered", true)

        @WorkerThread set(value) = setBool("redirect_only_when_registered", value)
}

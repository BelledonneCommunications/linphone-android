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
package org.linphone.integration.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.integration.IntegrationPrefs.CallMode
import org.linphone.integration.IntegrationPrefs.RedirectMode
import org.linphone.integration.calls.CallLogPreferenceReceiver
import org.linphone.integration.calls.ManagedAccounts
import org.linphone.integration.calls.ManagedCalls

/**
 * The detailed settings of the Android integration, in Linphone's config ([IntegrationPrefs]), and the status of
 * what Android has to grant (calling accounts, permissions, roles) with a tap to grant it.
 */
@UiThread
class IntegrationPreferencesFragment : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "[Integration Settings]"
    }

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Log.i("$TAG CALL_PHONE ${if (it) "granted" else "denied"}")
        refreshStatus()
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Log.i("$TAG ACCESS_FINE_LOCATION ${if (it) "granted" else "denied"}")
        refreshStatus()
    }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.i("$TAG Call redirection role request: result ${it.resultCode}")
        refreshStatus()
    }

    /** reads IntegrationPrefs (any thread), writes on the core thread and applies the side effects there */
    private val store = object : PreferenceDataStore() {
        override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
            "place_app_calls_through_system" -> IntegrationPrefs.placeAppCallsThroughSystem
            "accept_phone_numbers" -> IntegrationPrefs.acceptPhoneNumbers
            "video_calls_in_linphone" -> IntegrationPrefs.videoCallsInLinphone
            "report_hd_audio" -> IntegrationPrefs.reportHdAudio
            "report_wifi" -> IntegrationPrefs.reportWifi
            "status_hints" -> IntegrationPrefs.showStatusHints
            "follow_system_audio_route" -> IntegrationPrefs.followSystemAudioRoute
            "reject_with_message" -> IntegrationPrefs.rejectWithMessage
            "reject_as_busy" -> IntegrationPrefs.rejectAsBusy
            "allow_deflect" -> IntegrationPrefs.allowDeflect
            "allow_transfer" -> IntegrationPrefs.allowTransfer
            "self_managed_call_log" -> IntegrationPrefs.selfManagedCallLog
            "redirect_only_when_registered" -> IntegrationPrefs.redirectOnlyWhenRegistered
            else -> defValue
        }

        override fun putBoolean(key: String, value: Boolean) {
            coreContext.postOnCoreThread { core ->
                when (key) {
                    "place_app_calls_through_system" -> IntegrationPrefs.placeAppCallsThroughSystem = value
                    "accept_phone_numbers" -> {
                        IntegrationPrefs.acceptPhoneNumbers = value
                        ManagedCalls.refresh(core)
                    }
                    "video_calls_in_linphone" -> IntegrationPrefs.videoCallsInLinphone = value
                    "report_hd_audio" -> IntegrationPrefs.reportHdAudio = value
                    "report_wifi" -> IntegrationPrefs.reportWifi = value
                    "status_hints" -> IntegrationPrefs.showStatusHints = value
                    "follow_system_audio_route" -> IntegrationPrefs.followSystemAudioRoute = value
                    "reject_with_message" -> IntegrationPrefs.rejectWithMessage = value
                    "reject_as_busy" -> IntegrationPrefs.rejectAsBusy = value
                    "allow_deflect" -> IntegrationPrefs.allowDeflect = value
                    "allow_transfer" -> IntegrationPrefs.allowTransfer = value
                    "self_managed_call_log" -> IntegrationPrefs.selfManagedCallLog = value
                    "redirect_only_when_registered" -> IntegrationPrefs.redirectOnlyWhenRegistered = value
                }
                coreContext.postOnMainThread { refreshStatus() }
            }
        }

        override fun getString(key: String, defValue: String?): String? = when (key) {
            "call_mode" -> IntegrationPrefs.callMode.key
            "account_label" -> IntegrationPrefs.accountLabel
            "redirect_mode" -> IntegrationPrefs.redirectMode.key
            "redirect_account" -> IntegrationPrefs.redirectAccount
            "redirect_wifi_networks" -> IntegrationPrefs.redirectWifiNetworks
            "redirect_min_digits" -> IntegrationPrefs.redirectMinDigits.toString()
            "redirect_excluded_prefixes" -> IntegrationPrefs.redirectExcludedPrefixes
            else -> defValue
        }

        override fun putString(key: String, value: String?) {
            val v = value.orEmpty()
            coreContext.postOnCoreThread { core ->
                when (key) {
                    "call_mode" -> {
                        IntegrationPrefs.callMode = CallMode.of(v)
                        ManagedCalls.refresh(core)
                    }
                    "account_label" -> {
                        IntegrationPrefs.accountLabel = v.trim()
                        ManagedCalls.refresh(core)
                    }
                    "redirect_mode" -> {
                        val mode = RedirectMode.of(v)
                        IntegrationPrefs.redirectMode = mode
                        // upstream's switch: MainActivity keeps the role and its permissions requested while on
                        corePreferences.useCallRedirectionService = mode != RedirectMode.OFF
                    }
                    "redirect_account" -> IntegrationPrefs.redirectAccount = v
                    "redirect_wifi_networks" -> IntegrationPrefs.redirectWifiNetworks = v.trim()
                    "redirect_min_digits" -> IntegrationPrefs.redirectMinDigits = v.trim().toIntOrNull()?.coerceIn(1, 20) ?: 6
                    "redirect_excluded_prefixes" -> IntegrationPrefs.redirectExcludedPrefixes = v.trim()
                }
                coreContext.postOnMainThread { refreshStatus() }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = store
        setPreferencesFromResource(R.xml.integration_preferences, rootKey)

        findPreference<EditTextPreference>("redirect_min_digits")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
        }
        findPreference<EditTextPreference>("redirect_excluded_prefixes")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_PHONE
        }

        findPreference<Preference>("status_calling_accounts")?.setOnPreferenceClickListener {
            startActivity(ManagedAccounts.enableIntent())
            true
        }
        findPreference<Preference>("status_call_permission")?.setOnPreferenceClickListener {
            if (!granted(Manifest.permission.CALL_PHONE)) callPermission.launch(Manifest.permission.CALL_PHONE)
            true
        }
        findPreference<Preference>("status_location_permission")?.setOnPreferenceClickListener {
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            true
        }
        findPreference<Preference>("status_redirection_role")?.setOnPreferenceClickListener {
            requestRedirectionRole()
            true
        }
        findPreference<Preference>("status_system_call_log")?.setOnPreferenceClickListener {
            callLogSettingsIntent()?.let { intent -> startActivity(intent) }
            true
        }
        // asking for what a setting needs when it is turned on
        findPreference<ListPreference>("call_mode")?.setOnPreferenceChangeListener { _, value ->
            if (value != CallMode.LINPHONE.key && !granted(Manifest.permission.CALL_PHONE)) {
                callPermission.launch(Manifest.permission.CALL_PHONE)
            }
            true
        }
        findPreference<ListPreference>("redirect_mode")?.setOnPreferenceChangeListener { _, value ->
            if (value != RedirectMode.OFF.key) requestRedirectionRole()
            true
        }
        findPreference<EditTextPreference>("redirect_wifi_networks")?.setOnPreferenceChangeListener { _, value ->
            if ((value as? String).orEmpty().isNotBlank() && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            true
        }
        loadAccounts()
    }

    override fun onResume() {
        super.onResume()
        // what the user may have changed in the system meanwhile (calling accounts, permissions, roles)
        coreContext.postOnCoreThread { core -> ManagedCalls.refresh(core) }
        refreshStatus()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun requestRedirectionRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rm = requireContext().getSystemService(RoleManager::class.java) ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION) && !rm.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)) {
            roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION))
        }
    }

    /** Android 17: the system's call log integration settings, when the device has them */
    private fun callLogSettingsIntent(): Intent? {
        val intent = Intent("android.telecom.action.CONFIGURE_CALL_LOG_INTEGRATION")
        return intent.takeIf { it.resolveActivity(requireContext().packageManager) != null }
    }

    /** the SIP accounts for the redirection account choice */
    private fun loadAccounts() {
        val context = context?.applicationContext ?: return
        val defaultLabel = getString(R.string.integration_redirect_account_default)
        coreContext.postOnCoreThread { core ->
            val ids = arrayListOf("")
            val labels = arrayListOf(defaultLabel)
            for (account in core.accountList) {
                if (account.params.identityAddress == null) continue
                ids.add(ManagedAccounts.idOf(account))
                labels.add(ManagedAccounts.label(context, account))
            }
            coreContext.postOnMainThread {
                if (!isAdded) return@postOnMainThread
                findPreference<ListPreference>("redirect_account")?.apply {
                    entries = labels.toTypedArray()
                    entryValues = ids.toTypedArray()
                    val current = store.getString("redirect_account", "") ?: ""
                    summary = labels.getOrNull(ids.indexOf(current)) ?: labels[0]
                }
            }
        }
    }

    /** summaries and visibility from the current settings and what the system has granted */
    private fun refreshStatus() {
        if (!isAdded) return
        val context = requireContext().applicationContext
        val mode = CallMode.of(store.getString("call_mode", null))
        findPreference<ListPreference>("call_mode")?.summary = getString(
            when (mode) {
                CallMode.AUTOMATIC -> R.string.integration_call_mode_summary_automatic
                CallMode.SYSTEM -> R.string.integration_call_mode_summary_system
                CallMode.LINPHONE -> R.string.integration_call_mode_summary_linphone
            }
        )
        val systemCalls = mode != CallMode.LINPHONE
        for (key in listOf(
            "status_calling_accounts", "status_call_permission", "place_app_calls_through_system",
            "accept_phone_numbers", "account_label", "video_calls_in_linphone", "cat_call_screen"
        )) {
            findPreference<Preference>(key)?.isVisible = systemCalls
        }
        findPreference<Preference>("status_call_permission")?.summary = getString(
            if (granted(Manifest.permission.CALL_PHONE)) {
                R.string.integration_call_permission_granted
            } else {
                R.string.integration_call_permission_missing
            }
        )
        findPreference<EditTextPreference>("account_label")?.summary =
            IntegrationPrefs.accountLabel.ifBlank { getString(R.string.integration_account_label_default) }

        // calling accounts: one line per SIP account
        coreContext.postOnCoreThread { core ->
            val lines = core.accountList.filter { it.params.identityAddress != null }.map { account ->
                val label = ManagedAccounts.label(context, account)
                if (ManagedAccounts.isEnabled(context, ManagedAccounts.handleOf(context, account))) {
                    context.getString(R.string.integration_calling_account_enabled, label)
                } else {
                    context.getString(R.string.integration_calling_account_disabled, label)
                }
            }
            coreContext.postOnMainThread {
                if (!isAdded) return@postOnMainThread
                findPreference<Preference>("status_calling_accounts")?.summary =
                    lines.joinToString("\n").ifEmpty { getString(R.string.integration_calling_accounts_none) }
            }
        }

        // the system call history (Android 17)
        findPreference<Preference>("status_system_call_log")?.summary = when {
            Build.VERSION.SDK_INT < 37 -> getString(R.string.integration_system_call_log_unavailable)
            CallLogPreferenceReceiver.allowed(context) == true -> getString(R.string.integration_system_call_log_allowed)
            CallLogPreferenceReceiver.allowed(context) == false -> getString(R.string.integration_system_call_log_refused)
            callLogSettingsIntent() != null -> getString(R.string.integration_system_call_log_unknown)
            else -> getString(R.string.integration_system_call_log_unavailable)
        }
        findPreference<Preference>("self_managed_call_log")?.isEnabled = Build.VERSION.SDK_INT >= 37

        // redirection
        val redirect = RedirectMode.of(store.getString("redirect_mode", null))
        val role = findPreference<Preference>("status_redirection_role")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            role?.summary = getString(
                when {
                    rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION) ->
                        R.string.integration_redirection_role_unavailable
                    rm.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) -> R.string.integration_redirection_role_held
                    else -> R.string.integration_redirection_role_missing
                }
            )
        } else {
            role?.summary = getString(R.string.integration_redirection_role_unavailable)
        }
        val redirecting = redirect != RedirectMode.OFF
        for (key in listOf(
            "status_redirection_role", "redirect_account", "redirect_only_when_registered",
            "redirect_min_digits", "redirect_excluded_prefixes"
        )) {
            findPreference<Preference>(key)?.isVisible = redirecting
        }
        val wifi = redirect == RedirectMode.WIFI
        findPreference<Preference>("redirect_wifi_networks")?.apply {
            isVisible = wifi
            summary = IntegrationPrefs.redirectWifiNetworks.ifBlank { getString(R.string.integration_redirect_wifi_any) }
        }
        findPreference<Preference>("status_location_permission")?.apply {
            isVisible = wifi && IntegrationPrefs.redirectWifiNetworks.isNotBlank()
            summary = getString(
                if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    R.string.integration_location_permission_granted
                } else {
                    R.string.integration_location_permission_missing
                }
            )
        }
        findPreference<Preference>("redirect_min_digits")?.summary =
            getString(R.string.integration_redirect_min_digits_summary, IntegrationPrefs.redirectMinDigits.toString())
        findPreference<Preference>("redirect_excluded_prefixes")?.summary =
            IntegrationPrefs.redirectExcludedPrefixes.ifBlank { getString(R.string.integration_none) }
        findPreference<ListPreference>("redirect_mode")?.let { it.summary = it.entry }
        loadAccounts()
    }
}

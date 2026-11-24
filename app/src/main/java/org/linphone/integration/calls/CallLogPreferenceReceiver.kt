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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * Android 17: the user allowed or refused this app's calls in the system's unified call history
 * (Settings → call log integration). The choice is kept for the settings screen; Telecom applies it itself.
 */
class CallLogPreferenceReceiver : BroadcastReceiver() {
    companion object {
        private const val PREFS = "integration_state"
        private const val KEY = "system_call_log_allowed"

        // TelecomManager.ACTION_VOIP_CALL_LOG_PREFERENCE / EXTRA_VOIP_CALL_LOG_PREFERENCE_STATUS (API 37)
        const val ACTION = "android.telecom.action.VOIP_CALL_LOG_PREFERENCE"
        private const val EXTRA = "android.telecom.extra.VOIP_CALL_LOG_PREFERENCE_STATUS"

        /** true / false as the user set it in the system, null: never told */
        fun allowed(context: Context): Boolean? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return if (prefs.contains(KEY)) prefs.getBoolean(KEY, true) else null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !intent.hasExtra(EXTRA)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, intent.getBooleanExtra(EXTRA, true))
        }
    }
}

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

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.annotation.AnyThread
import androidx.core.content.edit
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

/**
 * "Call back" from the system's unified call history (Android 17) for a self-managed call:
 * TelecomManager.ACTION_CALL_BACK with EXTRA_UUID, the CallControl call id the call had. The address of every
 * self-managed call is kept under that id when the call is added to Telecom ([remember]).
 *
 * No UI of its own: it starts the call and finishes.
 */
class CallBackActivity : Activity() {
    companion object {
        private const val TAG = "[Call Back]"
        private const val PREFS = "integration_call_back"
        private const val KEEP = 500

        @AnyThread
        fun remember(uuid: String, sipUri: String) {
            val prefs = coreContext.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit {
                putString(uuid, "${System.currentTimeMillis()}|$sipUri")
                // the oldest go once there are more than KEEP
                val all = prefs.all
                if (all.size >= KEEP) {
                    all.entries
                        .sortedBy { (it.value as? String)?.substringBefore('|')?.toLongOrNull() ?: 0L }
                        .take(all.size - KEEP + 1)
                        .forEach { remove(it.key) }
                }
            }
        }

        @AnyThread
        private fun lookup(context: Context, uuid: String): String? {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(uuid, null)?.substringAfter('|')
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uuid = intent?.getStringExtra(TelecomManager.EXTRA_UUID)
        val uri = uuid?.let { lookup(this, it) }
        if (uri == null) {
            Log.w("$TAG No call known for [$uuid]")
        } else {
            Log.i("$TAG Calling back [$uri]")
            coreContext.postOnCoreThread { core ->
                val address = core.interpretUrl(uri, false)
                if (address != null) {
                    coreContext.startCall(address)
                } else {
                    Log.e("$TAG [$uri] is not a SIP address")
                }
            }
        }
        finish()
    }
}

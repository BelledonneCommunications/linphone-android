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
package org.linphone.compatibility

import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import org.linphone.core.tools.Log

class PhoneStateListener(private val telephonyManager: TelephonyManager) : PhoneStateInterface {
    private var gsmCallActive = false
    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            gsmCallActive = when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.i("[Context] Phone state is off hook")
                    true
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.i("[Context] Phone state is ringing")
                    true
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.i("[Context] Phone state is idle")
                    false
                }
                else -> {
                    Log.w("[Context] Phone state is unexpected: $state")
                    false
                }
            }
        }
    }

    init {
        Log.i("[Phone State Listener] Registering phone state listener")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun destroy() {
        Log.i("[Phone State Listener] Unregistering phone state listener")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

    override fun isInCall(): Boolean {
        return gsmCallActive
    }
}

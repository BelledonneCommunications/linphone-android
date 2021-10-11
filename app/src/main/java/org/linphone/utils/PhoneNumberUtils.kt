/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.utils

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import org.linphone.core.DialPlan
import org.linphone.core.Factory
import org.linphone.core.tools.Log

class PhoneNumberUtils {
    companion object {
        fun getDialPlanForCurrentCountry(context: Context): DialPlan? {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val countryIso = tm.networkCountryIso
                return getDialPlanFromCountryCode(countryIso)
            } catch (e: java.lang.Exception) {
                Log.e("[Phone Number Utils] $e")
            }
            return null
        }

        @SuppressLint("MissingPermission", "HardwareIds")
        fun getDevicePhoneNumber(context: Context): String? {
            if (PermissionHelper.get().hasReadPhoneStateOrPhoneNumbersPermission()) {
                try {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    return tm.line1Number
                } catch (e: java.lang.Exception) {
                    Log.e("[Phone Number Utils] $e")
                }
            }
            return null
        }

        fun getDialPlanFromCountryCallingPrefix(countryCode: String): DialPlan? {
            for (c in Factory.instance().dialPlans) {
                if (countryCode == c.countryCallingCode) return c
            }
            return null
        }

        private fun getDialPlanFromCountryCode(countryCode: String): DialPlan? {
            for (c in Factory.instance().dialPlans) {
                if (countryCode.equals(c.isoCountryCode, ignoreCase = true)) return c
            }
            return null
        }
    }
}

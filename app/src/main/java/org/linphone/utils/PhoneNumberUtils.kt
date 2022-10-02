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
import android.content.res.Resources
import android.provider.ContactsContract
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

        fun addressBookLabelTypeToVcardParamString(type: Int, default: String?): String {
            return when (type) {
                ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT -> "assistant"
                ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK -> "callback"
                ContactsContract.CommonDataKinds.Phone.TYPE_CAR -> "car"
                ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN -> "work,main"
                ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "home,fax"
                ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "work,fax"
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
                ContactsContract.CommonDataKinds.Phone.TYPE_ISDN -> "isdn"
                ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
                ContactsContract.CommonDataKinds.Phone.TYPE_MMS -> "text"
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "cell"
                ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
                ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX -> "fax"
                ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "pager"
                ContactsContract.CommonDataKinds.Phone.TYPE_RADIO -> "radio"
                ContactsContract.CommonDataKinds.Phone.TYPE_TELEX -> "telex"
                ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD -> "textphone"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "work,cell"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER -> "work,pager"
                ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM -> default ?: "custom"
                else -> default ?: type.toString()
            }
        }

        fun vcardParamStringToAddressBookLabel(resources: Resources, label: String): String {
            if (label.isEmpty()) return label
            val type = labelToType(label)
            return ContactsContract.CommonDataKinds.Phone.getTypeLabel(resources, type, label).toString()
        }

        private fun labelToType(label: String): Int {
            return when (label) {
                "assistant" -> ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT
                "callback" -> ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK
                "car" -> ContactsContract.CommonDataKinds.Phone.TYPE_CAR
                "work,main" -> ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN
                "home,fax" -> ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME
                "work,fax" -> ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK
                "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                "isdn" -> ContactsContract.CommonDataKinds.Phone.TYPE_ISDN
                "main" -> ContactsContract.CommonDataKinds.Phone.TYPE_MAIN
                "text" -> ContactsContract.CommonDataKinds.Phone.TYPE_MMS
                "cell" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                "other" -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                "fax" -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX
                "pager" -> ContactsContract.CommonDataKinds.Phone.TYPE_PAGER
                "radio" -> ContactsContract.CommonDataKinds.Phone.TYPE_RADIO
                "telex" -> ContactsContract.CommonDataKinds.Phone.TYPE_TELEX
                "textphone" -> ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD
                "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                "work,cell" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE
                "work,pager" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER
                "custom" -> ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM
                else -> ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM
            }
        }

        private fun getDialPlanFromCountryCode(countryCode: String): DialPlan? {
            for (c in Factory.instance().dialPlans) {
                if (countryCode.equals(c.isoCountryCode, ignoreCase = true)) return c
            }
            return null
        }

        fun arePhoneNumberWeakEqual(number1: String, number2: String): Boolean {
            return trimPhoneNumber(number1) == trimPhoneNumber(number2)
        }

        private fun trimPhoneNumber(phoneNumber: String): String {
            return phoneNumber.replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
        }
    }
}

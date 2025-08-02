/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package com.naminfo.utils

import android.content.res.Resources
import android.provider.ContactsContract
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.linphone.core.DialPlan
import org.linphone.core.Factory
import org.linphone.core.tools.Log

class PhoneNumberUtils {
    companion object {
        private const val TAG = "[Phone Number Utils]"

        @WorkerThread
        fun getDeviceDialPlan(countryIso: String): DialPlan? {
            for (dp in Factory.instance().dialPlans) {
                if (dp.isoCountryCode.equals(countryIso, true)) {
                    val prefix = dp.countryCallingCode
                    Log.i(
                        "$TAG Found matching entry [$prefix] in dialplan for network country iso [$countryIso]"
                    )
                    return dp
                }
            }
            return null
        }

        @AnyThread
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
                ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM -> {
                    Log.d(
                        "$TAG Found custom phone label type using default value [$default] or will use 'custom' if null"
                    )
                    default ?: "custom"
                }
                else -> {
                    Log.w(
                        "$TAG Can't translate phone label type [$type], using default value [$default]"
                    )
                    default ?: type.toString()
                }
            }
        }

        @AnyThread
        fun vcardParamStringToAddressBookLabel(resources: Resources, label: String): String {
            if (label.isEmpty()) return label
            val type = labelToType(label)
            return ContactsContract.CommonDataKinds.Phone.getTypeLabel(resources, type, label).toString()
        }

        @AnyThread
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
    }
}

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

package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.MutableLiveData
import org.linphone.activities.assistant.fragments.CountryPickerFragment
import org.linphone.core.AccountCreator
import org.linphone.core.DialPlan
import org.linphone.core.tools.Log
import org.linphone.utils.PhoneNumberUtils

abstract class AbstractPhoneViewModel(accountCreator: AccountCreator) :
    AbstractPushTokenViewModel(accountCreator),
    CountryPickerFragment.CountryPickedListener {

    val prefix = MutableLiveData<String>()
    val prefixError = MutableLiveData<String>()

    val phoneNumber = MutableLiveData<String>()
    val phoneNumberError = MutableLiveData<String>()

    val countryName = MutableLiveData<String>()

    init {
        prefix.value = "+"
    }

    override fun onCountryClicked(dialPlan: DialPlan) {
        prefix.value = "+${dialPlan.countryCallingCode}"
        countryName.value = dialPlan.country
    }

    fun isPhoneNumberOk(): Boolean {
        return prefix.value.orEmpty().length > 1 && // Not just '+' character
            prefixError.value.orEmpty().isEmpty() &&
            phoneNumber.value.orEmpty().isNotEmpty() &&
            phoneNumberError.value.orEmpty().isEmpty()
    }

    fun updateFromPhoneNumberAndOrDialPlan(number: String?, dialPlan: DialPlan?) {
        val internationalPrefix = "+${dialPlan?.countryCallingCode}"
        if (dialPlan != null) {
            Log.i("[Assistant] Found prefix from dial plan: ${dialPlan.countryCallingCode}")
            prefix.value = internationalPrefix
            getCountryNameFromPrefix(internationalPrefix)
        }

        if (number != null) {
            Log.i("[Assistant] Found phone number: $number")
            phoneNumber.value = if (number.startsWith(internationalPrefix)) {
                number.substring(internationalPrefix.length)
            } else {
                number
            }
        }
    }

    fun getCountryNameFromPrefix(prefix: String?) {
        if (!prefix.isNullOrEmpty()) {
            val countryCode = if (prefix.first() == '+') prefix.substring(1) else prefix
            val dialPlan = PhoneNumberUtils.getDialPlanFromCountryCallingPrefix(countryCode)
            Log.i("[Assistant] Found dial plan $dialPlan from country code: $countryCode")
            countryName.value = dialPlan?.country
        }
    }
}

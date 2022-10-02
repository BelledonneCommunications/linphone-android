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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import org.linphone.activities.assistant.fragments.CountryPickerFragment
import org.linphone.core.AccountCreator
import org.linphone.core.DialPlan
import org.linphone.core.tools.Log
import org.linphone.utils.PhoneNumberUtils

abstract class AbstractPhoneViewModel(val accountCreator: AccountCreator) :
    ViewModel(),
    CountryPickerFragment.CountryPickedListener {

    val prefix = MutableLiveData<String>()

    val phoneNumber = MutableLiveData<String>()
    val phoneNumberError = MutableLiveData<String>()

    val countryName: LiveData<String> = Transformations.switchMap(prefix) {
        getCountryNameFromPrefix(it)
    }

    init {
        prefix.value = "+"
    }

    override fun onCountryClicked(dialPlan: DialPlan) {
        prefix.value = "+${dialPlan.countryCallingCode}"
    }

    fun isPhoneNumberOk(): Boolean {
        return countryName.value.orEmpty().isNotEmpty() && phoneNumber.value.orEmpty().isNotEmpty() && phoneNumberError.value.orEmpty().isEmpty()
    }

    fun updateFromPhoneNumberAndOrDialPlan(number: String?, dialPlan: DialPlan?) {
        val internationalPrefix = "+${dialPlan?.countryCallingCode}"
        if (dialPlan != null) {
            Log.i("[Assistant] Found prefix from dial plan: ${dialPlan.countryCallingCode}")
            prefix.value = internationalPrefix
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

    private fun getCountryNameFromPrefix(prefix: String?): MutableLiveData<String> {
        val country = MutableLiveData<String>()
        country.value = ""

        if (prefix != null && prefix.isNotEmpty()) {
            val countryCode = if (prefix.first() == '+') prefix.substring(1) else prefix
            val dialPlan = PhoneNumberUtils.getDialPlanFromCountryCallingPrefix(countryCode)
            Log.i("[Assistant] Found dial plan $dialPlan from country code: $countryCode")
            country.value = dialPlan?.country
        }
        return country
    }
}

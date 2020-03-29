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
import androidx.lifecycle.ViewModel
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.*
import org.linphone.core.tools.Log

class SharedAssistantViewModel : ViewModel() {
    val remoteProvisioningUrl = MutableLiveData<String>()

    private var accountCreator: AccountCreator
    private var useGenericSipAccount: Boolean = false

    init {
        Log.i("[Assistant] Loading linphone default values")
        coreContext.core.loadConfigFromXml(corePreferences.linphoneDefaultValuesPath)
        accountCreator = coreContext.core.createAccountCreator(corePreferences.xmlRpcServerUrl)
        accountCreator.language = Locale.getDefault().language
    }

    fun getAccountCreator(genericAccountCreator: Boolean = false): AccountCreator {
        if (genericAccountCreator != useGenericSipAccount) {
            accountCreator.reset()
            accountCreator.language = Locale.getDefault().language

            if (genericAccountCreator) {
                Log.i("[Assistant] Loading default values")
                coreContext.core.loadConfigFromXml(corePreferences.defaultValuesPath)
            } else {
                Log.i("[Assistant] Loading linphone default values")
                coreContext.core.loadConfigFromXml(corePreferences.linphoneDefaultValuesPath)
            }
            useGenericSipAccount = genericAccountCreator
        }
        return accountCreator
    }
}

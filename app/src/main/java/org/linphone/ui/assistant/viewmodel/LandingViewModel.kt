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
package org.linphone.ui.assistant.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences

class LandingViewModel @UiThread constructor() : AccountLoginViewModel() {
    companion object {
        private const val TAG = "[Account Login ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val hideCreateAccount = MutableLiveData<Boolean>()

    val hideScanQrCode = MutableLiveData<Boolean>()

    val hideThirdPartyAccount = MutableLiveData<Boolean>()

    /*val redirectToDigestAuthEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val redirectToSingleSignOnEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }*/

    var conditionsAndPrivacyPolicyAccepted = false

    init {
        coreContext.postOnCoreThread { core ->
            // Prevent user from leaving assistant if no account was configured yet
            showBackButton.postValue(core.accountList.isNotEmpty())
            hideCreateAccount.postValue(corePreferences.hideAssistantCreateAccount)
            hideScanQrCode.postValue(corePreferences.hideAssistantScanQrCode)
            hideThirdPartyAccount.postValue(corePreferences.hideAssistantThirdPartySipAccount)
            conditionsAndPrivacyPolicyAccepted = corePreferences.conditionsAndPrivacyPolicyAccepted
        }
    }
}

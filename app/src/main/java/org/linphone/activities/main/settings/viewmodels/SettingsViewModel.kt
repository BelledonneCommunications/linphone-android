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
package org.linphone.activities.main.settings.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.settings.SettingListenerStub

class SettingsViewModel : ViewModel() {
    val tunnelAvailable: Boolean = coreContext.core.tunnelAvailable()

    val showAccountSettings: Boolean = corePreferences.showAccountSettings
    val showTunnelSettings: Boolean = tunnelAvailable && corePreferences.showTunnelSettings
    val showAudioSettings: Boolean = corePreferences.showAudioSettings
    val showVideoSettings: Boolean = corePreferences.showVideoSettings
    val showCallSettings: Boolean = corePreferences.showCallSettings
    val showChatSettings: Boolean = corePreferences.showChatSettings
    val showNetworkSettings: Boolean = corePreferences.showNetworkSettings
    val showContactsSettings: Boolean = corePreferences.showContactsSettings
    val showAdvancedSettings: Boolean = corePreferences.showAdvancedSettings

    val accounts = MutableLiveData<ArrayList<AccountSettingsViewModel>>()

    private var accountClickListener = object : SettingListenerStub() {
        override fun onAccountClicked(identity: String) {
            accountsSettingsListener.onAccountClicked(identity)
        }
    }

    lateinit var accountsSettingsListener: SettingListenerStub

    lateinit var tunnelSettingsListener: SettingListenerStub

    lateinit var audioSettingsListener: SettingListenerStub

    lateinit var videoSettingsListener: SettingListenerStub

    lateinit var callSettingsListener: SettingListenerStub

    lateinit var chatSettingsListener: SettingListenerStub

    lateinit var networkSettingsListener: SettingListenerStub

    lateinit var contactsSettingsListener: SettingListenerStub

    lateinit var advancedSettingsListener: SettingListenerStub

    init {
        updateAccountsList()
    }

    fun updateAccountsList() {
        val list = arrayListOf<AccountSettingsViewModel>()
        if (coreContext.core.accountList.isNotEmpty()) {
            for (account in coreContext.core.accountList) {
                val viewModel = AccountSettingsViewModel(account)
                viewModel.accountsSettingsListener = accountClickListener
                list.add(viewModel)
            }
        }
        accounts.value = list
    }
}

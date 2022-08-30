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
package org.linphone.activities.main.sidemenu.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.settings.viewmodels.AccountSettingsViewModel
import org.linphone.core.*
import org.linphone.utils.LinphoneUtils

class SideMenuViewModel : ViewModel() {
    val showAccounts: Boolean = corePreferences.showAccountsInSideMenu
    val showAssistant: Boolean = corePreferences.showAssistantInSideMenu
    val showSettings: Boolean = corePreferences.showSettingsInSideMenu
    val showRecordings: Boolean = corePreferences.showRecordingsInSideMenu
    val showScheduledConferences = MutableLiveData<Boolean>()
    val showAbout: Boolean = corePreferences.showAboutInSideMenu
    val showQuit: Boolean = corePreferences.showQuitInSideMenu

    val defaultAccountViewModel = MutableLiveData<AccountSettingsViewModel>()
    val defaultAccountFound = MutableLiveData<Boolean>()
    val defaultAccountAvatar = MutableLiveData<String>()

    val accounts = MutableLiveData<ArrayList<AccountSettingsViewModel>>()

    lateinit var accountsSettingsListener: SettingListenerStub

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            // +1 is for the default account, otherwise this will trigger every time
            if (accounts.value.isNullOrEmpty() ||
                coreContext.core.accountList.size != accounts.value.orEmpty().size + 1
            ) {
                // Only refresh the list if an account has been added or removed
                updateAccountsList()
            }
        }
    }

    init {
        defaultAccountFound.value = false
        defaultAccountAvatar.value = corePreferences.defaultAccountAvatarPath
        showScheduledConferences.value = corePreferences.showScheduledConferencesInSideMenu &&
            LinphoneUtils.isRemoteConferencingAvailable()
        coreContext.core.addListener(listener)
        updateAccountsList()
    }

    override fun onCleared() {
        defaultAccountViewModel.value?.destroy()
        accounts.value.orEmpty().forEach(AccountSettingsViewModel::destroy)
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun updateAccountsList() {
        defaultAccountFound.value = false // Do not assume a default account will still be found
        defaultAccountViewModel.value?.destroy()
        accounts.value.orEmpty().forEach(AccountSettingsViewModel::destroy)

        val list = arrayListOf<AccountSettingsViewModel>()
        val defaultAccount = coreContext.core.defaultAccount
        if (defaultAccount != null) {
            val defaultViewModel = AccountSettingsViewModel(defaultAccount)
            defaultViewModel.accountsSettingsListener = object : SettingListenerStub() {
                override fun onAccountClicked(identity: String) {
                    accountsSettingsListener.onAccountClicked(identity)
                }
            }
            defaultAccountViewModel.value = defaultViewModel
            defaultAccountFound.value = true
        }

        for (account in LinphoneUtils.getAccountsNotHidden()) {
            if (account != coreContext.core.defaultAccount) {
                val viewModel = AccountSettingsViewModel(account)
                viewModel.accountsSettingsListener = object : SettingListenerStub() {
                    override fun onAccountClicked(identity: String) {
                        accountsSettingsListener.onAccountClicked(identity)
                    }
                }
                list.add(viewModel)
            }
        }
        accounts.value = list

        showScheduledConferences.value = corePreferences.showScheduledConferencesInSideMenu &&
            LinphoneUtils.isRemoteConferencingAvailable()
    }

    fun setPictureFromPath(picturePath: String) {
        corePreferences.defaultAccountAvatarPath = picturePath
        defaultAccountAvatar.value = corePreferences.defaultAccountAvatarPath
        coreContext.contactsManager.updateLocalContacts()
    }
}

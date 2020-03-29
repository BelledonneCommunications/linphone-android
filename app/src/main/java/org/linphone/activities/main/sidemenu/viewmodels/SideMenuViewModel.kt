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
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.settings.viewmodels.AccountSettingsViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log

class SideMenuViewModel : ViewModel() {
    val showAssistant: Boolean = true
    val showSettings: Boolean = true
    val showRecordings: Boolean = true
    val showAbout: Boolean = true

    val defaultAccount = MutableLiveData<AccountSettingsViewModel>()
    val defaultAccountFound = MutableLiveData<Boolean>()

    val accounts = MutableLiveData<ArrayList<AccountSettingsViewModel>>()

    lateinit var accountsSettingsListener: SettingListenerStub

    private var accountClickListener = object : SettingListenerStub() {
        override fun onAccountClicked(identity: String) {
            accountsSettingsListener.onAccountClicked(identity)
        }
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onRegistrationStateChanged(
            core: Core,
            cfg: ProxyConfig,
            state: RegistrationState,
            message: String?
        ) {
            if (coreContext.core.proxyConfigList.size != accounts.value?.size) {
                // Only refresh the list if a proxy has been added or removed
                updateAccountsList()
            }
        }
    }

    private val quitListener: CoreListenerStub = object : CoreListenerStub() {
        override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String?) {
            if (state == GlobalState.Off) {
                Log.w("[Side Menu] Core properly terminated, killing process")
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    init {
        defaultAccountFound.value = false
        coreContext.core.addListener(listener)
        updateAccountsList()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun quit() {
        coreContext.core.addListener(quitListener)
        coreContext.stop()
    }

    fun updateAccountsList() {
        val list = arrayListOf<AccountSettingsViewModel>()
        if (coreContext.core.proxyConfigList.isNotEmpty()) {
            val defaultProxyConfig = coreContext.core.defaultProxyConfig
            if (defaultProxyConfig != null) {
                val defaultViewModel = AccountSettingsViewModel(defaultProxyConfig)
                defaultViewModel.accountsSettingsListener = accountClickListener
                defaultAccount.value = defaultViewModel
                defaultAccountFound.value = true
            }

            for (proxy in coreContext.core.proxyConfigList) {
                if (proxy != coreContext.core.defaultProxyConfig) {
                    val viewModel = AccountSettingsViewModel(proxy)
                    viewModel.accountsSettingsListener = accountClickListener
                    list.add(viewModel)
                }
            }
        }
        accounts.value = list
    }
}

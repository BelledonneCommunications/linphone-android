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
package org.linphone.ui.main.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.RegistrationState
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.Event

class DrawerMenuViewModel : ViewModel() {
    val accounts = MutableLiveData<ArrayList<AccountModel>>()

    val startAssistantEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val closeDrawerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            // Core thread
            computeAccountsList()
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            computeAccountsList()
        }
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    fun closeDrawerMenu() {
        // UI thread
        closeDrawerEvent.value = Event(true)
    }

    fun addAccount() {
        // UI thread
        startAssistantEvent.value = Event(true)
    }

    private fun computeAccountsList() {
        // Core thread
        accounts.value.orEmpty().forEach(AccountModel::destroy)

        val list = arrayListOf<AccountModel>()
        for (account in coreContext.core.accountList) {
            val model = AccountModel(account)
            list.add(model)
        }
        accounts.postValue(list)
    }
}

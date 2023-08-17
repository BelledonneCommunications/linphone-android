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

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Account
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class AssistantViewModel : ViewModel() {
    companion object {
        const val TAG = "[Assistant ViewModel]"
    }
    val username = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val loginEnabled = MediatorLiveData<Boolean>()

    val registrationInProgress = MutableLiveData<Boolean>()

    val accountLoggedInEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var newlyCreatedAuthInfo: AuthInfo
    private lateinit var newlyCreatedAccount: Account

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            // Core thread
            if (account == newlyCreatedAccount) {
                Log.i("$TAG Newly created account registration state is [$state] ($message)")

                if (state == RegistrationState.Ok) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)

                    // Set new account as default
                    core.defaultAccount = newlyCreatedAccount
                    coreContext.contactsManager.updateLocalContacts()
                    accountLoggedInEvent.postValue(Event(true))
                } else if (state == RegistrationState.Failed) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)

                    // TODO FIXME: show error

                    Log.e("$TAG Account failed to REGISTER, removing it")
                    core.removeAuthInfo(newlyCreatedAuthInfo)
                    core.removeAccount(newlyCreatedAccount)
                }
            }
        }
    }

    init {
        registrationInProgress.value = false

        loginEnabled.addSource(username) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }
    }

    fun login() {
        // UI thread
        coreContext.postOnCoreThread { core ->
            core.loadConfigFromXml(corePreferences.linphoneDefaultValuesPath)

            val user = username.value.orEmpty()
            val domain = corePreferences.defaultDomain

            newlyCreatedAuthInfo = Factory.instance().createAuthInfo(
                user,
                null,
                password.value.orEmpty(),
                null,
                null,
                domain
            )
            core.addAuthInfo(newlyCreatedAuthInfo)

            val accountParams = core.createAccountParams()
            val identityAddress = Factory.instance().createAddress("sip:$user@$domain")
            accountParams.identityAddress = identityAddress
            newlyCreatedAccount = core.createAccount(accountParams)

            registrationInProgress.postValue(true)
            core.addListener(coreListener)
            core.addAccount(newlyCreatedAccount)
        }
    }

    private fun isLoginButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() && password.value.orEmpty().isNotEmpty()
    }
}

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

import androidx.lifecycle.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class AccountLoginViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return AccountLoginViewModel(accountCreator) as T
    }
}

class AccountLoginViewModel(accountCreator: AccountCreator) : AbstractPhoneViewModel(accountCreator) {
    val loginWithUsernamePassword = MutableLiveData<Boolean>()

    val username = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val loginEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val leaveAssistantEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val invalidCredentialsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToSmsValidationEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : AccountCreatorListenerStub() {
        override fun onRecoverAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("[Assistant] [Account Login] Recover account status is $status")
            waitForServerAnswer.value = false

            if (status == AccountCreator.Status.RequestOk) {
                goToSmsValidationEvent.value = Event(true)
            } else {
                // TODO: show error
            }
        }
    }

    private var proxyConfigToCheck: ProxyConfig? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onRegistrationStateChanged(
            core: Core,
            cfg: ProxyConfig,
            state: RegistrationState,
            message: String?
        ) {
            if (cfg == proxyConfigToCheck) {
                Log.i("[Assistant] [Account Login] Registration state is $state: $message")
                waitForServerAnswer.value = false
                if (state == RegistrationState.Ok) {
                    leaveAssistantEvent.value = Event(true)
                    core.removeListener(this)
                } else if (state == RegistrationState.Failed) {
                    invalidCredentialsEvent.value = Event(true)
                    core.removeListener(this)
                }
            }
        }
    }

    init {
        accountCreator.addListener(listener)

        loginWithUsernamePassword.value = false

        loginEnabled.value = false
        loginEnabled.addSource(prefix) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(phoneNumber) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(username) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(loginWithUsernamePassword) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(phoneNumberError) {
            loginEnabled.value = isLoginButtonEnabled()
        }
    }

    override fun onCleared() {
        accountCreator.removeListener(listener)
        super.onCleared()
    }

    fun removeInvalidProxyConfig() {
        val cfg = proxyConfigToCheck
        cfg ?: return
        val authInfo = cfg.findAuthInfo()
        if (authInfo != null) coreContext.core.removeAuthInfo(authInfo)
        coreContext.core.removeProxyConfig(cfg)
        proxyConfigToCheck = null
    }

    fun continueEvenIfInvalidCredentials() {
        leaveAssistantEvent.value = Event(true)
    }

    fun login() {
        if (loginWithUsernamePassword.value == true) {
            accountCreator.username = username.value
            accountCreator.password = password.value
            Log.i("[Assistant] [Account Login] Username is ${accountCreator.username}")

            waitForServerAnswer.value = true
            coreContext.core.addListener(coreListener)
            if (!createProxyConfig()) {
                waitForServerAnswer.value = false
                coreContext.core.removeListener(coreListener)
                // TODO: show error
            }
        } else {
            accountCreator.setPhoneNumber(phoneNumber.value, prefix.value)
            accountCreator.username = accountCreator.phoneNumber
            Log.i("[Assistant] [Account Login] Phone number is ${accountCreator.phoneNumber}")

            waitForServerAnswer.value = true
            val status = accountCreator.recoverAccount()
            Log.i("[Assistant] [Account Login] Recover account returned $status")
            if (status != AccountCreator.Status.RequestOk) {
                waitForServerAnswer.value = false
                // TODO: show error
            }
        }
    }

    private fun isLoginButtonEnabled(): Boolean {
        return if (loginWithUsernamePassword.value == true) {
            username.value.orEmpty().isNotEmpty() && password.value.orEmpty().isNotEmpty()
        } else {
            isPhoneNumberOk()
        }
    }

    private fun createProxyConfig(): Boolean {
        val proxyConfig: ProxyConfig? = accountCreator.createProxyConfig()
        proxyConfigToCheck = proxyConfig

        if (proxyConfig == null) {
            Log.e("[Assistant] [Account Login] Account creator couldn't create proxy config")
            // TODO: show error
            return false
        }

        proxyConfig.isPushNotificationAllowed = true

        Log.i("[Assistant] [Account Login] Proxy config created")
        return true
    }
}

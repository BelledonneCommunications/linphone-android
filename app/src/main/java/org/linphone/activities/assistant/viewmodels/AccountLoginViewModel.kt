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
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.PhoneNumberUtils

class AccountLoginViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AccountLoginViewModel(accountCreator) as T
    }
}

class AccountLoginViewModel(accountCreator: AccountCreator) : AbstractPhoneViewModel(accountCreator) {
    val loginWithUsernamePassword = MutableLiveData<Boolean>()

    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val loginEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val displayName = MutableLiveData<String>()

    val leaveAssistantEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val invalidCredentialsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToSmsValidationEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
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
                onErrorEvent.value = Event("Error: ${status.name}")
            }
        }
    }

    private var proxyConfigToCheck: ProxyConfig? = null

    private val coreListener = object : CoreListenerStub() {
        @Deprecated("Deprecated in Java")
        override fun onRegistrationStateChanged(
            core: Core,
            cfg: ProxyConfig,
            state: RegistrationState,
            message: String
        ) {
            if (cfg == proxyConfigToCheck) {
                Log.i("[Assistant] [Account Login] Registration state is $state: $message")
                if (state == RegistrationState.Ok) {
                    waitForServerAnswer.value = false
                    leaveAssistantEvent.value = Event(true)
                    core.removeListener(this)
                } else if (state == RegistrationState.Failed) {
                    waitForServerAnswer.value = false
                    invalidCredentialsEvent.value = Event(true)
                    core.removeListener(this)
                }
            }
        }
    }

    init {
        accountCreator.addListener(listener)

        loginWithUsernamePassword.value = coreContext.context.resources.getBoolean(R.bool.isTablet)

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
        accountCreator.displayName = displayName.value

        if (loginWithUsernamePassword.value == true) {
            val result = accountCreator.setUsername(username.value)
            if (result != AccountCreator.UsernameStatus.Ok) {
                Log.e("[Assistant] [Account Login] Error [${result.name}] setting the username: ${username.value}")
                usernameError.value = result.name
                return
            }
            Log.i("[Assistant] [Account Login] Username is ${accountCreator.username}")

            val result2 = accountCreator.setPassword(password.value)
            if (result2 != AccountCreator.PasswordStatus.Ok) {
                Log.e("[Assistant] [Account Login] Error [${result2.name}] setting the password")
                passwordError.value = result2.name
                return
            }

            waitForServerAnswer.value = true
            coreContext.core.addListener(coreListener)
            if (!createProxyConfig()) {
                waitForServerAnswer.value = false
                coreContext.core.removeListener(coreListener)
                onErrorEvent.value = Event("Error: Failed to create account object")
            }
        } else {
            val result = AccountCreator.PhoneNumberStatus.fromInt(accountCreator.setPhoneNumber(phoneNumber.value, prefix.value))
            if (result != AccountCreator.PhoneNumberStatus.Ok) {
                Log.e("[Assistant] [Account Login] Error [$result] setting the phone number: ${phoneNumber.value} with prefix: ${prefix.value}")
                phoneNumberError.value = result.name
                return
            }
            Log.i("[Assistant] [Account Login] Phone number is ${accountCreator.phoneNumber}")

            val result2 = accountCreator.setUsername(accountCreator.phoneNumber)
            if (result2 != AccountCreator.UsernameStatus.Ok) {
                Log.e("[Assistant] [Account Login] Error [${result2.name}] setting the username: ${accountCreator.phoneNumber}")
                usernameError.value = result2.name
                return
            }
            Log.i("[Assistant] [Account Login] Username is ${accountCreator.username}")

            waitForServerAnswer.value = true
            val status = accountCreator.recoverAccount()
            Log.i("[Assistant] [Account Login] Recover account returned $status")
            if (status != AccountCreator.Status.RequestOk) {
                waitForServerAnswer.value = false
                onErrorEvent.value = Event("Error: ${status.name}")
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
            onErrorEvent.value = Event("Error: Failed to create account object")
            return false
        }

        proxyConfig.isPushNotificationAllowed = true

        if (proxyConfig.dialPrefix.isNullOrEmpty()) {
            val dialPlan = PhoneNumberUtils.getDialPlanForCurrentCountry(coreContext.context)
            if (dialPlan != null) {
                Log.i("[Assistant] [Account Login] Found dial plan country ${dialPlan.country} with international prefix ${dialPlan.countryCallingCode}")
                proxyConfig.edit()
                proxyConfig.dialPrefix = dialPlan.countryCallingCode
                proxyConfig.done()
            } else {
                Log.w("[Assistant] [Account Login] Failed to find dial plan")
            }
        }

        Log.i("[Assistant] [Account Login] Proxy config created")
        return true
    }
}

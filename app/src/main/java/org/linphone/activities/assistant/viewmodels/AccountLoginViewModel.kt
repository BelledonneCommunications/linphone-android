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

import android.content.pm.PackageManager
import androidx.lifecycle.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
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

    val loginEnabled = MediatorLiveData<Boolean>()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val displayName = MutableLiveData<String>()

    val forceLoginUsingUsernameAndPassword = MutableLiveData<Boolean>()

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

    private var accountToCheck: Account? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (account == accountToCheck) {
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

        val pushAvailable = LinphoneUtils.isPushNotificationAvailable()
        val deviceHasTelephonyFeature = coreContext.context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY
        )
        loginWithUsernamePassword.value = !deviceHasTelephonyFeature || !pushAvailable
        forceLoginUsingUsernameAndPassword.value = !pushAvailable

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
        loginEnabled.addSource(prefixError) {
            loginEnabled.value = isLoginButtonEnabled()
        }
    }

    override fun onCleared() {
        accountCreator.removeListener(listener)
        super.onCleared()
    }

    override fun onFlexiApiTokenReceived() {
        Log.i("[Assistant] [Account Login] Using FlexiAPI auth token [${accountCreator.token}]")
        waitForServerAnswer.value = false
        loginWithPhoneNumber()
    }

    override fun onFlexiApiTokenRequestError() {
        Log.e("[Assistant] [Account Login] Failed to get an auth token from FlexiAPI")
        waitForServerAnswer.value = false
        onErrorEvent.value = Event("Error: Failed to get an auth token from account manager server")
    }

    fun removeInvalidProxyConfig() {
        val account = accountToCheck
        account ?: return

        val core = coreContext.core
        val authInfo = account.findAuthInfo()
        if (authInfo != null) core.removeAuthInfo(authInfo)
        core.removeAccount(account)
        accountToCheck = null

        // Make sure there is a valid default account
        val accounts = core.accountList
        if (accounts.isNotEmpty() && core.defaultAccount == null) {
            core.defaultAccount = accounts.first()
            core.refreshRegisters()
        }
    }

    fun continueEvenIfInvalidCredentials() {
        leaveAssistantEvent.value = Event(true)
    }

    private fun loginWithUsername() {
        val result = accountCreator.setUsername(username.value)
        if (result != AccountCreator.UsernameStatus.Ok) {
            Log.e(
                "[Assistant] [Account Login] Error [${result.name}] setting the username: ${username.value}"
            )
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
        if (!createAccountAndAuthInfo()) {
            waitForServerAnswer.value = false
            coreContext.core.removeListener(coreListener)
            onErrorEvent.value = Event("Error: Failed to create account object")
        }
    }

    private fun loginWithPhoneNumber() {
        val result = AccountCreator.PhoneNumberStatus.fromInt(
            accountCreator.setPhoneNumber(phoneNumber.value, prefix.value)
        )
        if (result != AccountCreator.PhoneNumberStatus.Ok) {
            Log.e(
                "[Assistant] [Account Login] Error [$result] setting the phone number: ${phoneNumber.value} with prefix: ${prefix.value}"
            )
            phoneNumberError.value = result.name
            return
        }
        Log.i("[Assistant] [Account Login] Phone number is ${accountCreator.phoneNumber}")

        val result2 = accountCreator.setUsername(accountCreator.phoneNumber)
        if (result2 != AccountCreator.UsernameStatus.Ok) {
            Log.e(
                "[Assistant] [Account Login] Error [${result2.name}] setting the username: ${accountCreator.phoneNumber}"
            )
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

    fun login() {
        accountCreator.displayName = displayName.value

        if (loginWithUsernamePassword.value == true) {
            loginWithUsername()
        } else {
            val token = accountCreator.token.orEmpty()
            if (token.isNotEmpty()) {
                Log.i(
                    "[Assistant] [Account Login] We already have an auth token from FlexiAPI [$token], continue"
                )
                onFlexiApiTokenReceived()
            } else {
                Log.i("[Assistant] [Account Login] Requesting an auth token from FlexiAPI")
                waitForServerAnswer.value = true
                requestFlexiApiToken()
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

    private fun createAccountAndAuthInfo(): Boolean {
        val account = accountCreator.createAccountInCore()
        accountToCheck = account

        if (account == null) {
            Log.e("[Assistant] [Account Login] Account creator couldn't create account")
            onErrorEvent.value = Event("Error: Failed to create account object")
            return false
        }

        val params = account.params.clone()
        params.pushNotificationAllowed = true

        if (params.internationalPrefix.isNullOrEmpty()) {
            val dialPlan = PhoneNumberUtils.getDialPlanForCurrentCountry(coreContext.context)
            if (dialPlan != null) {
                Log.i(
                    "[Assistant] [Account Login] Found dial plan country ${dialPlan.country} with international prefix ${dialPlan.countryCallingCode}"
                )
                params.internationalPrefix = dialPlan.countryCallingCode
            } else {
                Log.w("[Assistant] [Account Login] Failed to find dial plan")
            }
        }

        account.params = params
        Log.i("[Assistant] [Account Login] Account created")
        return true
    }
}

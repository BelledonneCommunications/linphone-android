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
import androidx.annotation.WorkerThread
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
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class ThirdPartySipAccountLoginViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Third Party SIP Account Login ViewModel]"

        private const val UDP = "UDP"
        private const val TCP = "TCP"
        private const val TLS = "TLS"
    }

    val username = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val domain = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val transport = MutableLiveData<String>()

    val internationalPrefix = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val loginEnabled = MediatorLiveData<Boolean>()

    val registrationInProgress = MutableLiveData<Boolean>()

    val accountLoggedInEvent = MutableLiveData<Event<Boolean>>()

    val accountLoginErrorEvent = MutableLiveData<Event<String>>()

    val availableTransports = arrayListOf<String>()

    private lateinit var newlyCreatedAuthInfo: AuthInfo
    private lateinit var newlyCreatedAccount: Account

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (account == newlyCreatedAccount) {
                Log.i("$TAG Newly created account registration state is [$state] ($message)")

                if (state == RegistrationState.Ok) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)

                    // Set new account as default
                    core.defaultAccount = newlyCreatedAccount
                    accountLoggedInEvent.postValue(Event(true))
                } else if (state == RegistrationState.Failed) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)
                    // TODO: show translated string
                    accountLoginErrorEvent.postValue(Event(message))

                    Log.e("$TAG Account failed to REGISTER [$message], removing it")
                    core.removeAuthInfo(newlyCreatedAuthInfo)
                    core.removeAccount(newlyCreatedAccount)
                }
            }
        }
    }

    init {
        showPassword.value = false
        registrationInProgress.value = false

        loginEnabled.addSource(username) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(domain) {
            loginEnabled.value = isLoginButtonEnabled()
        }

        // TODO: handle formatting errors

        availableTransports.add(UDP)
        availableTransports.add(TCP)
        availableTransports.add(TLS)
        transport.value = TLS
    }

    @UiThread
    fun login() {
        coreContext.postOnCoreThread { core ->
            core.loadConfigFromXml(corePreferences.thirdPartyDefaultValuesPath)

            val user = username.value.orEmpty().trim()
            val domainValue = domain.value.orEmpty().trim()

            newlyCreatedAuthInfo = Factory.instance().createAuthInfo(
                user,
                null,
                password.value.orEmpty().trim(),
                null,
                null,
                domainValue
            )
            core.addAuthInfo(newlyCreatedAuthInfo)

            val accountParams = core.createAccountParams()

            val identityAddress = Factory.instance().createAddress("sip:$user@$domainValue")
            if (displayName.value.orEmpty().isNotEmpty()) {
                identityAddress?.displayName = displayName.value.orEmpty().trim()
            }
            accountParams.identityAddress = identityAddress

            val serverAddress = Factory.instance().createAddress("sip:$domainValue")
            serverAddress?.transport = when (transport.value.orEmpty().trim()) {
                TCP -> TransportType.Tcp
                TLS -> TransportType.Tls
                else -> TransportType.Udp
            }
            accountParams.serverAddress = serverAddress

            val prefix = internationalPrefix.value.orEmpty().trim()
            if (prefix.isNotEmpty()) {
                val prefixDigits = if (prefix.startsWith("+")) {
                    prefix.substring(1)
                } else {
                    prefix
                }
                if (prefixDigits.isNotEmpty()) {
                    Log.i("$TAG Setting international prefix [$prefixDigits] in account params")
                    accountParams.internationalPrefix = prefixDigits
                }
            }

            newlyCreatedAccount = core.createAccount(accountParams)

            registrationInProgress.postValue(true)
            core.addListener(coreListener)
            core.addAccount(newlyCreatedAccount)
        }
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    private fun isLoginButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() && password.value.orEmpty().isNotEmpty() && domain.value.orEmpty().isNotEmpty()
    }
}

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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

open class AccountLoginViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Account Login ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val hideCreateAccount = MutableLiveData<Boolean>()

    val hideScanQrCode = MutableLiveData<Boolean>()

    val hideThirdPartyAccount = MutableLiveData<Boolean>()

    val sipIdentity = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val internationalPrefix = MutableLiveData<String>()

    val internationalPrefixIsoCountryCode = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val loginEnabled = MediatorLiveData<Boolean>()

    val registrationInProgress = MutableLiveData<Boolean>()

    val accountLoggedInEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val accountLoginErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val skipLandingToThirdPartySipAccountEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var conditionsAndPrivacyPolicyAccepted = false

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
                    accountLoggedInEvent.postValue(Event(core.accountList.size == 1))
                } else if (state == RegistrationState.Failed) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)

                    val error = when (account.error) {
                        Reason.Forbidden -> {
                            AppUtils.getString(R.string.assistant_account_login_forbidden_error)
                        }
                        else -> {
                            AppUtils.getFormattedString(
                                R.string.assistant_account_login_error,
                                account.error.toString()
                            )
                        }
                    }
                    accountLoginErrorEvent.postValue(Event(error))

                    Log.e("$TAG Account failed to REGISTER [$message], removing it")
                    core.removeAuthInfo(newlyCreatedAuthInfo)
                    core.removeAccount(newlyCreatedAccount)
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            // Prevent user from leaving assistant if no account was configured yet
            showBackButton.postValue(core.accountList.isNotEmpty())
            hideCreateAccount.postValue(corePreferences.hideAssistantCreateAccount)
            hideScanQrCode.postValue(corePreferences.hideAssistantScanQrCode)
            hideThirdPartyAccount.postValue(corePreferences.hideAssistantThirdPartySipAccount)
            conditionsAndPrivacyPolicyAccepted = corePreferences.conditionsAndPrivacyPolicyAccepted

            if (corePreferences.assistantDirectlyGoToThirdPartySipAccountLogin) {
                skipLandingToThirdPartySipAccountEvent.postValue(Event(true))
            }
        }

        showPassword.value = false
        registrationInProgress.value = false

        loginEnabled.addSource(sipIdentity) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }
    }

    @UiThread
    fun login() {
        coreContext.postOnCoreThread { core ->
            core.loadConfigFromXml(corePreferences.linphoneDefaultValuesPath)

            val userInput = sipIdentity.value.orEmpty().trim()
            val defaultDomain = corePreferences.defaultDomain
            val identity = if (userInput.startsWith("sip:")) {
                if (userInput.contains("@")) {
                    userInput
                } else {
                    "$userInput@$defaultDomain"
                }
            } else {
                if (userInput.contains("@")) {
                    "sip:$userInput"
                } else {
                    "sip:$userInput@$defaultDomain"
                }
            }
            Log.i("$TAG Computed identity is [$identity] from user input [$userInput]")

            val identityAddress = Factory.instance().createAddress(identity)
            if (identityAddress == null) {
                Log.e("$TAG Can't parse [$identity] as Address!")
                showRedToast(R.string.assistant_login_cant_parse_address_toast, R.drawable.warning_circle)
                return@postOnCoreThread
            }

            val accounts = core.accountList
            val found = accounts.find {
                it.params.identityAddress?.weakEqual(identityAddress) == true
            }
            if (found != null) {
                Log.w("$TAG An account with the same identity address [${identityAddress.asStringUriOnly()}] already exists, do not add it again!")
                showRedToast(R.string.assistant_account_login_already_connected_error, R.drawable.warning_circle)
                return@postOnCoreThread
            }

            val user = identityAddress.username
            if (user == null) {
                Log.e(
                    "$TAG Address [${identityAddress.asStringUriOnly()}] doesn't contains an username!"
                )
                showRedToast(R.string.assistant_login_address_without_username_toast, R.drawable.warning_circle)
                return@postOnCoreThread
            }

            val domain = identityAddress.domain

            newlyCreatedAuthInfo = Factory.instance().createAuthInfo(
                user,
                null,
                password.value.orEmpty().trim(),
                null,
                null,
                domain
            )
            core.addAuthInfo(newlyCreatedAuthInfo)

            val accountParams = core.createAccountParams()
            accountParams.identityAddress = identityAddress

            val prefix = internationalPrefix.value.orEmpty().trim()
            val isoCountryCode = internationalPrefixIsoCountryCode.value.orEmpty()
            if (prefix.isNotEmpty()) {
                val prefixDigits = if (prefix.startsWith("+")) {
                    prefix.substring(1)
                } else {
                    prefix
                }
                if (prefixDigits.isNotEmpty()) {
                    Log.i(
                        "$TAG Setting international prefix [$prefixDigits]($isoCountryCode) in account params"
                    )
                    accountParams.internationalPrefix = prefixDigits
                    accountParams.internationalPrefixIsoCountryCode = isoCountryCode
                }
            }

            newlyCreatedAccount = core.createAccount(accountParams)

            registrationInProgress.postValue(true)
            core.addListener(coreListener)
            Log.i(
                "$TAG Trying to log in account with SIP identity [${identityAddress.asStringUriOnly()}]"
            )
            core.addAccount(newlyCreatedAccount)
        }
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    private fun isLoginButtonEnabled(): Boolean {
        return sipIdentity.value.orEmpty().trim().isNotEmpty() && password.value.orEmpty().isNotEmpty()
    }
}

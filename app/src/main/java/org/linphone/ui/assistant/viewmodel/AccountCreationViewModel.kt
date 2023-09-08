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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.AccountCreator
import org.linphone.core.AccountCreatorListenerStub
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.DialPlan
import org.linphone.core.tools.Log
import org.linphone.ui.assistant.fragment.CountryPickerFragment
import org.linphone.utils.Event

class AccountCreationViewModel @UiThread constructor() : ViewModel(), CountryPickerFragment.CountryPickedListener {
    companion object {
        private const val TAG = "[Account Creation ViewModel]"
    }

    val username = MutableLiveData<String>()

    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val phoneNumber = MutableLiveData<String>()

    val phoneNumberError = MutableLiveData<String>()

    val internationalPrefix = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val createEnabled = MediatorLiveData<Boolean>()

    val smsCodeFirstDigit = MutableLiveData<String>()
    val smsCodeSecondDigit = MutableLiveData<String>()
    val smsCodeThirdDigit = MutableLiveData<String>()
    val smsCodeLastDigit = MutableLiveData<String>()

    val operationInProgress = MutableLiveData<Boolean>()

    val normalizedPhoneNumberEvent = MutableLiveData<Event<String>>()

    val goToSmsCodeConfirmationViewEvent = MutableLiveData<Event<Boolean>>()

    val goToLoginPageEvent = MutableLiveData<Event<Boolean>>()

    private var waitingForFlexiApiPushToken = false
    private var waitForPushJob: Job? = null

    private lateinit var accountCreator: AccountCreator

    private val accountCreatorListener = object : AccountCreatorListenerStub() {
        @WorkerThread
        override fun onIsAccountExist(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("$TAG onIsAccountExist status [$status] ($response)")

            when (status) {
                AccountCreator.Status.AccountExist, AccountCreator.Status.AccountExistWithAlias -> {
                    operationInProgress.postValue(false)
                    createEnabled.postValue(false)
                    usernameError.postValue("Account already exists")
                }
                AccountCreator.Status.AccountNotExist -> {
                    operationInProgress.postValue(false)
                    checkPhoneNumber()
                }
                else -> {
                    operationInProgress.postValue(false)
                    createEnabled.postValue(false)
                    phoneNumberError.postValue(status.name)
                }
            }
        }

        @WorkerThread
        override fun onIsAliasUsed(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("$TAG onIsAliasUsed status [$status] ($response)")
            when (status) {
                AccountCreator.Status.AliasExist, AccountCreator.Status.AliasIsAccount -> {
                    operationInProgress.postValue(false)
                    createEnabled.postValue(false)
                    phoneNumberError.postValue("Phone number already used")
                }
                AccountCreator.Status.AliasNotExist -> {
                    operationInProgress.postValue(false)
                    createAccount()
                }
                else -> {
                    operationInProgress.postValue(false)
                    createEnabled.postValue(false)
                    phoneNumberError.postValue(status.name)
                }
            }
        }

        @WorkerThread
        override fun onCreateAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("$TAG onCreateAccount status [$status] ($response)")
            accountCreator.token = null
            operationInProgress.postValue(false)

            when (status) {
                AccountCreator.Status.AccountCreated -> {
                    goToSmsCodeConfirmationViewEvent.postValue(Event(true))
                }
                else -> {
                    // TODO
                }
            }
        }

        @WorkerThread
        override fun onActivateAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("$TAG onActivateAccount status [$status] ($response)")
            operationInProgress.postValue(false)

            if (status == AccountCreator.Status.AccountActivated) {
                Log.i("$TAG Account has been successfully activated, going to login page")
                goToLoginPageEvent.postValue(Event(true))
            } else {
                // TODO
            }
        }
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onPushNotificationReceived(core: Core, payload: String?) {
            Log.i("$TAG Push received: [$payload]")

            val data = payload.orEmpty()
            if (data.isNotEmpty()) {
                try {
                    // This is because JSONObject.toString() done by the SDK will result in payload looking like {"custom-payload":"{\"token\":\"value\"}"}
                    val cleanPayload = data
                        .replace("\\\"", "\"")
                        .replace("\"{", "{")
                        .replace("}\"", "}")
                    Log.i("$TAG Cleaned payload is: [$cleanPayload]")

                    val json = JSONObject(cleanPayload)
                    val customPayload = json.getJSONObject("custom-payload")
                    if (customPayload.has("token")) {
                        waitForPushJob?.cancel()
                        waitingForFlexiApiPushToken = false
                        operationInProgress.postValue(false)

                        val token = customPayload.getString("token")
                        if (token.isNotEmpty()) {
                            Log.i("$TAG Extracted token [$token] from push payload")
                            accountCreator.token = token
                            checkUsername()
                        } else {
                            Log.e("$TAG Push payload JSON object has an empty 'token'!")
                            onFlexiApiTokenRequestError()
                        }
                    } else {
                        Log.e("$TAG Push payload JSON object has no 'token' key!")
                        onFlexiApiTokenRequestError()
                    }
                } catch (e: JSONException) {
                    Log.e("$TAG Exception trying to parse push payload as JSON: [$e]")
                    onFlexiApiTokenRequestError()
                }
            } else {
                Log.e("$TAG Push payload is null or empty, can't extract auth token!")
                onFlexiApiTokenRequestError()
            }
        }
    }

    init {
        internationalPrefix.value = "+1"

        coreContext.postOnCoreThread { core ->
            accountCreator = core.createAccountCreator(core.accountCreatorUrl)
            accountCreator.addListener(accountCreatorListener)
            core.addListener(coreListener)
        }

        showPassword.value = false

        createEnabled.addSource(username) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(password) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(internationalPrefix) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(phoneNumber) {
            createEnabled.value = isCreateButtonEnabled()
        }
    }

    @UiThread
    override fun onCountryClicked(dialPlan: DialPlan) {
        internationalPrefix.value = "+${dialPlan.countryCallingCode}"
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            if (::accountCreator.isInitialized) {
                accountCreator.removeListener(accountCreatorListener)
            }
            core.removeListener(coreListener)
        }
        waitForPushJob?.cancel()

        super.onCleared()
    }

    @UiThread
    fun confirmPhoneNumber() {
        coreContext.postOnCoreThread {
            if (::accountCreator.isInitialized) {
                val prefix = internationalPrefix.value.orEmpty().trim()
                val digitsPrefix = if (prefix.startsWith("+")) {
                    prefix.substring(1)
                } else {
                    prefix
                }
                val number = phoneNumber.value.orEmpty().trim()
                accountCreator.setPhoneNumber(number, digitsPrefix)

                val normalizedPhoneNumber = accountCreator.phoneNumber
                if (!normalizedPhoneNumber.isNullOrEmpty()) {
                    normalizedPhoneNumberEvent.postValue(Event(normalizedPhoneNumber))
                } else {
                    Log.e(
                        "$TAG Failed to compute phone number using international prefix [$digitsPrefix] and number [$number]"
                    )
                    operationInProgress.postValue(false)
                    phoneNumberError.postValue("Wrong international prefix / local phone number")
                }
            } else {
                Log.e("$TAG Account creator hasn't been initialized!")
            }
        }
    }

    @UiThread
    fun requestToken() {
        coreContext.postOnCoreThread {
            if (accountCreator.token == null) {
                Log.i("$TAG We don't have a creation token, let's request one")
                requestFlexiApiToken()
            } else {
                Log.i("$TAG We've already have a token [${accountCreator.token}], continuing")
                checkUsername()
            }
        }
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    private fun isCreateButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() && password.value.orEmpty().isNotEmpty() && phoneNumber.value.orEmpty().isNotEmpty() && internationalPrefix.value.orEmpty().isNotEmpty()
    }

    @UiThread
    fun validateCode() {
        operationInProgress.value = true
        val code = "${smsCodeFirstDigit.value}${smsCodeSecondDigit.value}${smsCodeThirdDigit.value}${smsCodeLastDigit.value}"
        Log.i("$TAG Activating account using code [$code]")
        accountCreator.activationCode = code

        coreContext.postOnCoreThread {
            val status = accountCreator.activateAccount()
            Log.i("$TAG activateAccount returned $status")
            if (status != AccountCreator.Status.RequestOk) {
                Log.e("$TAG Can't activate account [$status]")
                operationInProgress.postValue(false)
            }
        }
    }

    @WorkerThread
    private fun checkUsername() {
        usernameError.postValue("")
        accountCreator.username = username.value.orEmpty().trim()
        accountCreator.domain = corePreferences.defaultDomain

        operationInProgress.postValue(true)
        val status = accountCreator.isAccountExist
        Log.i("$TAG isAccountExist for username [${accountCreator.username}] returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            Log.e("$TAG Can't check if account already exists [$status]")
            operationInProgress.postValue(false)
        }
    }

    @WorkerThread
    private fun checkPhoneNumber() {
        operationInProgress.postValue(true)

        val status = accountCreator.isAliasUsed
        Log.i("$TAG isAliasUsed returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            Log.e("$TAG Can't check if phone number is already used [$status]")
            operationInProgress.postValue(false)
        }
    }

    @WorkerThread
    private fun createAccount() {
        operationInProgress.postValue(true)
        accountCreator.password = password.value.orEmpty().trim()
        val status = accountCreator.createAccount()

        Log.i("$TAG createAccount returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            Log.e("$TAG Can't create account [$status]")
            operationInProgress.postValue(false)
        } else {
            Log.i("$TAG createAccount consumed our token, setting it to null")
            accountCreator.token = null
        }
    }

    @WorkerThread
    private fun requestFlexiApiToken() {
        if (!coreContext.core.isPushNotificationAvailable) {
            Log.e(
                "$TAG Core says push notification aren't available, can't request a token from FlexiAPI"
            )
            onFlexiApiTokenRequestError()
            return
        }

        val pushConfig = coreContext.core.pushNotificationConfig
        if (pushConfig != null) {
            Log.i(
                "$TAG Found push notification info: provider [${pushConfig.provider}], param [${pushConfig.param}] and prid [${pushConfig.prid}]"
            )
            accountCreator.pnProvider = pushConfig.provider
            accountCreator.pnParam = pushConfig.param
            accountCreator.pnPrid = pushConfig.prid

            // Request an auth token, will be sent by push
            val result = accountCreator.requestAuthToken()
            if (result == AccountCreator.Status.RequestOk) {
                val waitFor = 5000
                waitingForFlexiApiPushToken = true
                waitForPushJob?.cancel()

                Log.i("$TAG Waiting push with auth token for $waitFor ms")
                waitForPushJob = viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        delay(waitFor.toLong())
                    }
                    withContext(Dispatchers.Main) {
                        if (waitingForFlexiApiPushToken) {
                            waitingForFlexiApiPushToken = false
                            Log.e("$TAG Auth token wasn't received by push in $waitFor ms")
                            onFlexiApiTokenRequestError()
                        }
                    }
                }
            } else {
                Log.e("$TAG Failed to require a push with an auth token: [$result]")
                onFlexiApiTokenRequestError()
            }
        } else {
            Log.e("$TAG No push configuration object in Core, shouldn't happen!")
            onFlexiApiTokenRequestError()
        }
    }

    @WorkerThread
    private fun onFlexiApiTokenRequestError() {
        Log.e("$TAG Flexi API token request by push error!")
        operationInProgress.postValue(false)
    }
}

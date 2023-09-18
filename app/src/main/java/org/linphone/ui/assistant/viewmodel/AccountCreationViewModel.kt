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
import org.linphone.R
import org.linphone.core.AccountCreator
import org.linphone.core.AccountCreator.PhoneNumberStatus
import org.linphone.core.AccountCreator.UsernameStatus
import org.linphone.core.AccountCreatorListenerStub
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.DialPlan
import org.linphone.core.tools.Log
import org.linphone.ui.assistant.fragment.CountryPickerFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class AccountCreationViewModel @UiThread constructor() : ViewModel(), CountryPickerFragment.CountryPickedListener {
    companion object {
        private const val TAG = "[Account Creation ViewModel]"
    }

    val username = MutableLiveData<String>()

    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val passwordError = MutableLiveData<String>()

    val phoneNumber = MutableLiveData<String>()

    val phoneNumberError = MutableLiveData<String>()

    val internationalPrefix = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val createEnabled = MediatorLiveData<Boolean>()

    val confirmationMessage = MutableLiveData<String>()

    val smsCodeFirstDigit = MutableLiveData<String>()
    val smsCodeSecondDigit = MutableLiveData<String>()
    val smsCodeThirdDigit = MutableLiveData<String>()
    val smsCodeLastDigit = MutableLiveData<String>()

    val operationInProgress = MutableLiveData<Boolean>()

    val normalizedPhoneNumberEvent = MutableLiveData<Event<String>>()

    val goToSmsCodeConfirmationViewEvent = MutableLiveData<Event<Boolean>>()

    val goToLoginPageEvent = MutableLiveData<Event<Boolean>>()

    val errorHappenedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

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

                    val error = AppUtils.getString(
                        R.string.assistant_account_register_username_already_in_use_error
                    )
                    usernameError.postValue(error)
                }
                AccountCreator.Status.AccountNotExist -> {
                    operationInProgress.postValue(false)
                    checkPhoneNumber()
                }
                else -> {
                    Log.e("$TAG An unexpected error occurred!")
                    operationInProgress.postValue(false)
                    createEnabled.postValue(false)

                    phoneNumberError.postValue(
                        AppUtils.getString(
                            R.string.assistant_account_register_invalid_phone_number_error
                        )
                    )
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

                    val error = AppUtils.getString(
                        R.string.assistant_account_register_phone_number_already_in_use_error
                    )
                    phoneNumberError.postValue(error)
                }
                AccountCreator.Status.AliasNotExist -> {
                    operationInProgress.postValue(false)
                    createAccount()
                }
                else -> {
                    Log.e("$TAG An unexpected error occurred!")
                    operationInProgress.postValue(false)
                    createEnabled.postValue(false)

                    phoneNumberError.postValue(
                        AppUtils.getString(
                            R.string.assistant_account_register_invalid_phone_number_error
                        )
                    )
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
                    Log.e("$TAG Account couldn't be created, an unexpected error occurred!")
                    errorHappenedEvent.postValue(
                        Event(
                            AppUtils.getFormattedString(
                                R.string.assistant_account_register_server_error,
                                status.toInt()
                            )
                        )
                    )
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
                Log.e("$TAG Account couldn't be activated, an unexpected error occurred!")
                errorHappenedEvent.postValue(
                    Event(
                        AppUtils.getFormattedString(
                            R.string.assistant_account_register_server_error,
                            status.toInt()
                        )
                    )
                )
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
        operationInProgress.value = false

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
        operationInProgress.value = true
        coreContext.postOnCoreThread {
            if (::accountCreator.isInitialized) {
                val prefix = internationalPrefix.value.orEmpty().trim()
                val digitsPrefix = if (prefix.startsWith("+")) {
                    prefix.substring(1)
                } else {
                    prefix
                }
                val number = phoneNumber.value.orEmpty().trim()

                val status = accountCreator.setPhoneNumber(number, digitsPrefix)
                Log.i("$TAG setPhoneNumber returned $status")
                if (status == PhoneNumberStatus.Ok.toInt()) {
                    val normalizedPhoneNumber = accountCreator.phoneNumber

                    val message = coreContext.context.getString(
                        R.string.assistant_account_creation_sms_confirmation_explanation,
                        normalizedPhoneNumber
                    )
                    confirmationMessage.postValue(message)

                    Log.i(
                        "$TAG Normalized phone number from [$number] and prefix [$digitsPrefix] is [$normalizedPhoneNumber]"
                    )
                    if (!normalizedPhoneNumber.isNullOrEmpty()) {
                        normalizedPhoneNumberEvent.postValue(Event(normalizedPhoneNumber))
                    } else {
                        Log.e(
                            "$TAG Failed to compute phone number using international prefix [$digitsPrefix] and number [$number]"
                        )
                        operationInProgress.postValue(false)

                        val error = AppUtils.getString(
                            R.string.assistant_account_register_invalid_phone_number_error
                        )
                        phoneNumberError.postValue(error)
                    }
                } else {
                    Log.e(
                        "$TAG Failed to set phone number [$number] and prefix [$digitsPrefix] into account creator!"
                    )
                    val error = when (status) {
                        PhoneNumberStatus.Invalid.toInt() -> {
                            AppUtils.getString(
                                R.string.assistant_account_register_invalid_phone_number_error
                            )
                        }
                        PhoneNumberStatus.InvalidCountryCode.toInt() -> {
                            AppUtils.getString(
                                R.string.assistant_account_register_invalid_phone_number_international_prefix_error
                            )
                        }
                        PhoneNumberStatus.TooLong.toInt() -> {
                            AppUtils.getString(
                                R.string.assistant_account_register_invalid_phone_number_too_long_error
                            )
                        }
                        PhoneNumberStatus.TooShort.toInt() -> {
                            AppUtils.getString(
                                R.string.assistant_account_register_invalid_phone_number_too_short_error
                            )
                        }
                        else -> {
                            AppUtils.getString(
                                R.string.assistant_account_register_invalid_phone_number_error
                            )
                        }
                    }
                    phoneNumberError.postValue(error)
                    operationInProgress.postValue(false)
                }
            } else {
                Log.e("$TAG Account creator hasn't been initialized!")
                errorHappenedEvent.postValue(
                    Event(AppUtils.getString(R.string.assistant_account_register_unexpected_error))
                )
            }
        }
    }

    @UiThread
    fun requestToken() {
        operationInProgress.value = true

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
                errorHappenedEvent.postValue(
                    Event(
                        AppUtils.getFormattedString(
                            R.string.assistant_account_register_server_error,
                            status.toInt()
                        )
                    )
                )
            }
        }
    }

    @WorkerThread
    private fun checkUsername() {
        operationInProgress.postValue(true)

        usernameError.postValue("")
        val usernameStatus = accountCreator.setUsername(username.value.orEmpty().trim())
        Log.i("$TAG setUsername returned $usernameStatus")
        if (usernameStatus != UsernameStatus.Ok) {
            val error = when (usernameStatus) {
                UsernameStatus.InvalidCharacters, UsernameStatus.Invalid -> {
                    AppUtils.getString(
                        R.string.assistant_account_register_username_invalid_characters_error
                    )
                }
                UsernameStatus.TooShort -> {
                    AppUtils.getString(R.string.assistant_account_register_username_too_short_error)
                }
                UsernameStatus.TooLong -> {
                    AppUtils.getString(R.string.assistant_account_register_username_too_long_error)
                }
                else -> {
                    AppUtils.getString(R.string.assistant_account_register_username_error)
                }
            }
            usernameError.postValue(error)
            operationInProgress.postValue(false)
            return
        }

        accountCreator.domain = corePreferences.defaultDomain

        val status = accountCreator.isAccountExist
        Log.i("$TAG isAccountExist for username [${accountCreator.username}] returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            Log.e("$TAG Can't check if account already exists [$status]")
            operationInProgress.postValue(false)
            errorHappenedEvent.postValue(
                Event(
                    AppUtils.getFormattedString(
                        R.string.assistant_account_register_server_error,
                        status.toInt()
                    )
                )
            )
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
            errorHappenedEvent.postValue(
                Event(
                    AppUtils.getFormattedString(
                        R.string.assistant_account_register_server_error,
                        status.toInt()
                    )
                )
            )
        }
    }

    @WorkerThread
    private fun createAccount() {
        operationInProgress.postValue(true)

        val passwordStatus = accountCreator.setPassword(password.value.orEmpty().trim())
        Log.i("$TAG setPassword returned $passwordStatus")
        if (passwordStatus != AccountCreator.PasswordStatus.Ok) {
            val error = when (passwordStatus) {
                AccountCreator.PasswordStatus.InvalidCharacters -> {
                    AppUtils.getString(
                        R.string.assistant_account_register_password_invalid_characters_error
                    )
                }
                AccountCreator.PasswordStatus.TooShort -> {
                    AppUtils.getString(R.string.assistant_account_register_password_too_short)
                }
                AccountCreator.PasswordStatus.TooLong -> {
                    AppUtils.getString(R.string.assistant_account_register_password_too_long_error)
                }
                else -> {
                    AppUtils.getString(R.string.assistant_account_register_invalid_password_error)
                }
            }
            passwordError.postValue(error)
            operationInProgress.postValue(false)
        }

        val status = accountCreator.createAccount()
        Log.i("$TAG createAccount returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            Log.e("$TAG Can't create account [$status]")
            operationInProgress.postValue(false)
            errorHappenedEvent.postValue(
                Event(
                    AppUtils.getFormattedString(
                        R.string.assistant_account_register_server_error,
                        status.toInt()
                    )
                )
            )
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

        operationInProgress.postValue(true)

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
        errorHappenedEvent.postValue(
            Event(
                AppUtils.getString(
                    R.string.assistant_account_register_push_notification_not_received_error
                )
            )
        )
    }
}

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
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.AccountManagerServices
import org.linphone.core.AccountManagerServicesRequest
import org.linphone.core.AccountManagerServicesRequestListenerStub
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.DialPlan
import org.linphone.core.Dictionary
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class AccountCreationViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Account Creation ViewModel]"

        private const val TIME_TO_WAIT_FOR_PUSH_NOTIFICATION_WITH_ACCOUNT_CREATION_TOKEN = 5000
        private const val HASH_ALGORITHM = "SHA-256"
    }

    val username = MutableLiveData<String>()

    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val passwordError = MutableLiveData<String>()

    val phoneNumber = MutableLiveData<String>()

    val phoneNumberError = MutableLiveData<String>()

    val dialPlansLabelList = arrayListOf<String>()

    val dialPlansShortLabelList = arrayListOf<String>()

    val dialPlansList = arrayListOf<DialPlan>()

    val selectedDialPlan = MutableLiveData<DialPlan>()

    val showPassword = MutableLiveData<Boolean>()

    val createEnabled = MediatorLiveData<Boolean>()

    val pushNotificationsAvailable = MutableLiveData<Boolean>()

    val confirmationMessage = MutableLiveData<String>()

    val smsCodeFirstDigit = MutableLiveData<String>()
    val smsCodeSecondDigit = MutableLiveData<String>()
    val smsCodeThirdDigit = MutableLiveData<String>()
    val smsCodeLastDigit = MutableLiveData<String>()

    val operationInProgress = MutableLiveData<Boolean>()

    private var normalizedPhoneNumber: String? = null
    val normalizedPhoneNumberEvent = MutableLiveData<Event<String>>()

    val goToSmsCodeConfirmationViewEvent = MutableLiveData<Event<Boolean>>()

    val accountCreatedEvent = MutableLiveData<Event<Boolean>>()

    val errorHappenedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private var waitingForFlexiApiPushToken = false
    private var waitForPushJob: Job? = null

    private lateinit var accountManagerServices: AccountManagerServices
    private var accountCreationToken: String? = null

    private var accountCreatedAuthInfo: AuthInfo? = null
    private var accountCreated: Account? = null

    private val accountManagerServicesListener = object : AccountManagerServicesRequestListenerStub() {
        @WorkerThread
        override fun onRequestSuccessful(
            request: AccountManagerServicesRequest,
            data: String?
        ) {
            Log.i("$TAG Request [$request] was successful, data is [$data]")
            operationInProgress.postValue(false)

            when (request.type) {
                AccountManagerServicesRequest.Type.CreateAccountUsingToken -> {
                    if (!data.isNullOrEmpty()) {
                        storeAccountInCore(data)
                        sendCodeBySms()
                    } else {
                        Log.e(
                            "$TAG No data found for createAccountUsingToken request, can't continue!"
                        )
                    }
                }
                AccountManagerServicesRequest.Type.SendPhoneNumberLinkingCodeBySms -> {
                    goToSmsCodeConfirmationViewEvent.postValue(Event(true))
                }
                AccountManagerServicesRequest.Type.LinkPhoneNumberUsingCode -> {
                    val account = accountCreated
                    if (account != null) {
                        Log.i(
                            "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] has been created & activated, setting it as default"
                        )
                        coreContext.core.defaultAccount = account
                    }
                    accountCreatedEvent.postValue(Event(true))
                }
                else -> { }
            }
        }

        @WorkerThread
        override fun onRequestError(
            request: AccountManagerServicesRequest,
            statusCode: Int,
            errorMessage: String?,
            parameterErrors: Dictionary?
        ) {
            Log.e(
                "$TAG Request [$request] returned an error with status code [$statusCode] and message [$errorMessage]"
            )
            operationInProgress.postValue(false)

            if (!errorMessage.isNullOrEmpty()) {
                showFormattedRedToastEvent.postValue(
                    Event(
                        Pair(
                            errorMessage,
                            R.drawable.warning_circle
                        )
                    )
                )
            }

            for (parameter in parameterErrors?.keys.orEmpty()) {
                val parameterErrorMessage = parameterErrors?.getString(parameter) ?: ""
                when (parameter) {
                    "username" -> usernameError.postValue(parameterErrorMessage)
                    "password" -> passwordError.postValue(parameterErrorMessage)
                    "phone" -> phoneNumberError.postValue(parameterErrorMessage)
                }
            }

            when (request.type) {
                AccountManagerServicesRequest.Type.SendAccountCreationTokenByPush -> {
                    Log.w("$TAG Cancelling job waiting for push notification")
                    waitingForFlexiApiPushToken = false
                    waitForPushJob?.cancel()
                }
                AccountManagerServicesRequest.Type.SendPhoneNumberLinkingCodeBySms -> {
                    val authInfo = accountCreatedAuthInfo
                    if (authInfo != null) {
                        coreContext.core.removeAuthInfo(authInfo)
                    }
                    val account = accountCreated
                    if (account != null) {
                        coreContext.core.removeAccount(account)
                    }
                    createEnabled.postValue(true)
                }
                else -> {
                    createEnabled.postValue(true)
                }
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
                            accountCreationToken = token
                            Log.i(
                                "$TAG Extracted token [$accountCreationToken] from push payload, creating account"
                            )
                            createAccount()
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
        operationInProgress.value = false

        coreContext.postOnCoreThread { core ->
            pushNotificationsAvailable.postValue(LinphoneUtils.arePushNotificationsAvailable(core))

            val dialPlans = Factory.instance().dialPlans.toList()
            for (dialPlan in dialPlans) {
                dialPlansList.add(dialPlan)
                dialPlansLabelList.add(
                    "${dialPlan.flag} ${dialPlan.country} | +${dialPlan.countryCallingCode}"
                )
                dialPlansShortLabelList.add(
                    "${dialPlan.flag} +${dialPlan.countryCallingCode}"
                )
            }

            accountManagerServices = core.createAccountManagerServices()
            accountManagerServices.language = Locale.getDefault().language // Returns en, fr, etc...
            core.addListener(coreListener)
        }

        showPassword.value = false

        createEnabled.addSource(username) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(password) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(selectedDialPlan) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(phoneNumber) {
            createEnabled.value = isCreateButtonEnabled()
        }
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
        waitForPushJob?.cancel()

        super.onCleared()
    }

    @UiThread
    fun phoneNumberConfirmedByUser() {
        coreContext.postOnCoreThread {
            if (::accountManagerServices.isInitialized) {
                val dialPlan = selectedDialPlan.value
                if (dialPlan == null) {
                    Log.e("$TAG No dial plan (country) selected!")
                    return@postOnCoreThread
                }
                val number = phoneNumber.value.orEmpty().trim()
                val formattedPhoneNumber = dialPlan.formatPhoneNumber(number, false)
                Log.i(
                    "$TAG Formatted phone number [$number] using dial plan [${dialPlan.country}] is [$formattedPhoneNumber]"
                )

                val message = coreContext.context.getString(
                    R.string.assistant_account_creation_sms_confirmation_explanation,
                    formattedPhoneNumber
                )
                normalizedPhoneNumber = formattedPhoneNumber
                confirmationMessage.postValue(message)
                normalizedPhoneNumberEvent.postValue(Event(formattedPhoneNumber))
            } else {
                Log.e("$TAG Account manager services hasn't been initialized!")
                errorHappenedEvent.postValue(
                    Event(AppUtils.getString(R.string.assistant_account_register_unexpected_error))
                )
            }
        }
    }

    @UiThread
    fun startAccountCreation() {
        operationInProgress.value = true

        coreContext.postOnCoreThread {
            if (accountCreationToken.isNullOrEmpty()) {
                Log.i("$TAG We don't have a creation token, let's request one")
                requestFlexiApiToken()
            } else {
                val authInfo = accountCreatedAuthInfo
                if (authInfo != null) {
                    Log.i("$TAG Account has already been created, requesting SMS to be sent")
                    sendCodeBySms()
                } else {
                    Log.i("$TAG We've already have a token [$accountCreationToken], continuing")
                    createAccount()
                }
            }
        }
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    private fun isCreateButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() && password.value.orEmpty().isNotEmpty() && phoneNumber.value.orEmpty().isNotEmpty() && selectedDialPlan.value?.countryCallingCode.orEmpty().isNotEmpty()
    }

    @UiThread
    fun validateCode() {
        usernameError.postValue("")
        passwordError.postValue("")
        phoneNumberError.postValue("")
        operationInProgress.value = true

        val account = accountCreated
        if (::accountManagerServices.isInitialized && account != null) {
            val code =
                "${smsCodeFirstDigit.value}${smsCodeSecondDigit.value}${smsCodeThirdDigit.value}${smsCodeLastDigit.value}"
            val identity = account.params.identityAddress
            if (identity != null) {
                Log.i(
                    "$TAG Activating account using code [$code] for account [${identity.asStringUriOnly()}]"
                )
                val request = accountManagerServices.createLinkPhoneNumberToAccountUsingCodeRequest(
                    identity,
                    code
                )
                request.addListener(accountManagerServicesListener)
                request.submit()

                // Reset code
                smsCodeFirstDigit.postValue("")
                smsCodeSecondDigit.postValue("")
                smsCodeThirdDigit.postValue("")
                smsCodeLastDigit.postValue("")
            }
        }
    }

    @WorkerThread
    private fun sendCodeBySms() {
        usernameError.postValue("")
        passwordError.postValue("")
        phoneNumberError.postValue("")

        val account = accountCreated
        if (::accountManagerServices.isInitialized && account != null) {
            val phoneNumberValue = normalizedPhoneNumber
            if (phoneNumberValue.isNullOrEmpty()) {
                Log.e("$TAG Phone number is null or empty, this shouldn't happen at this step!")
                return
            }

            operationInProgress.postValue(true)
            createEnabled.postValue(false)

            val identity = account.params.identityAddress
            if (identity != null) {
                Log.i(
                    "$TAG Account [${identity.asStringUriOnly()}] should now be created, asking account manager to send a confirmation code by SMS to [$phoneNumberValue]"
                )
                val request = accountManagerServices.createSendPhoneNumberLinkingCodeBySmsRequest(
                    identity,
                    phoneNumberValue
                )
                request.addListener(accountManagerServicesListener)
                request.submit()
            }
        }
    }

    @WorkerThread
    private fun createAccount() {
        usernameError.postValue("")
        passwordError.postValue("")
        phoneNumberError.postValue("")

        if (::accountManagerServices.isInitialized) {
            val token = accountCreationToken
            if (token.isNullOrEmpty()) {
                Log.e("$TAG No account creation token, can't create account!")
                return
            }

            operationInProgress.postValue(true)
            createEnabled.postValue(false)

            val usernameValue = username.value
            val passwordValue = password.value
            if (usernameValue.isNullOrEmpty() || passwordValue.isNullOrEmpty()) {
                Log.e("$TAG Either username [$usernameValue] or password is null or empty!")
                return
            }

            Log.i(
                "$TAG Account creation token is [$token], creating account with username [$usernameValue] and algorithm SHA-256"
            )
            val request = accountManagerServices.createNewAccountUsingTokenRequest(
                usernameValue,
                passwordValue,
                HASH_ALGORITHM,
                token
            )
            request.addListener(accountManagerServicesListener)
            request.submit()
        }
    }

    @WorkerThread
    private fun storeAccountInCore(identity: String) {
        val passwordValue = password.value

        val core = coreContext.core
        val sipIdentity = Factory.instance().createAddress(identity)
        if (sipIdentity == null) {
            Log.e("$TAG Failed to create address from SIP Identity [$identity]!")
            return
        }

        // We need to have an AuthInfo for newly created account to authorize phone number linking request
        val authInfo = Factory.instance().createAuthInfo(
            sipIdentity.username.orEmpty(),
            null,
            passwordValue,
            null,
            null,
            sipIdentity.domain
        )
        core.addAuthInfo(authInfo)
        Log.i("$TAG Auth info for SIP identity [${sipIdentity.asStringUriOnly()}] created & added")

        val dialPlan = selectedDialPlan.value
        val accountParams = core.createAccountParams()
        accountParams.identityAddress = sipIdentity
        if (dialPlan != null) {
            Log.i(
                "$TAG Setting international prefix [${dialPlan.internationalCallPrefix}] and country [${dialPlan.isoCountryCode}] to account params"
            )
            accountParams.internationalPrefix = dialPlan.internationalCallPrefix
            accountParams.internationalPrefixIsoCountryCode = dialPlan.isoCountryCode
        }
        val account = core.createAccount(accountParams)
        core.addAccount(account)
        Log.i("$TAG Account for SIP identity [${sipIdentity.asStringUriOnly()}] created & added")

        accountCreatedAuthInfo = authInfo
        accountCreated = account
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
        createEnabled.postValue(false)

        val pushConfig = coreContext.core.pushNotificationConfig
        if (pushConfig != null) {
            val provider = pushConfig.provider
            val param = pushConfig.param
            val prid = pushConfig.prid
            if (provider.isNullOrEmpty() || param.isNullOrEmpty() || prid.isNullOrEmpty()) {
                Log.e(
                    "$TAG At least one mandatory push information (provider [$provider], param [$param], prid [$prid]) is missing!"
                )
                onFlexiApiTokenRequestError()
                return
            }

            // Request an auth token, will be sent by push
            val request = accountManagerServices.createSendAccountCreationTokenByPushRequest(
                provider,
                param,
                prid
            )
            request.addListener(accountManagerServicesListener)
            request.submit()

            val waitFor = TIME_TO_WAIT_FOR_PUSH_NOTIFICATION_WITH_ACCOUNT_CREATION_TOKEN
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
                        Log.e("$TAG Auth token wasn't received by push in [$waitFor] ms")
                        onFlexiApiTokenRequestError()
                    }
                }
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

/*
 * Copyright (c) 2010-2025 Belledonne Communications SARL.
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
import org.linphone.core.AccountCreatorListenerStub
import org.linphone.core.AccountManagerServices
import org.linphone.core.AccountManagerServicesRequest
import org.linphone.core.AccountManagerServicesRequestListenerStub
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.DialPlan
import org.linphone.core.Dictionary
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import java.util.Locale

class RecoverPhoneAccountViewModel : GenericViewModel() {
    companion object {
        private const val TAG = "[Recover Phone Account ViewModel]"

        private const val TIME_TO_WAIT_FOR_PUSH_NOTIFICATION_WITH_ACCOUNT_CREATION_TOKEN = 5000
    }

    val pushNotificationsAvailable = MutableLiveData<Boolean>()

    val dialPlansLabelList = arrayListOf<String>()

    val dialPlansShortLabelList = arrayListOf<String>()

    val dialPlansList = arrayListOf<DialPlan>()

    val selectedDialPlan = MutableLiveData<DialPlan>()

    val phoneNumber = MutableLiveData<String>()

    val phoneNumberError = MutableLiveData<String>()

    val confirmationMessage = MutableLiveData<String>()

    val smsCodeFirstDigit = MutableLiveData<String>()
    val smsCodeSecondDigit = MutableLiveData<String>()
    val smsCodeThirdDigit = MutableLiveData<String>()
    val smsCodeLastDigit = MutableLiveData<String>()

    val operationInProgress = MutableLiveData<Boolean>()

    val recoverEnabled = MediatorLiveData<Boolean>()

    private var normalizedPhoneNumber: String? = null
    val normalizedPhoneNumberEvent = MutableLiveData<Event<String>>()

    val goToSmsValidationEvent = MutableLiveData<Event<Boolean>>()

    val accountCreatedEvent = MutableLiveData<Event<String>>()

    private lateinit var accountManagerServices: AccountManagerServices
    private val accountManagerServicesListener = object : AccountManagerServicesRequestListenerStub() {
        @WorkerThread
        override fun onRequestSuccessful(
            request: AccountManagerServicesRequest,
            data: String?
        ) {
            Log.i("$TAG Request [$request] was successful, data is [$data]")
            operationInProgress.postValue(false)
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
                showFormattedRedToast(errorMessage, R.drawable.warning_circle)
            }

            when (request.type) {
                AccountManagerServicesRequest.Type.SendAccountCreationTokenByPush -> {
                    Log.w("$TAG Cancelling job waiting for push notification")
                    waitingForFlexiApiPushToken = false
                    waitForPushJob?.cancel()
                }
                else -> {
                }
            }
            recoverEnabled.postValue(true)
        }
    }
    private var accountCreationToken: String? = null

    private var waitingForFlexiApiPushToken = false
    private var waitForPushJob: Job? = null

    private lateinit var accountCreator: AccountCreator
    private val accountCreatorListener = object : AccountCreatorListenerStub() {
        @WorkerThread
        override fun onRecoverAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("$TAG Recover account status is $status")
            operationInProgress.postValue(false)

            if (status == AccountCreator.Status.RequestOk) {
                goToSmsValidationEvent.postValue(Event(true))
            } else {
                Log.e("$TAG Error in onRecoverAccount [${status.name}]")
                showFormattedRedToast(status.name, R.drawable.warning_circle)
            }
        }

        @WorkerThread
        override fun onLoginLinphoneAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("$TAG onLoginLinphoneAccount status is $status")
            operationInProgress.postValue(false)

            if (status == AccountCreator.Status.RequestOk) {
                if (!createAccountAndAuthInfo()) {
                    Log.e("$TAG Failed to create account object")
                }
            } else {
                Log.e("$TAG Error in onRecoverAccount [${status.name}]")
                showFormattedRedToast(status.name, R.drawable.warning_circle)
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
                                "$TAG Extracted token [$accountCreationToken] from push payload, recovering account"
                            )
                            requestSmsCode()
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
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

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

            accountCreator = core.createAccountCreator("https://subscribe.linphone.org/api/")
            accountCreator.addListener(accountCreatorListener)
        }

        recoverEnabled.addSource(selectedDialPlan) {
            recoverEnabled.value = phoneNumber.value.orEmpty().isNotEmpty() && selectedDialPlan.value?.countryCallingCode.orEmpty().isNotEmpty()
        }
        recoverEnabled.addSource(phoneNumber) {
            recoverEnabled.value = phoneNumber.value.orEmpty().isNotEmpty() && selectedDialPlan.value?.countryCallingCode.orEmpty().isNotEmpty()
        }
    }

    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
            accountCreator.removeListener(accountCreatorListener)
        }

        super.onCleared()
    }

    @UiThread
    fun sendCode() {
        coreContext.postOnCoreThread {
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
        }
    }

    @WorkerThread
    fun requestSmsCode() {
        operationInProgress.postValue(true)

        coreContext.core.loadConfigFromXml(corePreferences.linphoneDefaultValuesPath)
        accountCreator.domain = corePreferences.defaultDomain

        val dialPlan = selectedDialPlan.value
        if (dialPlan == null) {
            Log.e("$TAG No dial plan (country) selected!")
            return
        }
        val number = phoneNumber.value.orEmpty().trim()
        val countryCallingCode = dialPlan.countryCallingCode
        var result = AccountCreator.PhoneNumberStatus.fromInt(
            accountCreator.setPhoneNumber(number, countryCallingCode)
        )
        if (result != AccountCreator.PhoneNumberStatus.Ok) {
            Log.e(
                "$TAG Error [$result] setting the phone number: $number with prefix: $countryCallingCode"
            )
            phoneNumberError.postValue(result.name)
            operationInProgress.postValue(false)
            return
        }
        Log.i("$TAG Phone number is ${accountCreator.phoneNumber}")

        val result2 = accountCreator.setUsername(accountCreator.phoneNumber)
        if (result2 != AccountCreator.UsernameStatus.Ok) {
            Log.e(
                "$TAG Error [${result2.name}] setting the username: ${accountCreator.phoneNumber}"
            )
            phoneNumberError.postValue(result2.name)
            operationInProgress.postValue(false)
            return
        }
        Log.i("$TAG Username is ${accountCreator.username}")

        accountCreator.token = accountCreationToken
        Log.i("$TAG Token is ${accountCreator.token}")

        val status = accountCreator.recoverAccount()
        Log.i("$TAG Recover account returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            operationInProgress.postValue(false)
            Log.e("$TAG Error doing recoverAccount [${status.name}]")
            showFormattedRedToast(status.name, R.drawable.warning_circle)
        }
    }

    @UiThread
    fun validateCode() {
        operationInProgress.value = true

        coreContext.postOnCoreThread { core ->
            val code =
                "${smsCodeFirstDigit.value.orEmpty().trim()}${smsCodeSecondDigit.value.orEmpty().trim()}${smsCodeThirdDigit.value.orEmpty().trim()}${smsCodeLastDigit.value.orEmpty().trim()}"
            accountCreator.activationCode = code
            val status = accountCreator.loginLinphoneAccount()
            Log.i("$TAG Code [$code] validation result is $status")
            if (status != AccountCreator.Status.RequestOk) {
                operationInProgress.postValue(false)
                Log.e("$TAG Error doing loginLinphoneAccount [${status.name}]")
                showFormattedRedToast(status.name, R.drawable.warning_circle)
            }

            // Reset code
            smsCodeFirstDigit.postValue("")
            smsCodeSecondDigit.postValue("")
            smsCodeThirdDigit.postValue("")
            smsCodeLastDigit.postValue("")
        }
    }

    @WorkerThread
    private fun createAccountAndAuthInfo(): Boolean {
        val account = accountCreator.createAccountInCore()

        if (account == null) {
            Log.e("$TAG Account creator couldn't create account")
            return false
        }
        coreContext.core.defaultAccount = account

        val username = account.params.identityAddress?.username.orEmpty()
        Log.i("$TAG Account created with username [$username]")
        accountCreatedEvent.postValue(Event(username))
        return true
    }

    @UiThread
    fun startRecoveryProcess() {
        coreContext.postOnCoreThread {
            requestFlexiApiToken()
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
        recoverEnabled.postValue(false)

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
        showRedToast(R.string.assistant_account_register_push_notification_not_received_error, R.drawable.warning_circle)
    }
}

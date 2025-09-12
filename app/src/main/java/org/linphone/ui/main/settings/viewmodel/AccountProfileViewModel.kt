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
package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.AccountDevice
import org.linphone.core.AccountManagerServices
import org.linphone.core.AccountManagerServicesRequest
import org.linphone.core.AccountManagerServicesRequestListenerStub
import org.linphone.core.Address
import org.linphone.core.DialPlan
import org.linphone.core.Dictionary
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.model.AccountModel
import org.linphone.ui.main.model.isEndToEndEncryptionMandatory
import org.linphone.ui.main.settings.model.AccountDeviceModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class AccountProfileViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Account Profile ViewModel]"
    }

    val accountModel = MutableLiveData<AccountModel>()

    val sipAddress = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val dialPlansLabelList = arrayListOf<String>()

    val dialPlansList = arrayListOf<DialPlan>()

    val selectedDialPlan = MutableLiveData<Int>()

    val registerEnabled = MutableLiveData<Boolean>()

    val isCurrentlySelectedModeSecure = MutableLiveData<Boolean>()

    val devices = MutableLiveData<ArrayList<AccountDeviceModel>>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    val expandDetails = MutableLiveData<Boolean>()

    val expandDevices = MutableLiveData<Boolean>()

    val isOnDefaultDomain = MutableLiveData<Boolean>()

    val emptyDevices = MediatorLiveData<Boolean>()

    val devicesFetchInProgress = MutableLiveData<Boolean>()

    val hideAccountSettings = MutableLiveData<Boolean>()

    val deviceId = MutableLiveData<String>()

    val showDeviceId = MutableLiveData<Boolean>()

    val accountRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var account: Account

    private lateinit var accountManagerServices: AccountManagerServices

    private val accountManagerServicesListener = object : AccountManagerServicesRequestListenerStub() {
        @WorkerThread
        override fun onDevicesListFetched(
            request: AccountManagerServicesRequest,
            accountDevices: Array<out AccountDevice>
        ) {
            Log.i("$TAG Fetched [${accountDevices.size}] devices for our account")
            val devicesList = arrayListOf<AccountDeviceModel>()
            for (accountDevice in accountDevices) {
                devicesList.add(
                    AccountDeviceModel(accountDevice) { model, device ->
                        if (::accountManagerServices.isInitialized) {
                            val identityAddress = account.params.identityAddress
                            if (identityAddress != null) {
                                Log.i(
                                    "$TAG Removing device with name [${device.name}] and uuid [${device.uuid}]"
                                )
                                val deleteRequest = accountManagerServices.createDeleteDeviceRequest(
                                    identityAddress,
                                    device
                                )
                                deleteRequest.addListener(this)
                                deleteRequest.submit()

                                val newList = arrayListOf<AccountDeviceModel>()
                                newList.addAll(devices.value.orEmpty())
                                newList.remove(model)
                                devices.postValue(newList)
                            } else {
                                Log.e("$TAG Account identity address is null, can't delete device!")
                            }
                        }
                    }
                )
            }
            devices.postValue(devicesList)
            devicesFetchInProgress.postValue(false)
        }

        override fun onRequestSuccessful(request: AccountManagerServicesRequest, data: String?) {
            if (request.type == AccountManagerServicesRequest.Type.DeleteDevice) {
                Log.i("$TAG Device successfully deleted: $data")
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
                "$TAG Request [${request.type}] returned an error with status code [$statusCode] and message [$errorMessage]"
            )
            if (!errorMessage.isNullOrEmpty()) {
                when (request.type) {
                    AccountManagerServicesRequest.Type.GetDevicesList, AccountManagerServicesRequest.Type.DeleteDevice -> {
                        showFormattedRedToast(errorMessage, R.drawable.warning_circle)
                        devicesFetchInProgress.postValue(false)
                    }
                    else -> {}
                }
            }
        }
    }

    init {
        expandDetails.value = true
        expandDevices.value = false
        showDeviceId.value = false
        devicesFetchInProgress.value = true
        isOnDefaultDomain.value = false

        emptyDevices.value = true
        emptyDevices.addSource(devices) { list ->
            emptyDevices.value = list.orEmpty().isEmpty()
        }

        coreContext.postOnCoreThread {
            hideAccountSettings.postValue(corePreferences.hideAccountSettings)
            dialPlansLabelList.add("") // To allow removing selected dial plan

            val dialPlans = Factory.instance().dialPlans.toList()
            for (dialPlan in dialPlans) {
                dialPlansList.add(dialPlan)
                dialPlansLabelList.add(
                    "${dialPlan.flag} ${dialPlan.country} | +${dialPlan.countryCallingCode}"
                )
            }
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            accountModel.value?.destroy()
        }
    }

    @UiThread
    fun findAccountMatchingIdentity(identity: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.accountList.find {
                it.params.identityAddress?.asStringUriOnly() == identity
            }
            if (found != null) {
                Log.i("$TAG Found matching account [$found]")
                account = found
                accountModel.postValue(AccountModel(account))
                isCurrentlySelectedModeSecure.postValue(isEndToEndEncryptionMandatory())
                registerEnabled.postValue(account.params.isRegisterEnabled)

                sipAddress.postValue(account.params.identityAddress?.asStringUriOnly())
                displayName.postValue(account.params.identityAddress?.displayName)
                showDeviceId.postValue(false)

                val identityAddress = account.params.identityAddress
                if (identityAddress != null) {
                    val domain = identityAddress.domain
                    val defaultDomain = corePreferences.defaultDomain
                    isOnDefaultDomain.postValue(domain == defaultDomain)
                    if (domain == defaultDomain) {
                        requestDevicesList(identityAddress)
                    } else {
                        Log.i(
                            "$TAG Account with domain [$domain] can't get devices list, only works with [$defaultDomain] domain"
                        )
                    }
                } else {
                    Log.e("$TAG No identity address found!")
                }

                val prefix = account.params.internationalPrefix
                val isoCountryCode = account.params.internationalPrefixIsoCountryCode
                if (!prefix.isNullOrEmpty() || !isoCountryCode.isNullOrEmpty()) {
                    Log.i(
                        "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] prefix is [$prefix]($isoCountryCode)"
                    )
                    val dialPlan = Factory.instance().dialPlans.find {
                        it.isoCountryCode == isoCountryCode
                    } ?: Factory.instance().dialPlans.find {
                        it.countryCallingCode == prefix
                    }
                    if (dialPlan != null) {
                        val index = dialPlansList.indexOf(dialPlan) + 1
                        Log.i(
                            "$TAG Found matching dial plan [${dialPlan.country}] at index [$index]"
                        )
                        selectedDialPlan.postValue(index)
                    }
                }
                deviceId.postValue(account.contactAddress?.getUriParam("gr"))

                accountFoundEvent.postValue(Event(true))
            } else {
                accountFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun deleteAccount() {
        coreContext.postOnCoreThread { core ->
            if (::account.isInitialized) {
                Log.i("$TAG Removing call logs, conversations & meetings related to account being removed")
                account.clearCallLogs()

                // Wait for a better API in the SDK, deleteChatRoom will cause user to leave the groups,
                // which will cause issues in case of multi device
                /*
                for (conversation in account.chatRooms) {
                    core.deleteChatRoom(conversation)
                }
                */
                for (meeting in account.conferenceInformationList) {
                    core.deleteConferenceInformation(meeting)
                }

                val identity = account.params.identityAddress?.asStringUriOnly()
                val authInfo = account.findAuthInfo()
                if (authInfo != null) {
                    Log.i("$TAG Found auth info for account [$identity], removing it")
                    if (authInfo.password.isNullOrEmpty() && authInfo.ha1.isNullOrEmpty() && authInfo.accessToken != null) {
                        Log.i("$TAG Auth info was using bearer token instead of password")
                        val ssoCache = File(corePreferences.ssoCacheFile)
                        if (ssoCache.exists()) {
                            Log.i("$TAG Found auth_state.json file, deleting it")
                            viewModelScope.launch {
                                FileUtils.deleteFile(ssoCache.absolutePath)
                            }
                        }
                    }
                    core.removeAuthInfo(authInfo)
                } else {
                    Log.w("$TAG Failed to find matching auth info for account [$identity]")
                }

                core.removeAccount(account)
                Log.i("$TAG Account [$identity] has been removed")
                accountRemovedEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun setNewPicturePath(path: String) {
        coreContext.postOnCoreThread {
            if (::account.isInitialized) {
                val params = account.params
                val copy = params.clone()

                if (path.isNotEmpty() && path != params.pictureUri) {
                    Log.i("$TAG New account profile picture [$path]")
                    copy.pictureUri = path
                } else {
                    Log.i("$TAG Account profile picture removed")
                    copy.pictureUri = null
                }

                // Create a new model to force UI to update
                val newModel = AccountModel(account)
                newModel.picturePath.postValue(path)
                accountModel.postValue(newModel)

                // Also update friend & contact avatar model for ourselves
                val model = coreContext.contactsManager.getContactAvatarModelForAddress(
                    params.identityAddress
                )
                model.friend.photo = path
                model.picturePath.postValue(path)

                account.params = copy
                account.refreshRegister()
            }
        }
    }

    @UiThread
    fun saveChangesWhenLeaving() {
        coreContext.postOnCoreThread {
            if (::account.isInitialized) {
                val params = account.params
                val copy = params.clone()

                val address = params.identityAddress?.clone()
                if (address != null) {
                    val newValue = displayName.value.orEmpty().trim()
                    address.displayName = newValue
                    copy.identityAddress = address
                    // This will trigger a REGISTER, so account display name will be updated by
                    // CoreListener.onAccountRegistrationStateChanged everywhere in the app
                    Log.i(
                        "$TAG Updated account [${params.identityAddress?.asStringUriOnly()}] identity address display name [$newValue]"
                    )
                }

                account.params = copy
                account.refreshRegister()
            }
        }
    }

    @UiThread
    fun toggleDetailsExpand() {
        expandDetails.value = expandDetails.value == false
    }

    @UiThread
    fun toggleDevicesExpand() {
        expandDevices.value = expandDevices.value == false
    }

    @UiThread
    fun toggleRegister() {
        coreContext.postOnCoreThread { core ->
            val params = account.params
            val copy = params.clone()
            copy.isRegisterEnabled = !params.isRegisterEnabled
            Log.i(
                "$TAG Account registration is now [${if (copy.isRegisterEnabled) "enabled" else "disabled"}] for account [${account.params.identityAddress?.asStringUriOnly()}]"
            )
            account.params = copy
            registerEnabled.postValue(account.params.isRegisterEnabled)

            if (!core.isNetworkReachable) {
                // To reflect the difference between Disabled & Disconnected
                accountModel.value?.updateRegistrationState()
            }
        }
    }

    @UiThread
    fun setDialPlan(dialPlan: DialPlan) {
        coreContext.postOnCoreThread {
            val params = account.params
            val copy = params.clone()
            copy.internationalPrefix = dialPlan.countryCallingCode
            copy.internationalPrefixIsoCountryCode = dialPlan.isoCountryCode
            account.params = copy
            Log.i(
                "$TAG Updated international prefix for account [${account.params.identityAddress?.asStringUriOnly()}] to [${copy.internationalPrefix} (${copy.internationalPrefixIsoCountryCode})]"
            )
        }
    }

    @UiThread
    fun removeDialPlan() {
        coreContext.postOnCoreThread {
            val params = account.params
            val copy = params.clone()
            copy.internationalPrefix = ""
            copy.internationalPrefixIsoCountryCode = ""
            account.params = copy
            Log.i(
                "$TAG Removed international prefix for account [${account.params.identityAddress?.asStringUriOnly()}]"
            )
        }
    }

    @UiThread
    fun showDebugInfo(): Boolean {
        showDeviceId.value = true
        return true
    }

    @WorkerThread
    private fun requestDevicesList(identityAddress: Address) {
        Log.i(
            "$TAG Request devices list for identity address [${identityAddress.asStringUriOnly()}]"
        )
        accountManagerServices = coreContext.core.createAccountManagerServices()
        accountManagerServices.language = Locale.getDefault().language // Returns en, fr, etc...
        val request = accountManagerServices.createGetDevicesListRequest(
            identityAddress
        )
        request.addListener(accountManagerServicesListener)
        request.submit()
    }
}

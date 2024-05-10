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
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Account
import org.linphone.core.DialPlan
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.model.AccountModel
import org.linphone.ui.main.model.isEndToEndEncryptionMandatory
import org.linphone.ui.main.model.setEndToEndEncryptionMandatory
import org.linphone.ui.main.model.setInteroperabilityMode
import org.linphone.ui.main.settings.model.AccountDeviceModel
import org.linphone.utils.Event

class AccountProfileViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Account Profile ViewModel]"
    }

    val accountModel = MutableLiveData<AccountModel>()

    val sipAddress = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val dialPlansLabelList = arrayListOf<String>()

    val dialPlansList = arrayListOf<DialPlan>()

    val selectedDialPlan = MutableLiveData<Int>()

    val pushNotificationsAvailable = MutableLiveData<Boolean>()

    val pushNotificationsEnabled = MutableLiveData<Boolean>()

    val registerEnabled = MutableLiveData<Boolean>()

    val showModeSelection = MutableLiveData<Boolean>()

    val isCurrentlySelectedModeSecure = MutableLiveData<Boolean>()

    val devices = MutableLiveData<ArrayList<AccountDeviceModel>>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    val expandDetails = MutableLiveData<Boolean>()

    val expandDevices = MutableLiveData<Boolean>()

    val hideAccountSettings = MutableLiveData<Boolean>()

    val accountRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var account: Account

    init {
        expandDetails.value = true
        expandDevices.value = false // TODO: set to true when feature will be available

        coreContext.postOnCoreThread { core ->
            pushNotificationsAvailable.postValue(core.isPushNotificationAvailable)
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

        coreContext.postOnCoreThread { core ->
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
                isCurrentlySelectedModeSecure.postValue(account.isEndToEndEncryptionMandatory())
                registerEnabled.postValue(account.params.isRegisterEnabled)
                pushNotificationsEnabled.postValue(
                    core.isPushNotificationAvailable && account.params.pushNotificationAllowed
                )

                sipAddress.postValue(account.params.identityAddress?.asStringUriOnly())
                displayName.postValue(account.params.identityAddress?.displayName)

                val limeServerUrl = account.params.limeServerUrl
                val conferenceFactoryUri = account.params.conferenceFactoryUri
                val showMode = limeServerUrl.orEmpty().isNotEmpty() && conferenceFactoryUri.orEmpty().isNotEmpty()
                if (!showMode) {
                    Log.i(
                        "$TAG Either LIME server URL or conference factory URI isn't set, hiding end-to-end encrypted/interop mode selection"
                    )
                }
                showModeSelection.postValue(showMode)

                val devicesList = arrayListOf<AccountDeviceModel>()
                // TODO FIXME: use real devices list from API, not implemented yet
                devices.postValue(devicesList)

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
                val authInfo = account.findAuthInfo()
                if (authInfo != null) {
                    Log.i("$TAG Found auth info for account, removing it")
                    core.removeAuthInfo(authInfo)
                } else {
                    Log.w("$TAG Failed to find matching auth info for account")
                }

                core.removeAccount(account)
                Log.i("$TAG Account has been removed")
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
                copy.pushNotificationAllowed = pushNotificationsEnabled.value == true

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
        coreContext.postOnCoreThread {
            val params = account.params
            val copy = params.clone()
            copy.isRegisterEnabled = !params.isRegisterEnabled
            Log.i(
                "$TAG Account registration is now [${if (copy.isRegisterEnabled) "enabled" else "disabled"}] for account [${account.params.identityAddress?.asStringUriOnly()}]"
            )
            account.params = copy
            registerEnabled.postValue(account.params.isRegisterEnabled)
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
    fun switchToSecureMode() {
        isCurrentlySelectedModeSecure.value = true
    }

    @UiThread
    fun switchToInteropMode() {
        isCurrentlySelectedModeSecure.value = false
    }

    @UiThread
    fun applySelectedMode() {
        coreContext.postOnCoreThread { core ->
            if (isCurrentlySelectedModeSecure.value == true) {
                Log.i(
                    "$TAG Selected mode is end-to-end encrypted, forcing media & im encryption to mandatory and setting media encryption to ZRTP"
                )
                account.setEndToEndEncryptionMandatory()
            } else {
                Log.i(
                    "$TAG Selected mode is interoperable, not forcing media & im encryption to mandatory and setting media encryption to SRTP"
                )
                account.setInteroperabilityMode()
            }
        }
    }
}

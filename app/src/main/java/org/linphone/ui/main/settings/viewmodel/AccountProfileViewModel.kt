package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.DialPlan
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.AccountModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.ui.main.settings.model.AccountDeviceModel
import org.linphone.utils.Event

class AccountProfileViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Account Profile ViewModel]"
    }

    val accountModel = MutableLiveData<AccountModel>()

    val sipAddress = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val dialPlansLabelList = arrayListOf<String>()

    val dialPlansList = arrayListOf<DialPlan>()

    val selectedDialPlan = MutableLiveData<Int>()

    val pushNotificationsEnabled = MutableLiveData<Boolean>()

    val registerEnabled = MutableLiveData<Boolean>()

    val isCurrentlySelectedModeSecure = MutableLiveData<Boolean>()

    val devices = MutableLiveData<ArrayList<AccountDeviceModel>>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    val expandDetails = MutableLiveData<Boolean>()

    val expandDevices = MutableLiveData<Boolean>()

    val accountRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var account: Account

    init {
        expandDetails.value = true
        expandDevices.value = false // TODO: set to true when feature will be available

        coreContext.postOnCoreThread {
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
                isCurrentlySelectedModeSecure.postValue(account.isInSecureMode())
                registerEnabled.postValue(account.params.isRegisterEnabled)
                pushNotificationsEnabled.postValue(account.params.pushNotificationAllowed)

                sipAddress.postValue(account.params.identityAddress?.asStringUriOnly())
                displayName.postValue(account.params.identityAddress?.displayName)

                val devicesList = arrayListOf<AccountDeviceModel>()
                // TODO FIXME: use real devices list from API
                devicesList.add(
                    AccountDeviceModel("Pixel 6 Pro de Sylvain", "03/10/2023", "9h25") {
                    }
                )
                devicesList.add(
                    AccountDeviceModel(
                        "Sylvain Galaxy Tab S9 Pro+ Ultra",
                        "03/10/2023",
                        "9h25"
                    ) {
                    }
                )
                devicesList.add(
                    AccountDeviceModel("MacBook Pro de Marcel", "03/10/2023", "9h25") {
                    }
                )
                devicesList.add(
                    AccountDeviceModel("sylvain@fedora-linux-38", "03/10/2023", "9h25") {
                    }
                )
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
                newModel.images.postValue(arrayListOf(path))
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
        // TODO
    }
}

package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class AccountProfileViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Account Profile ViewModel]"
    }

    val accountModel = MutableLiveData<AccountModel>()

    val sipAddress = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val registerEnabled = MutableLiveData<Boolean>()

    val currentMode = MutableLiveData<String>()

    val internationalPrefix = MutableLiveData<String>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    val expandDetails = MutableLiveData<Boolean>()

    val accountRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var account: Account

    init {
        expandDetails.value = true
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
                currentMode.postValue(
                    AppUtils.getString(R.string.manage_account_secure_mode_default_title)
                ) // TODO: use real API when available
                registerEnabled.postValue(account.params.isRegisterEnabled)

                sipAddress.postValue(account.params.identityAddress?.asStringUriOnly())
                displayName.postValue(account.params.identityAddress?.displayName)
                internationalPrefix.postValue(account.params.internationalPrefix)

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
                }

                accountModel.value?.avatar?.postValue(path)
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

                copy.internationalPrefix = internationalPrefix.value.orEmpty()

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
    fun toggleRegister() {
        coreContext.postOnCoreThread {
            val params = account.params
            val copy = params.clone()
            copy.isRegisterEnabled = !params.isRegisterEnabled
            account.params = copy
            registerEnabled.postValue(account.params.isRegisterEnabled)
        }
    }
}

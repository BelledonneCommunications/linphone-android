package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class AccountProfileViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Account Profile ViewModel]"
    }

    val picturePath = MutableLiveData<String>()

    val sipAddress = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val internationalPrefix = MutableLiveData<String>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var account: Account

    @UiThread
    fun findAccountMatchingIdentity(identity: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.accountList.find {
                it.params.identityAddress?.asStringUriOnly() == identity
            }
            if (found != null) {
                Log.i("$TAG Found matching account [$found]")
                account = found
                sipAddress.postValue(account.params.identityAddress?.asStringUriOnly())
                displayName.postValue(account.params.identityAddress?.displayName)
                picturePath.postValue(account.params.pictureUri)
                internationalPrefix.postValue(account.params.internationalPrefix)

                accountFoundEvent.postValue(Event(true))
            } else {
                accountFoundEvent.postValue(Event(false))
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

                val newPictureUri = picturePath.value
                if (!newPictureUri.isNullOrEmpty() && newPictureUri != params.pictureUri) {
                    Log.i("$TAG New account profile picture [$newPictureUri]")
                    copy.pictureUri = newPictureUri
                }

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
}

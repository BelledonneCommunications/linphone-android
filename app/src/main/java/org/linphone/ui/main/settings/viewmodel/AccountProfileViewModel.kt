package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.getPicturePath
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class AccountProfileViewModel : ViewModel() {
    companion object {
        const val TAG = "[Account Profile ViewModel]"
    }

    val picturePath = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var account: Account

    @UiThread
    fun findAccountMatchingIdentity(identity: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.accountList.find {
                it.params.identityAddress?.asStringUriOnly() == identity
            }
            if (found != null) {
                Log.i("$TAG Found matching local friend [$found]")
                account = found
                displayName.postValue(account.params.identityAddress?.displayName)
                picturePath.postValue(account.getPicturePath())

                accountFoundEvent.postValue(Event(true))
            } else {
                accountFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun saveDisplayNameChanges() {
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
                    account.params = copy
                    Log.i(
                        "$TAG Updated account [$account] identity address display name [$newValue]"
                    )
                }
            }
        }
    }

    @UiThread
    fun setImage(file: File) {
        val path = FileUtils.getProperFilePath(file.absolutePath)
        picturePath.value = path

        coreContext.postOnCoreThread {
            if (::account.isInitialized) {
                // TODO: save image path in account
            }
        }
    }
}

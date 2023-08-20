package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.utils.Event

class AccountProfileViewModel : ViewModel() {
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
                account = found
                displayName.postValue(account.params.identityAddress?.displayName)

                val friend = coreContext.contactsManager.localFriends.find {
                    it.addresses.find { address ->
                        address.asStringUriOnly() == identity
                    } != null
                }
                if (friend != null) {
                    picturePath.postValue(friend.photo)
                } else {
                    // TODO
                }
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
                val address = params.identityAddress
                address?.displayName = displayName.value.orEmpty().trim()
                copy.identityAddress = address
                account.params = copy
            }
        }
    }

    @UiThread
    fun setImage(file: File) {
        val path = file.absolutePath
        picturePath.value = path
        coreContext.postOnCoreThread {
            if (::account.isInitialized) {
                val friend = coreContext.contactsManager.localFriends.find {
                    it.addresses.find { address ->
                        address.asStringUriOnly() == account.params.identityAddress?.asStringUriOnly()
                    } != null
                }
                if (friend != null) {
                    friend.edit()
                    friend.photo = path
                    friend.done()
                }
            }
        }
    }
}

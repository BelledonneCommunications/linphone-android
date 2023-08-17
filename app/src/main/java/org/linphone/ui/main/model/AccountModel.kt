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
package org.linphone.ui.main.model

import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.Friend
import org.linphone.core.RegistrationState
import org.linphone.ui.main.contacts.model.ContactAvatarModel

class AccountModel(private val account: Account) {
    val friend: Friend?

    val contact = MutableLiveData<ContactAvatarModel>()

    val registrationState = MutableLiveData<String>()

    val isConnected = MutableLiveData<Boolean>()

    val inError = MutableLiveData<Boolean>()

    val isDefault = MutableLiveData<Boolean>()

    private val accountListener = object : AccountListenerStub() {
        override fun onRegistrationStateChanged(
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            updateRegistrationState()
        }
    }

    init {
        // Core thread
        account.addListener(accountListener)
        isDefault.postValue(coreContext.core.defaultAccount == account)

        friend = coreContext.contactsManager.localFriends.find {
            it.addresses.find { address ->
                address.weakEqual(account.params.identityAddress!!)
            } != null
        }

        if (friend != null) {
            contact.postValue(ContactAvatarModel(friend))
        }

        updateRegistrationState()
    }

    fun destroy() {
        // Core thread
        account.removeListener(accountListener)
    }

    fun setAsDefault() {
        // UI thread
        coreContext.postOnCoreThread { core ->
            core.defaultAccount = account
            isDefault.postValue(true)
        }
    }

    fun refreshRegister() {
        // UI thread
        coreContext.postOnCoreThread { core ->
            core.refreshRegisters()
        }
    }

    private fun updateRegistrationState() {
        // Core thread
        val state = when (account.state) {
            RegistrationState.None, RegistrationState.Cleared -> "Disabled"
            RegistrationState.Progress -> "Connection..."
            RegistrationState.Failed -> "Error"
            RegistrationState.Ok -> "Connected"
            RegistrationState.Refreshing -> "Refreshing"
            else -> "${account.state}"
        }
        isConnected.postValue(account.state == RegistrationState.Ok)
        inError.postValue(account.state == RegistrationState.Failed)
        registrationState.postValue(state)
    }
}

/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.core.AccountCreator
import org.linphone.core.AccountCreatorListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class EmailAccountCreationViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EmailAccountCreationViewModel(accountCreator) as T
    }
}

class EmailAccountCreationViewModel(val accountCreator: AccountCreator) : ViewModel() {
    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val email = MutableLiveData<String>()
    val emailError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val passwordConfirmation = MutableLiveData<String>()
    val passwordConfirmationError = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val createEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val goToEmailValidationEvent = MutableLiveData<Event<Boolean>>()

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val listener = object : AccountCreatorListenerStub() {
        override fun onIsAccountExist(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("[Assistant] [Account Creation] onIsAccountExist status is $status")
            when (status) {
                AccountCreator.Status.AccountExist, AccountCreator.Status.AccountExistWithAlias -> {
                    waitForServerAnswer.value = false
                    usernameError.value = AppUtils.getString(R.string.assistant_error_username_already_exists)
                }
                AccountCreator.Status.AccountNotExist -> {
                    val createAccountStatus = creator.createAccount()
                    if (createAccountStatus != AccountCreator.Status.RequestOk) {
                        waitForServerAnswer.value = false
                        onErrorEvent.value = Event("Error: ${status.name}")
                    }
                }
                else -> {
                    waitForServerAnswer.value = false
                    onErrorEvent.value = Event("Error: ${status.name}")
                }
            }
        }

        override fun onCreateAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("[Account Creation] onCreateAccount status is $status")
            waitForServerAnswer.value = false

            when (status) {
                AccountCreator.Status.AccountCreated -> {
                    goToEmailValidationEvent.value = Event(true)
                }
                else -> {
                    onErrorEvent.value = Event("Error: ${status.name}")
                }
            }
        }
    }

    init {
        accountCreator.addListener(listener)

        createEnabled.value = false
        createEnabled.addSource(username) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(usernameError) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(email) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(emailError) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(password) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(passwordError) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(passwordConfirmation) {
            createEnabled.value = isCreateButtonEnabled()
        }
        createEnabled.addSource(passwordConfirmationError) {
            createEnabled.value = isCreateButtonEnabled()
        }
    }

    override fun onCleared() {
        accountCreator.removeListener(listener)
        super.onCleared()
    }

    fun create() {
        accountCreator.username = username.value
        accountCreator.password = password.value
        accountCreator.email = email.value
        accountCreator.displayName = displayName.value

        waitForServerAnswer.value = true
        val status = accountCreator.isAccountExist
        Log.i("[Assistant] [Account Creation] Account exists returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            waitForServerAnswer.value = false
            onErrorEvent.value = Event("Error: ${status.name}")
        }
    }

    private fun isCreateButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() &&
            email.value.orEmpty().isNotEmpty() &&
            password.value.orEmpty().isNotEmpty() &&
            passwordConfirmation.value.orEmpty().isNotEmpty() &&
            password.value == passwordConfirmation.value &&
            usernameError.value.orEmpty().isEmpty() &&
            emailError.value.orEmpty().isEmpty() &&
            passwordError.value.orEmpty().isEmpty() &&
            passwordConfirmationError.value.orEmpty().isEmpty()
    }
}

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

import androidx.lifecycle.*
import org.linphone.core.AccountCreator
import org.linphone.core.AccountCreatorListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class PhoneAccountLinkingViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PhoneAccountLinkingViewModel(accountCreator) as T
    }
}

class PhoneAccountLinkingViewModel(accountCreator: AccountCreator) : AbstractPhoneViewModel(accountCreator) {
    val username = MutableLiveData<String>()

    val allowSkip = MutableLiveData<Boolean>()

    val linkEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val leaveAssistantEvent = MutableLiveData<Event<Boolean>>()

    val goToSmsValidationEvent = MutableLiveData<Event<Boolean>>()

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val listener = object : AccountCreatorListenerStub() {
        override fun onIsAliasUsed(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("[Phone Account Linking] onIsAliasUsed status is $status")

            when (status) {
                AccountCreator.Status.AliasNotExist -> {
                    if (creator.linkAccount() != AccountCreator.Status.RequestOk) {
                        Log.e("[Phone Account Linking] linkAccount status is $status")
                        waitForServerAnswer.value = false
                        onErrorEvent.value = Event("Error: ${status.name}")
                    }
                }
                AccountCreator.Status.AliasExist, AccountCreator.Status.AliasIsAccount -> {
                    waitForServerAnswer.value = false
                    onErrorEvent.value = Event("Error: ${status.name}")
                }
                else -> {
                    waitForServerAnswer.value = false
                    onErrorEvent.value = Event("Error: ${status.name}")
                }
            }
        }

        override fun onLinkAccount(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("[Phone Account Linking] onLinkAccount status is $status")
            waitForServerAnswer.value = false

            when (status) {
                AccountCreator.Status.RequestOk -> {
                    goToSmsValidationEvent.value = Event(true)
                }
                else -> {
                    onErrorEvent.value = Event("Error: ${status.name}")
                }
            }
        }
    }

    init {
        accountCreator.addListener(listener)

        linkEnabled.value = false
        linkEnabled.addSource(prefix) {
            linkEnabled.value = isLinkButtonEnabled()
        }
        linkEnabled.addSource(phoneNumber) {
            linkEnabled.value = isLinkButtonEnabled()
        }
        linkEnabled.addSource(phoneNumberError) {
            linkEnabled.value = isLinkButtonEnabled()
        }
    }

    override fun onCleared() {
        accountCreator.removeListener(listener)
        super.onCleared()
    }

    fun link() {
        accountCreator.setPhoneNumber(phoneNumber.value, prefix.value)
        accountCreator.username = username.value
        Log.i("[Assistant] [Phone Account Linking] Phone number is ${accountCreator.phoneNumber}")

        waitForServerAnswer.value = true
        val status: AccountCreator.Status = accountCreator.isAliasUsed
        Log.i("[Phone Account Linking] isAliasUsed returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            waitForServerAnswer.value = false
            onErrorEvent.value = Event("Error: ${status.name}")
        }
    }

    fun skip() {
        leaveAssistantEvent.value = Event(true)
    }

    private fun isLinkButtonEnabled(): Boolean {
        return isPhoneNumberOk()
    }
}

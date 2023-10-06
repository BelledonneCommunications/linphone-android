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
package org.linphone.ui.main.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class MainViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Main ViewModel]"

        const val NONE = 0
        const val ACCOUNT_REGISTRATION_FAILURE = 1
        const val IN_CALL = 2
    }

    val showTopBar = MutableLiveData<Boolean>()

    val atLeastOneCall = MutableLiveData<Boolean>()

    val callLabel = MutableLiveData<String>()

    val callsStatus = MutableLiveData<String>()

    val defaultAccountRegistrationErrorEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val changeSystemTopBarColorEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    val goBackToCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var defaultAccountRegistrationFailed = false

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, asking fragment to change back status bar color")
            changeSystemTopBarColorEvent.postValue(Event(NONE))
        }

        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (core.callsNb > 0) {
                updateCurrentCallInfo()
            }
            val calls = core.callsNb > 0
            showTopBar.postValue(calls)
            atLeastOneCall.postValue(calls)
        }

        @WorkerThread
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            when (state) {
                RegistrationState.Failed -> {
                    if (account == core.defaultAccount) {
                        Log.e("$TAG Default account registration failed!")
                        defaultAccountRegistrationFailed = true
                        defaultAccountRegistrationErrorEvent.postValue(Event(true))
                    } else {
                        Log.e("$TAG Non-default account registration failed!")
                        // Do not show connection error top bar if there is a call
                        if (atLeastOneCall.value == false) {
                            changeSystemTopBarColorEvent.postValue(
                                Event(
                                    ACCOUNT_REGISTRATION_FAILURE
                                )
                            )
                            showTopBar.postValue(true)
                        }
                    }
                }
                RegistrationState.Ok -> {
                    if (account == core.defaultAccount && defaultAccountRegistrationFailed) {
                        Log.i("$TAG Default account is now registered")
                        defaultAccountRegistrationFailed = false
                        defaultAccountRegistrationErrorEvent.postValue(Event(false))
                    } else {
                        // If no call and no account is in Failed state, hide top bar
                        val found = core.accountList.find {
                            it.state == RegistrationState.Failed
                        }
                        if (found == null) {
                            Log.i("$TAG No account in Failed state anymore")
                            if (atLeastOneCall.value == false) {
                                changeSystemTopBarColorEvent.postValue(Event(NONE))
                                showTopBar.postValue(false)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    init {
        defaultAccountRegistrationFailed = false
        showTopBar.value = false

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            if (core.callsNb > 0) {
                updateCurrentCallInfo()
            }

            val calls = core.callsNb > 0
            showTopBar.postValue(calls)
            atLeastOneCall.postValue(calls)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun closeTopBar() {
        showTopBar.value = false
    }

    @UiThread
    fun onTopBarClicked() {
        goBackToCallEvent.value = Event(true)
    }

    @WorkerThread
    private fun updateCurrentCallInfo() {
        val core = coreContext.core
        if (core.callsNb == 1) {
            val currentCall = core.currentCall
            if (currentCall != null) {
                val contact = coreContext.contactsManager.findContactByAddress(
                    currentCall.remoteAddress
                )
                callLabel.postValue(
                    contact?.name ?: LinphoneUtils.getDisplayName(currentCall.remoteAddress)
                )
                callsStatus.postValue(LinphoneUtils.callStateToString(currentCall.state))
            } else {
                val firstCall = core.calls.firstOrNull()
                if (firstCall != null) {
                    val contact = coreContext.contactsManager.findContactByAddress(
                        firstCall.remoteAddress
                    )
                    callLabel.postValue(
                        contact?.name ?: LinphoneUtils.getDisplayName(firstCall.remoteAddress)
                    )
                    callsStatus.postValue(LinphoneUtils.callStateToString(firstCall.state))
                }
            }
        } else {
            callLabel.postValue(
                AppUtils.getFormattedString(R.string.calls_count_label, core.callsNb)
            )
            callsStatus.postValue("") // TODO: improve ?
        }
        Log.i("$TAG At least a call, asking fragment to change status bar color")
        changeSystemTopBarColorEvent.postValue(Event(IN_CALL))
    }
}

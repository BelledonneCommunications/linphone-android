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
    }

    val atLastOneCall = MutableLiveData<Boolean>()

    val callsLabel = MutableLiveData<String>()

    val callsStatus = MutableLiveData<String>()

    val defaultAccountRegistrationErrorEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val changeSystemTopBarColorToInCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goBackToCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var defaultAccountRegistrationFailed = false

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, asking fragment to change back status bar color")
            changeSystemTopBarColorToInCallEvent.postValue(Event(false))
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
            atLastOneCall.postValue(core.callsNb > 0)
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
                        // TODO: show red top bar for non-default account registration failure
                    }
                }
                RegistrationState.Ok -> {
                    if (account == core.defaultAccount && defaultAccountRegistrationFailed) {
                        Log.i("$TAG Default account is now registered")
                        defaultAccountRegistrationFailed = false
                        defaultAccountRegistrationErrorEvent.postValue(Event(false))
                    } else {
                        // TODO: hide red top bar for non-default account registration failure
                    }
                }
                else -> {}
            }
        }
    }

    init {
        defaultAccountRegistrationFailed = false

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            if (core.callsNb > 0) {
                updateCurrentCallInfo()
            }
            atLastOneCall.postValue(core.callsNb > 0)
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
    fun goBackToCall() {
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
                callsLabel.postValue(
                    contact?.name ?: LinphoneUtils.getDisplayName(currentCall.remoteAddress)
                )
                callsStatus.postValue(LinphoneUtils.callStateToString(currentCall.state))
            } else {
                val firstCall = core.calls.firstOrNull()
                if (firstCall != null) {
                    val contact = coreContext.contactsManager.findContactByAddress(
                        firstCall.remoteAddress
                    )
                    callsLabel.postValue(
                        contact?.name ?: LinphoneUtils.getDisplayName(firstCall.remoteAddress)
                    )
                    callsStatus.postValue(LinphoneUtils.callStateToString(firstCall.state))
                }
            }
        } else {
            callsLabel.postValue(
                AppUtils.getFormattedString(R.string.calls_count_label, core.callsNb)
            )
            callsStatus.postValue("") // TODO: improve ?
        }
        Log.i("$TAG At least a call, asking fragment to change status bar color")
        changeSystemTopBarColorToInCallEvent.postValue(Event(true))
    }
}

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
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

open class AbstractTopBarViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Top Bar ViewModel]"
    }

    val title = MutableLiveData<String>()

    val account = MutableLiveData<AccountModel>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val atLastOneCall = MutableLiveData<Boolean>()

    val callDisplayName = MutableLiveData<String>()

    val callStatus = MutableLiveData<String>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openDrawerMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val changeSystemTopBarColorToInCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goBackToCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

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
    }

    init {
        searchBarVisible.value = false

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            if (core.callsNb > 0) {
                updateCurrentCallInfo()
            }
            atLastOneCall.postValue(core.callsNb > 0)
        }

        update()
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
            account.value?.destroy()
        }
    }

    @UiThread
    fun openDrawerMenu() {
        openDrawerMenuEvent.value = Event(true)
    }

    @UiThread
    fun openSearchBar() {
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    @UiThread
    fun closeSearchBar() {
        clearFilter()
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    @UiThread
    fun clearFilter() {
        searchFilter.value = ""
    }

    @UiThread
    fun update() {
        coreContext.postOnCoreThread { core ->
            if (core.accountList.isNotEmpty()) {
                Log.i("$TAG Updating displayed default account")
                val defaultAccount = core.defaultAccount ?: core.accountList.first()
                account.postValue(AccountModel(defaultAccount))
            }

            if (core.callsNb > 0) {
                updateCurrentCallInfo()
            }
            atLastOneCall.postValue(core.callsNb > 0)
        }
    }

    @UiThread
    fun goBackToCall() {
        goBackToCallEvent.value = Event(true)
    }

    @WorkerThread
    private fun updateCurrentCallInfo() {
        val core = coreContext.core
        val currentCall = core.currentCall
        if (currentCall != null) {
            val contact = coreContext.contactsManager.findContactByAddress(
                currentCall.remoteAddress
            )
            callDisplayName.postValue(
                contact?.name ?: LinphoneUtils.getDisplayName(currentCall.remoteAddress)
            )
            callStatus.postValue(callStateToString(currentCall.state))
        } else {
            val firstCall = core.calls.firstOrNull()
            if (firstCall != null) {
                val contact = coreContext.contactsManager.findContactByAddress(
                    firstCall.remoteAddress
                )
                callDisplayName.postValue(
                    contact?.name ?: LinphoneUtils.getDisplayName(firstCall.remoteAddress)
                )
                callStatus.postValue(callStateToString(firstCall.state))
            }
        }
        Log.i("$TAG At least a call, asking fragment to change status bar color")
        changeSystemTopBarColorToInCallEvent.postValue(Event(true))
    }

    @WorkerThread
    private fun callStateToString(state: Call.State): String {
        return when (state) {
            Call.State.IncomingEarlyMedia, Call.State.IncomingReceived -> {
                AppUtils.getString(R.string.voip_call_state_incoming_received)
            }
            Call.State.OutgoingInit, Call.State.OutgoingProgress -> {
                AppUtils.getString(R.string.voip_call_state_outgoing_progress)
            }
            Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> {
                AppUtils.getString(R.string.voip_call_state_outgoing_ringing)
            }
            Call.State.Connected, Call.State.StreamsRunning, Call.State.Updating, Call.State.UpdatedByRemote -> {
                AppUtils.getString(R.string.voip_call_state_connected)
            }
            Call.State.Pausing, Call.State.Paused, Call.State.PausedByRemote -> {
                AppUtils.getString(R.string.voip_call_state_paused)
            }
            Call.State.End, Call.State.Released, Call.State.Error -> {
                AppUtils.getString(R.string.voip_call_state_ended)
            }
            else -> {
                // TODO: handle other states
                ""
            }
        }
    }
}

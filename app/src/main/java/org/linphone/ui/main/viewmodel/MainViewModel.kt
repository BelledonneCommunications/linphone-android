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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.ContactLoader
import org.linphone.core.Account
import org.linphone.core.AuthInfo
import org.linphone.core.AuthMethod
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
        const val NON_DEFAULT_ACCOUNT_NOTIFICATIONS = 5
        const val NON_DEFAULT_ACCOUNT_NOT_CONNECTED = 10
        const val NETWORK_NOT_REACHABLE = 19
        const val SINGLE_CALL = 20
        const val MULTIPLE_CALLS = 21
    }

    val showAlert = MutableLiveData<Boolean>()

    val alertLabel = MutableLiveData<String>()

    val alertIcon = MutableLiveData<Int>()

    val atLeastOneCall = MutableLiveData<Boolean>()

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

    val openDrawerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showNewAccountToastEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val startLoadingContactsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val authenticationRequestedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    var accountsFound = -1

    var mainIntentHandled = false

    private var defaultAccountRegistrationFailed = false

    private val alertsList = arrayListOf<Pair<Int, String>>()

    private var alertJob: Job? = null

    private var firstAccountRegistered: Boolean = false

    private var authInfoPendingPasswordUpdate: AuthInfo? = null

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, removing in-call 'alert'")
            removeAlert(SINGLE_CALL)
            atLeastOneCall.postValue(false)
        }

        @WorkerThread
        override fun onFirstCallStarted(core: Core) {
            Log.i("$TAG First call started, adding in-call 'alert'")
            updateCallAlert()
            atLeastOneCall.postValue(true)
        }

        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (
                core.callsNb > 1 && (
                    LinphoneUtils.isCallEnding(call.state) ||
                        LinphoneUtils.isCallIncoming(call.state) ||
                        LinphoneUtils.isCallOutgoing(call.state)
                    )
            ) {
                updateCallAlert()
            } else if (core.callsNb == 1) {
                if (LinphoneUtils.isCallEnding(call.state)) {
                    removeAlert(MULTIPLE_CALLS)
                }
                callsStatus.postValue(LinphoneUtils.callStateToString(call.state))
            }
        }

        @WorkerThread
        override fun onNetworkReachable(core: Core, reachable: Boolean) {
            Log.i("$TAG Network is ${if (reachable) "reachable" else "not reachable"}")
            if (!reachable) {
                val label = AppUtils.getString(R.string.network_not_reachable)
                addAlert(NETWORK_NOT_REACHABLE, label)
            } else {
                removeAlert(NETWORK_NOT_REACHABLE)
            }
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
                    } else if (core.isNetworkReachable) {
                        Log.e("$TAG Non-default account registration failed!")
                        val label = AppUtils.getString(
                            R.string.connection_error_for_non_default_account
                        )
                        addAlert(NON_DEFAULT_ACCOUNT_NOT_CONNECTED, label)
                    }
                }
                RegistrationState.Ok -> {
                    if (!firstAccountRegistered) {
                        triggerNativeAddressBookImport()
                    }

                    if (account == core.defaultAccount) {
                        if (defaultAccountRegistrationFailed) {
                            Log.i("$TAG Default account is now registered")
                            defaultAccountRegistrationFailed = false
                            defaultAccountRegistrationErrorEvent.postValue(Event(false))
                        }
                    } else {
                        // If no call and no account is in Failed state, hide top bar
                        val found = core.accountList.find {
                            it.state == RegistrationState.Failed
                        }
                        if (found == null) {
                            removeAlert(NON_DEFAULT_ACCOUNT_NOT_CONNECTED)
                        }
                    }
                }
                else -> {}
            }
        }

        @WorkerThread
        override fun onDefaultAccountChanged(core: Core, account: Account?) {
            if (account == null) {
                Log.w("$TAG Default account is now null!")
            } else {
                Log.i(
                    "$TAG Default account changed, now is [${account.params.identityAddress?.asStringUriOnly()}]"
                )
                coreContext.updateFriendListsSubscriptionDependingOnDefaultAccount()

                if (defaultAccountRegistrationFailed && account.state != RegistrationState.Failed) {
                    Log.i(
                        "$TAG Newly set default account isn't in failed registration state, clearing alert"
                    )
                    defaultAccountRegistrationFailed = false
                    defaultAccountRegistrationErrorEvent.postValue(Event(false))

                    // Refresh REGISTER to re-compute alerts regarding accounts registration state
                    core.refreshRegisters()
                }
            }

            removeAlert(NON_DEFAULT_ACCOUNT_NOTIFICATIONS)

            // TODO: compute other calls notifications count
        }

        @WorkerThread
        override fun onAccountRemoved(core: Core, account: Account) {
            accountsFound -= 1

            if (core.defaultAccount == null) {
                Log.i(
                    "$TAG Default account was removed, setting first available account (if any) as default"
                )
                core.defaultAccount = core.accountList.firstOrNull()
            }
        }

        @WorkerThread
        override fun onAuthenticationRequested(core: Core, authInfo: AuthInfo, method: AuthMethod) {
            if (authInfo.username == null || authInfo.domain == null || authInfo.realm == null) {
                return
            }

            Log.w(
                "$TAG Authentication requested for account [${authInfo.username}@${authInfo.domain}] with realm [${authInfo.realm}] using method [$method]"
            )
            val accountFound = core.accountList.find {
                it.params.identityAddress?.username == authInfo.username && it.params.identityAddress?.domain == authInfo.domain
            }
            if (accountFound == null) {
                Log.w("$TAG Failed to find account matching auth info, aborting auth dialog")
                return
            }
            val identity = "${authInfo.username}@${authInfo.domain}"
            authInfoPendingPasswordUpdate = authInfo
            authenticationRequestedEvent.postValue(Event(identity))
        }
    }

    init {
        defaultAccountRegistrationFailed = false
        showAlert.value = false

        coreContext.postOnCoreThread { core ->
            accountsFound = core.accountList.size

            core.addListener(coreListener)

            if (!core.isNetworkReachable) {
                Log.w("$TAG Network is not reachable!")
                val label = AppUtils.getString(R.string.network_not_reachable)
                addAlert(NETWORK_NOT_REACHABLE, label)
            }

            if (core.callsNb > 0) {
                updateCallAlert()
            }
            atLeastOneCall.postValue(core.callsNb > 0)

            if (core.defaultAccount?.state == RegistrationState.Ok && !firstAccountRegistered) {
                triggerNativeAddressBookImport()
            }
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
    fun checkForNewAccount() {
        coreContext.postOnCoreThread { core ->
            val count = core.accountList.size
            if (count > accountsFound) {
                showNewAccountToastEvent.postValue(Event(true))
            }
            accountsFound = count
        }
    }

    @UiThread
    fun closeTopBar() {
        showAlert.value = false

        coreContext.postOnCoreThread {
            Log.i("$TAG User closed alerts top bar, clearing alerts")
            cancelAlertJob()
            alertsList.clear()
            updateDisplayedAlert()
        }
    }

    @UiThread
    fun onTopBarClicked() {
        if (atLeastOneCall.value == true) {
            goBackToCallEvent.value = Event(true)
        } else {
            openDrawerEvent.value = Event(true)
        }
    }

    @UiThread
    fun updateAuthInfo(password: String) {
        coreContext.postOnCoreThread { core ->
            val authInfo = authInfoPendingPasswordUpdate
            if (authInfo != null) {
                Log.i(
                    "$TAG Updating password for username [${authInfo.username}] using auth info [$authInfo]"
                )
                authInfo.password = password
                core.addAuthInfo(authInfo)
                authInfoPendingPasswordUpdate = null
                core.refreshRegisters()
            }
        }
    }

    @WorkerThread
    private fun updateCallAlert() {
        val core = coreContext.core
        val callsNb = core.callsNb
        if (callsNb == 1) {
            removeAlert(MULTIPLE_CALLS)

            val currentCall = core.currentCall ?: core.calls.firstOrNull()
            if (currentCall != null) {
                val address = currentCall.remoteAddress
                val contact = coreContext.contactsManager.findContactByAddress(address)
                val label = if (contact != null) {
                    contact.name ?: LinphoneUtils.getDisplayName(address)
                } else {
                    val conferenceInfo = coreContext.core.findConferenceInformationFromUri(
                        address
                    )
                    conferenceInfo?.subject ?: LinphoneUtils.getDisplayName(
                        address
                    )
                }
                addAlert(SINGLE_CALL, label)
                callsStatus.postValue(LinphoneUtils.callStateToString(currentCall.state))
            }
        } else if (callsNb > 1) {
            removeAlert(SINGLE_CALL)

            addAlert(
                MULTIPLE_CALLS,
                AppUtils.getFormattedString(R.string.calls_count_label, callsNb)
            )
            callsStatus.postValue("") // TODO: improve ?
        }
    }

    @WorkerThread
    private fun addAlert(type: Int, label: String) {
        val found = alertsList.find {
            it.first == type
        }
        if (found == null) {
            cancelAlertJob()
            val alert = Pair(type, label)
            alertsList.add(alert)
            updateDisplayedAlert()
        } else {
            Log.w("$TAG There is already an alert with type [$type], skipping...")
        }
    }

    @WorkerThread
    private fun removeAlert(type: Int) {
        val found = alertsList.find {
            it.first == type
        }
        if (found != null) {
            cancelAlertJob()
            alertsList.remove(found)
            updateDisplayedAlert()
        } else {
            Log.w("$TAG Failed to remove alert with type [$type], not found in current alerts list")
        }
    }

    @WorkerThread
    private fun cancelAlertJob() {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                alertJob?.cancelAndJoin()
                alertJob = null
            }
        }
    }

    @WorkerThread
    private fun updateDisplayedAlert() {
        // Sort alerts by priority
        alertsList.sortByDescending {
            it.first
        }

        val maxedPriorityAlert = alertsList.firstOrNull()
        if (maxedPriorityAlert == null) {
            Log.i("$TAG No alert to display")
            showAlert.postValue(false)
            changeSystemTopBarColorEvent.postValue(Event(NONE))
        } else {
            val type = maxedPriorityAlert.first
            val label = maxedPriorityAlert.second
            Log.i("$TAG Max priority alert right now is [$type]")
            when (type) {
                NON_DEFAULT_ACCOUNT_NOTIFICATIONS, NON_DEFAULT_ACCOUNT_NOT_CONNECTED -> {
                    alertIcon.postValue(R.drawable.bell_simple)
                }
                NETWORK_NOT_REACHABLE -> {
                    alertIcon.postValue(R.drawable.wifi_slash)
                }
                SINGLE_CALL, MULTIPLE_CALLS -> {
                    alertIcon.postValue(R.drawable.phone)
                }
            }
            alertLabel.postValue(label)

            coreContext.postOnMainThread {
                val delayMs = if (type == SINGLE_CALL) 1000L else 0L
                alertJob = viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        delay(delayMs)
                        withContext(Dispatchers.Main) {
                            showAlert.value = true
                            changeSystemTopBarColorEvent.value = Event(type)
                        }
                    }
                }
            }
        }
    }

    @WorkerThread
    private fun triggerNativeAddressBookImport() {
        firstAccountRegistered = true

        if (coreContext.core.getFriendListByName(ContactLoader.NATIVE_ADDRESS_BOOK_FRIEND_LIST) == null) {
            Log.i("$TAG Native friend list not found, trying to fetch native contacts")
            startLoadingContactsEvent.postValue(Event(true))
        } else {
            Log.i("$TAG Native contacts were already imported once, do not do it again")
        }
    }
}

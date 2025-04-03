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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.GlobalState
import org.linphone.core.MessageWaitingIndication
import org.linphone.core.RegistrationState
import org.linphone.core.VFS
import org.linphone.core.tools.AndroidPlatformHelper
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

class MainViewModel
    @UiThread
    constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Main ViewModel]"

        const val NONE = 0
        const val MWI_MESSAGES_WAITING = 4
        const val NON_DEFAULT_ACCOUNT_NOTIFICATIONS = 5
        const val NON_DEFAULT_ACCOUNT_NOT_CONNECTED = 10
        const val FULL_SCREEN_INTENTS_PERMISSION_NOT_GRANTED = 14
        const val SEND_NOTIFICATIONS_PERMISSION_NOT_GRANTED = 15
        const val DEFAULT_ACCOUNT_DISABLED = 18
        const val NETWORK_NOT_REACHABLE = 19
        const val SINGLE_CALL = 20
        const val MULTIPLE_CALLS = 21
    }

    val showAlert = MutableLiveData<Boolean>()

    val maxAlertLevel = MutableLiveData<Int>()

    val alertLabel = MutableLiveData<String>()

    val alertIcon = MutableLiveData<Int>()

    val atLeastOneCall = MutableLiveData<Boolean>()

    val callsStatus = MutableLiveData<String>()

    val goBackToCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openDrawerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPostNotificationsPermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askFullScreenIntentPermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showNewAccountToastEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val startLoadingContactsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val lastAccountRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var accountsFound = -1

    var mainIntentHandled = false

    private val alertsList = arrayListOf<Pair<Int, String>>()

    private var firstAccountRegistered: Boolean = false

    private var monitorAccount = false

    private var nonDefaultAccountNotificationsCount = 0

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onGlobalStateChanged(core: Core, state: GlobalState?, message: String) {
            Log.i("$TAG Core's global state is now [${core.globalState}]")
            if (core.globalState == GlobalState.On) {
                computeNonDefaultAccountNotificationsCount()
            }
        }

        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, removing in-call 'alert'")
            removeAlert(SINGLE_CALL)
            atLeastOneCall.postValue(false)
            computeNonDefaultAccountNotificationsCount()
        }

        @WorkerThread
        override fun onFirstCallStarted(core: Core) {
            Log.i("$TAG First call started, adding in-call 'alert'")
            updateCallAlert()
            coreContext.postOnCoreThreadDelayed({
                if (core.callsNb > 0) {
                    Log.i("$TAG At least a call is active, showing 'alert' top bar")
                    atLeastOneCall.postValue(true)
                } else {
                    Log.i("$TAG No call found, do not show 'alert' top bar")
                }
            }, 1000L)
        }

        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("$TAG A call's state changed, updating 'alerts' if needed")
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
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            Log.i("$TAG Message(s) received, updating notifications count if needed")
            val account = LinphoneUtils.getAccountForAddress(chatRoom.localAddress)
            if (account != null && account != core.defaultAccount) {
                computeNonDefaultAccountNotificationsCount()
            }
        }

        @WorkerThread
        override fun onNetworkReachable(core: Core, reachable: Boolean) {
            Log.i(
                "$TAG According to SDK, network is ${if (reachable) "reachable" else "not reachable"}"
            )
            checkNetworkReachability()
        }

        @WorkerThread
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (!monitorAccount) return

            when (state) {
                RegistrationState.Failed -> {
                    if (account == core.defaultAccount) {
                        Log.e("$TAG Default account registration failed!")
                        val label = AppUtils.getString(
                            R.string.connection_error_for_non_default_account
                        )
                        addAlert(DEFAULT_ACCOUNT_DISABLED, label)
                    } else if (core.isNetworkReachable) {
                        Log.e("$TAG Non-default account registration failed!")
                        val label = AppUtils.getString(
                            R.string.connection_error_for_non_default_account
                        )
                        addAlert(NON_DEFAULT_ACCOUNT_NOT_CONNECTED, label)
                    }
                }
                RegistrationState.Ok -> {
                    removeAlert(NETWORK_NOT_REACHABLE) // Just in case

                    if (!firstAccountRegistered) {
                        triggerNativeAddressBookImport()
                    }

                    if (account == core.defaultAccount) {
                        Log.i("$TAG Default account is now registered")
                        removeAlert(DEFAULT_ACCOUNT_DISABLED)
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
                RegistrationState.Progress, RegistrationState.Refreshing -> {
                    if (account == core.defaultAccount) {
                        Log.i(
                            "$TAG Default account is registering, removing registration failed alert for now"
                        )
                        removeAlert(DEFAULT_ACCOUNT_DISABLED)
                    }
                }
                RegistrationState.Cleared -> {
                    if (account == core.defaultAccount) {
                        Log.w("$TAG Default account is now disabled")
                        val label = AppUtils.getString(
                            R.string.default_account_disabled
                        )
                        addAlert(DEFAULT_ACCOUNT_DISABLED, label)
                    }
                }
                else -> {}
            }
        }

        @WorkerThread
        override fun onDefaultAccountChanged(core: Core, account: Account?) {
            if (!monitorAccount) return
            if (core.globalState != GlobalState.On) return // In case of late remote provisioning

            if (account == null) {
                Log.w("$TAG Default account is now null!")
            } else {
                Log.i(
                    "$TAG Default account changed, now is [${account.params.identityAddress?.asStringUriOnly()}]"
                )
                coreContext.updateFriendListsSubscriptionDependingOnDefaultAccount()

                removeAlert(DEFAULT_ACCOUNT_DISABLED)
                removeAlert(NON_DEFAULT_ACCOUNT_NOT_CONNECTED)
                // Refresh REGISTER to re-compute alerts regarding accounts registration state
                core.refreshRegisters()

                if (!account.params.isRegisterEnabled) {
                    val label = AppUtils.getString(
                        R.string.default_account_disabled
                    )
                    addAlert(DEFAULT_ACCOUNT_DISABLED, label)
                }
            }

            computeNonDefaultAccountNotificationsCount()
        }

        @WorkerThread
        override fun onAccountRemoved(core: Core, account: Account) {
            if (!monitorAccount) return
            if (core.globalState != GlobalState.On) return // In case of late remote provisioning

            Log.w(
                "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] has been removed!"
            )
            removeAlert(DEFAULT_ACCOUNT_DISABLED)
            removeAlert(NON_DEFAULT_ACCOUNT_NOT_CONNECTED)
            // Refresh REGISTER to re-compute alerts regarding accounts registration state
            core.refreshRegisters()

            computeNonDefaultAccountNotificationsCount()

            if (core.accountList.isEmpty()) {
                Log.w("$TAG No more account configured, going into assistant")
                lastAccountRemovedEvent.postValue(Event(true))
            }
        }

        @WorkerThread
        override fun onMessageWaitingIndicationChanged(
            core: Core,
            event: org.linphone.core.Event,
            mwi: MessageWaitingIndication
        ) {
            if (mwi.hasMessageWaiting()) {
                val summaries = mwi.summaries
                Log.i(
                    "$TAG MWI NOTIFY received, messages are waiting ([${summaries.size}] summaries)"
                )
                if (summaries.isNotEmpty()) {
                    val summary = summaries.first()
                    val label = AppUtils.getStringWithPlural(
                        R.plurals.mwi_messages_are_waiting,
                        summary.nbNew,
                        summary.nbNew.toString()
                    )
                    addAlert(MWI_MESSAGES_WAITING, label)
                }
            } else {
                Log.i("$TAG MWI NOTIFY received, no message is waiting")
                removeAlert(MWI_MESSAGES_WAITING)
            }
        }
    }

    init {
        showAlert.value = false
        atLeastOneCall.value = false
        maxAlertLevel.value = NONE
        nonDefaultAccountNotificationsCount = 0
        enableAccountMonitoring(true)

        coreContext.postOnCoreThread { core ->
            accountsFound = core.accountList.size

            core.addListener(coreListener)

            if (!core.isNetworkReachable && core.globalState == GlobalState.On) {
                Log.w("$TAG Core is ON & network is not reachable!")
                val label = AppUtils.getString(R.string.network_not_reachable)
                addAlert(NETWORK_NOT_REACHABLE, label)
            }

            if (core.callsNb > 0) {
                updateCallAlert()
                atLeastOneCall.postValue(true)
            }

            val defaultAccount = core.defaultAccount
            if (defaultAccount != null) {
                if (!defaultAccount.params.isRegisterEnabled) {
                    val label = AppUtils.getString(
                        R.string.default_account_disabled
                    )
                    addAlert(DEFAULT_ACCOUNT_DISABLED, label)
                }

                if (defaultAccount.state == RegistrationState.Ok && !firstAccountRegistered) {
                    triggerNativeAddressBookImport()
                }
            }
        }

        updateMissingPermissionAlert()

        if (VFS.isEnabled(coreContext.context)) {
            val cache = corePreferences.vfsCachePath
            viewModelScope.launch {
                val notClearedCount = FileUtils.countFilesInDirectory(cache)
                if (notClearedCount > 0) {
                    Log.w(
                        "$TAG [VFS] There are [$notClearedCount] plain files not cleared from previous app lifetime, removing them now"
                    )
                    FileUtils.clearExistingPlainFiles(cache)
                }
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
    fun updateNetworkReachability() {
        coreContext.postOnCoreThread {
            checkNetworkReachability()
        }
    }

    @UiThread
    fun updateMissingPermissionAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            coreContext.postOnCoreThread {
                checkFullScreenIntentNotificationPermission()
                checkPostNotificationsPermission()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            coreContext.postOnCoreThread {
                checkPostNotificationsPermission()
            }
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
            showAlert.postValue(false)
            alertsList.clear()
            updateDisplayedAlert()
        }
    }

    @UiThread
    fun onTopBarClicked() {
        if (atLeastOneCall.value == true) {
            goBackToCallEvent.value = Event(true)
        } else if (!Compatibility.hasFullScreenIntentPermission(coreContext.context)) {
            askFullScreenIntentPermissionEvent.value = Event(true)
        } else if (!Compatibility.isPostNotificationsPermissionGranted(coreContext.context)) {
            askPostNotificationsPermissionEvent.value = Event(true)
        } else {
            openDrawerEvent.value = Event(true)
        }
    }

    @UiThread
    fun enableAccountMonitoring(enable: Boolean) {
        if (enable != monitorAccount) {
            monitorAccount = enable
            Log.i(
                "$TAG Account monitoring is now [${if (monitorAccount) "enabled" else "disabled"}]"
            )
        } else {
            Log.i(
                "$TAG Account monitoring is already [${if (monitorAccount) "enabled" else "disabled"}], nothing to do"
            )
        }
    }

    @WorkerThread
    private fun computeNonDefaultAccountNotificationsCount() {
        var count = 0
        for (account in coreContext.core.accountList) {
            if (account == coreContext.core.defaultAccount) continue
            count += account.unreadChatMessageCount + account.missedCallsCount
        }
        if (count != nonDefaultAccountNotificationsCount) {
            if (count > 0) {
                val label = AppUtils.getStringWithPlural(
                    R.plurals.pending_notification_for_other_accounts,
                    count,
                    count.toString()
                )
                addAlert(NON_DEFAULT_ACCOUNT_NOTIFICATIONS, label, forceUpdate = true)
                Log.i("$TAG Found [$count] pending notifications for other account(s)")
            } else {
                removeAlert(NON_DEFAULT_ACCOUNT_NOTIFICATIONS)
                Log.i("$TAG No pending notification found for other account(s)")
            }
            nonDefaultAccountNotificationsCount = count
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
                val address = currentCall.callLog.remoteAddress
                val conferenceInfo = LinphoneUtils.getConferenceInfoIfAny(currentCall)
                val label = if (conferenceInfo != null) {
                    conferenceInfo.subject ?: LinphoneUtils.getDisplayName(address)
                } else {
                    val contact = coreContext.contactsManager.findContactByAddress(address)
                    contact?.name ?: LinphoneUtils.getDisplayName(address)
                }
                Log.i("$TAG Showing single call alert with label [$label]")
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
    private fun addAlert(type: Int, label: String, forceUpdate: Boolean = false) {
        val found = alertsList.find {
            it.first == type
        }
        if (found == null || forceUpdate) {
            showAlert.postValue(false)
            if (found != null) {
                alertsList.remove(found)
            }

            val alert = Pair(type, label)
            Log.i("$TAG Adding alert with type [$type]")
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
            showAlert.postValue(false)
            Log.i("$TAG Removing alert with type [$type]")
            alertsList.remove(found)
            updateDisplayedAlert()
        } else {
            Log.w("$TAG Failed to remove alert with type [$type], not found in current alerts list")
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
            maxAlertLevel.postValue(NONE)
        } else {
            val type = maxedPriorityAlert.first
            val label = maxedPriorityAlert.second
            Log.i("$TAG Max priority alert right now is [$type]")
            maxAlertLevel.postValue(type)
            val icon = when (type) {
                DEFAULT_ACCOUNT_DISABLED -> {
                    R.drawable.warning_circle
                }
                NETWORK_NOT_REACHABLE -> {
                    R.drawable.wifi_slash
                }
                SEND_NOTIFICATIONS_PERMISSION_NOT_GRANTED, FULL_SCREEN_INTENTS_PERMISSION_NOT_GRANTED -> {
                    R.drawable.bell_simple_slash
                }
                SINGLE_CALL, MULTIPLE_CALLS -> {
                    R.drawable.phone
                }
                else -> {
                    R.drawable.bell_simple
                }
            }
            alertIcon.postValue(icon)
            alertLabel.postValue(label)

            if (type < SINGLE_CALL) {
                // Call alert is displayed using atLeastOnCall mutable, not showAlert
                Log.i("$TAG Alert top-bar is currently invisible, display it now")
                showAlert.postValue(true)
            }
        }
    }

    @WorkerThread
    private fun triggerNativeAddressBookImport() {
        firstAccountRegistered = true
        Log.i("$TAG Trying to fetch & import native contacts")
        startLoadingContactsEvent.postValue(Event(true))
    }

    @WorkerThread
    private fun checkNetworkReachability() {
        val reachable = coreContext.core.isNetworkReachable
        Log.i("$TAG Network is ${if (reachable) "reachable" else "not reachable"}")
        if (!reachable && coreContext.core.globalState == GlobalState.On) {
            val label = if (coreContext.core.isWifiOnlyEnabled) {
                if (AndroidPlatformHelper.isReady() && AndroidPlatformHelper.instance().isActiveNetworkWifiOnlyCompliant) {
                    AppUtils.getString(R.string.network_not_reachable)
                } else {
                    AppUtils.getString(R.string.network_is_not_wifi)
                }
            } else {
                AppUtils.getString(R.string.network_not_reachable)
            }
            addAlert(NETWORK_NOT_REACHABLE, label)
        } else {
            removeAlert(NETWORK_NOT_REACHABLE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    private fun checkFullScreenIntentNotificationPermission() {
        if (!Compatibility.hasFullScreenIntentPermission(coreContext.context)) {
            Log.w("$TAG USE_FULL_SCREEN_INTENT seems to be not granted!")
            val label = AppUtils.getString(R.string.full_screen_intent_permission_not_granted)
            coreContext.postOnCoreThread {
                addAlert(FULL_SCREEN_INTENTS_PERMISSION_NOT_GRANTED, label)
            }
        } else {
            removeAlert(FULL_SCREEN_INTENTS_PERMISSION_NOT_GRANTED)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @WorkerThread
    private fun checkPostNotificationsPermission() {
        if (!Compatibility.isPostNotificationsPermissionGranted(coreContext.context)) {
            Log.w("$TAG POST_NOTIFICATIONS seems to be not granted!")
            val label = AppUtils.getString(R.string.post_notifications_permission_not_granted)
            coreContext.postOnCoreThread {
                addAlert(SEND_NOTIFICATIONS_PERMISSION_NOT_GRANTED, label)
            }
        } else {
            removeAlert(SEND_NOTIFICATIONS_PERMISSION_NOT_GRANTED)
        }
    }
}

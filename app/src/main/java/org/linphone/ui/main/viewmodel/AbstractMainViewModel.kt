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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ConfiguringState
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

open class AbstractMainViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Abstract Main ViewModel]"
    }

    val title = MutableLiveData<String>()

    val account = MutableLiveData<AccountModel>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val contactsSelected = MutableLiveData<Boolean>()

    val callsSelected = MutableLiveData<Boolean>()

    val conversationsSelected = MutableLiveData<Boolean>()

    val meetingsSelected = MutableLiveData<Boolean>()

    val hideConversations = MutableLiveData<Boolean>()

    val hideMeetings = MutableLiveData<Boolean>()

    val missedCallsCount = MutableLiveData<Int>()

    val unreadMessages = MutableLiveData<Int>()

    val isFilterEmpty = MutableLiveData<Boolean>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openDrawerMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToHistoryEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToContactsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToConversationsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToMeetingsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val defaultAccountChangedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    protected var currentFilter = ""

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (state == Call.State.End || state == Call.State.Error) {
                updateMissedCallsCount()
            }
        }

        @WorkerThread
        override fun onChatRoomStateChanged(
            core: Core,
            chatRoom: ChatRoom,
            state: ChatRoom.State?
        ) {
            if (state == ChatRoom.State.Deleted) {
                computeUnreadMessagesCount()
            }
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            computeUnreadMessagesCount()
        }

        @WorkerThread
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            computeUnreadMessagesCount()
        }

        @WorkerThread
        override fun onConfiguringStatus(core: Core, status: ConfiguringState?, message: String?) {
            if (status != ConfiguringState.Skipped) {
                account.value?.destroy()

                val defaultAccount = core.defaultAccount
                if (defaultAccount != null) {
                    Log.i("$TAG Configuring status is [$status], reload default account")
                    account.postValue(AccountModel(defaultAccount))
                    defaultAccountChangedEvent.postValue(Event(true))
                } else {
                    Log.w("$TAG Configuring status is [$status] but no default account was found!")
                }
            }
        }

        @WorkerThread
        override fun onDefaultAccountChanged(core: Core, defaultAccount: Account?) {
            account.value?.destroy()

            if (defaultAccount == null) {
                Log.w("$TAG Default account is now null!")
                return
            } else {
                Log.i(
                    "$TAG Default account has changed [${defaultAccount.params.identityAddress?.asStringUriOnly()}]"
                )

                account.value?.destroy()
                account.postValue(AccountModel(defaultAccount))
            }

            computeUnreadMessagesCount()
            updateMissedCallsCount()
            updateAvailableMenus()

            defaultAccountChangedEvent.postValue(Event(true))
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            configure()
        }

        searchBarVisible.value = false
        isFilterEmpty.value = true
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
        if (searchFilter.value.orEmpty().isEmpty()) {
            searchBarVisible.value = false
            focusSearchBarEvent.value = Event(false)
        } else {
            searchFilter.value = ""
        }
    }

    @UiThread
    fun applyFilter(filter: String = currentFilter) {
        Log.i("$TAG New filter set by user [$filter]")
        currentFilter = filter
        isFilterEmpty.postValue(filter.isEmpty())
        filter()
    }

    @UiThread
    open fun filter() {
    }

    @UiThread
    fun update() {
        coreContext.postOnCoreThread { core ->
        }
    }

    @UiThread
    fun navigateToContacts() {
        navigateToContactsEvent.value = Event(true)
    }

    @UiThread
    fun navigateToHistory() {
        navigateToHistoryEvent.value = Event(true)
    }

    @UiThread
    fun navigateToConversations() {
        navigateToConversationsEvent.value = Event(true)
    }

    @UiThread
    fun navigateToMeetings() {
        navigateToMeetingsEvent.value = Event(true)
    }

    @UiThread
    fun updateUnreadMessagesCount() {
        coreContext.postOnCoreThread {
            computeUnreadMessagesCount()
        }
    }

    @WorkerThread
    fun updateMissedCallsCount() {
        val account = LinphoneUtils.getDefaultAccount()
        val count = account?.missedCallsCount ?: coreContext.core.missedCallsCount
        val moreThanOne = count > 1
        Log.i(
            "$TAG There ${if (moreThanOne) "are" else "is"} [$count] missed ${if (moreThanOne) "calls" else "call"}"
        )
        missedCallsCount.postValue(count)
    }

    @WorkerThread
    fun computeUnreadMessagesCount() {
        val account = LinphoneUtils.getDefaultAccount()
        val count = account?.unreadChatMessageCount ?: coreContext.core.unreadChatMessageCount
        val moreThanOne = count > 1
        Log.i(
            "$TAG There ${if (moreThanOne) "are" else "is"} [$count] unread ${if (moreThanOne) "messages" else "message"}"
        )
        unreadMessages.postValue(count)
    }

    @UiThread
    fun resetMissedCallsCount() {
        coreContext.postOnCoreThread { core ->
            val account = LinphoneUtils.getDefaultAccount()
            account?.resetMissedCallsCount() ?: core.resetMissedCallsCount()
            updateMissedCallsCount()
        }
    }

    @WorkerThread
    fun updateAvailableMenus() {
        hideConversations.postValue(corePreferences.disableChat)

        val conferencingAvailable = LinphoneUtils.isRemoteConferencingAvailable(
            coreContext.core
        )
        val hideGroupCall =
            coreContext.core.accountList.isEmpty() || corePreferences.disableMeetings || !conferencingAvailable
        hideMeetings.postValue(hideGroupCall)
    }

    @WorkerThread
    private fun configure() {
        updateAvailableMenus()

        val core = coreContext.core
        val defaultAccount = core.defaultAccount
        if (defaultAccount != null || core.accountList.isNotEmpty()) {
            Log.i("$TAG Updating displayed default account")
            account.value?.destroy()
            account.postValue(AccountModel(defaultAccount ?: core.accountList.first()))

            computeUnreadMessagesCount()
            updateMissedCallsCount()
        } else {
            Log.e("$TAG Accounts list no supposed to be empty!")
        }
    }
}

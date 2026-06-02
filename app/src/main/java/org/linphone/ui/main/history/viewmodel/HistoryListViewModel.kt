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
package org.linphone.ui.main.history.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contacts.ContactsManager
import org.linphone.core.Address
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.GlobalState
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.ui.main.history.model.CallLogModelWrapper
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.ui.main.viewmodel.AbstractMainViewModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import java.text.Collator
import java.util.Locale

class HistoryListViewModel
    @UiThread
    constructor() : AbstractMainViewModel() {
    companion object {
        private const val TAG = "[History List ViewModel]"
    }

    val callLogs = MutableLiveData<ArrayList<CallLogModelWrapper>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val historyInsertedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    private val tempCallLogsList = ArrayList<CallLogModelWrapper>()

    private val magicSearch = coreContext.core.createMagicSearch()

    private val magicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            Log.i("$TAG Magic search contacts available")
            val results = magicSearch.lastSearch
            processMagicSearchResults(results)
            fetchInProgress.postValue(false)
        }
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onGlobalStateChanged(core: Core, state: GlobalState?, message: String) {
            if (state == GlobalState.On) {
                Log.i("$TAG Core just started, fetching history")
                computeCallLogsList(currentFilter)
            }
        }

        @WorkerThread
        override fun onCallLogUpdated(core: Core, callLog: CallLog) {
            Log.i("$TAG A call log was created, updating list")
            computeCallLogsList(currentFilter)
            historyInsertedEvent.postValue(Event(true))
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            computeCallLogsList(currentFilter)
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        fetchInProgress.value = true

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.addListener(contactsListener)
            core.addListener(coreListener)
            magicSearch.addListener(magicSearchListener)

            computeCallLogsList(currentFilter)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.removeListener(contactsListener)
            core.removeListener(coreListener)
            magicSearch.removeListener(magicSearchListener)
        }
    }

    @UiThread
    fun removeAllCallLogs() {
        coreContext.postOnCoreThread { core ->
            // TODO FIXME: remove workaround later
            if (coreContext.core.accountList.size > 1) {
                val account = LinphoneUtils.getDefaultAccount()
                if (account != null) {
                    account.clearCallLogs()
                } else {
                    core.clearCallLogs()
                }
            } else {
                core.clearCallLogs()
            }

            historyDeletedEvent.postValue(Event(true))
            computeCallLogsList(currentFilter)
        }
    }

    @UiThread
    override fun filter() {
        coreContext.postOnCoreThread {
            computeCallLogsList(currentFilter)
        }
    }

    @WorkerThread
    private fun computeCallLogsList(filter: String) {
        if (coreContext.core.globalState != GlobalState.On) {
            Log.e("$TAG Core isn't ON yet, do not attempt to get calls history")
            return
        }

        if (callLogs.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

        val isFilterEmpty = filter.isEmpty()
        if (!isFilterEmpty) {
            magicSearch.getContactsListAsync(
                filter,
                corePreferences.contactsFilter,
                MagicSearch.Source.All.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }

        val list = arrayListOf<CallLogModelWrapper>()

        val account = LinphoneUtils.getDefaultAccount()
        // Fetch all call logs if only one account to workaround no history issue
        // TODO FIXME: remove workaround later
        val logs = if (coreContext.core.accountList.size > 1) {
            account?.callLogs ?: coreContext.core.callLogs
        } else {
            coreContext.core.callLogs
        }

        for (callLog in logs) {
            val model = CallLogModel(callLog)
            if (isCallLogMatchingFilter(model, filter)) {
                list.add(CallLogModelWrapper(model))
            }
        }

        Log.i("$TAG Fetched [${list.size}] call log(s)")
        if (isFilterEmpty) {
            callLogs.postValue(list)
        } else {
            fetchInProgress.postValue(true)
            tempCallLogsList.clear()
            tempCallLogsList.addAll(list)
        }
    }

    @WorkerThread
    private fun isCallLogMatchingFilter(model: CallLogModel, filter: String): Boolean {
        if (filter.isEmpty()) return true

        val friendName = model.avatarModel.friend.name ?: LinphoneUtils.getDisplayName(model.address)
        return friendName.contains(filter, ignoreCase = true) || model.address.asStringUriOnly().contains(filter, ignoreCase = true)
    }

    @WorkerThread
    private fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("$TAG Processing [${results.size}] results")

        val contactsList = arrayListOf<CallLogModelWrapper>()
        val suggestionsList = arrayListOf<CallLogModelWrapper>()
        val requestList = arrayListOf<CallLogModelWrapper>()

        val defaultAccountDomain = LinphoneUtils.getDefaultAccount()?.params?.domain
        for (result in results) {
            val address = result.address
            val friend = result.friend
            if (friend != null) {
                val found = contactsList.find { it.contactModel?.friend == friend }
                if (found != null) continue

                val mainAddress = address ?: LinphoneUtils.getFirstAvailableAddressForFriend(friend)
                if (mainAddress != null) {
                    val model = ConversationContactOrSuggestionModel(mainAddress, friend = friend)
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForFriend(
                        friend
                    )
                    model.avatarModel.postValue(avatarModel)
                    contactsList.add(CallLogModelWrapper(null, model))
                } else {
                    Log.w("$TAG Found friend [${friend.name}] in search results but no Address could be found, skipping it")
                }
            } else if (address != null) {
                if (result.sourceFlags == MagicSearch.Source.Request.toInt()) {
                    val model = ConversationContactOrSuggestionModel(address) {
                        coreContext.startAudioCall(address)
                    }
                    val avatarModel = getContactAvatarModelForAddress(address)
                    model.avatarModel.postValue(avatarModel)
                    requestList.add(CallLogModelWrapper(null, model))
                    continue
                }

                val defaultAccountAddress = coreContext.core.defaultAccount?.params?.identityAddress
                if (defaultAccountAddress != null && address.weakEqual(defaultAccountAddress)) {
                    Log.i("$TAG Removing from suggestions current default account address")
                    continue
                }

                val model = ConversationContactOrSuggestionModel(address, defaultAccountDomain = defaultAccountDomain) {
                    coreContext.startAudioCall(address)
                }
                val avatarModel = getContactAvatarModelForAddress(address)
                model.avatarModel.postValue(avatarModel)
                suggestionsList.add(CallLogModelWrapper(null, model))
            }
        }

        val collator = Collator.getInstance(Locale.getDefault())
        contactsList.sortWith { model1, model2 ->
            collator.compare(model1.contactModel?.name, model2.contactModel?.name)
        }
        suggestionsList.sortWith { model1, model2 ->
            collator.compare(model1.contactModel?.name, model2.contactModel?.name)
        }

        val list = arrayListOf<CallLogModelWrapper>()
        list.addAll(tempCallLogsList)
        list.addAll(contactsList)
        list.addAll(suggestionsList)
        list.addAll(requestList)
        callLogs.postValue(list)
        Log.i(
            "$TAG Processed [${results.size}] results: [${contactsList.size}] contacts and [${suggestionsList.size}] suggestions"
        )
    }

    @WorkerThread
    private fun getContactAvatarModelForAddress(address: Address): ContactAvatarModel {
        val fakeFriend = coreContext.core.createFriend()
        fakeFriend.name = LinphoneUtils.getDisplayName(address)
        fakeFriend.address = address
        return ContactAvatarModel(fakeFriend)
    }
}

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
package org.linphone.ui.main.calls.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactsListener
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.calls.model.SuggestionModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class StartCallViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Start Call ViewModel]"
    }

    val searchFilter = MutableLiveData<String>()

    val emptyContactsList = MutableLiveData<Boolean>()

    val suggestionsList = MutableLiveData<ArrayList<SuggestionModel>>()

    val onSuggestionClickedEvent: MutableLiveData<Event<Address>> by lazy {
        MutableLiveData<Event<Address>>()
    }

    private var currentFilter = ""
    private var previousFilter = "NotSet"
    private var limitSearchToLinphoneAccounts = true

    private lateinit var magicSearch: MagicSearch

    private val magicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            Log.i("$TAG Magic search contacts available")
            processMagicSearchResults(magicSearch.lastSearch)
        }
    }

    private val contactsListener = object : ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            applyFilter(
                currentFilter,
                if (limitSearchToLinphoneAccounts) LinphoneApplication.corePreferences.defaultDomain else "",
                MagicSearch.Source.CallLogs.toInt() or MagicSearch.Source.ChatRooms.toInt() or MagicSearch.Source.Request.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            val defaultAccount = core.defaultAccount
            limitSearchToLinphoneAccounts = defaultAccount?.isInSecureMode() ?: false

            coreContext.contactsManager.addListener(contactsListener)
            magicSearch = core.createMagicSearch()
            magicSearch.limitedSearch = false
            magicSearch.addListener(magicSearchListener)
        }

        applyFilter(currentFilter)
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread {
            magicSearch.removeListener(magicSearchListener)
            coreContext.contactsManager.removeListener(contactsListener)
        }
        super.onCleared()
    }

    @UiThread
    fun clearFilter() {
        searchFilter.value = ""
    }

    @WorkerThread
    fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("$TAG Processing [${results.size}] results")

        val list = arrayListOf<SuggestionModel>()
        for (result in results) {
            val address = result.address

            if (address != null) {
                val friend = coreContext.core.findFriend(address)
                // We don't want Friends here as they would also be in contacts list
                if (friend == null) {
                    // If user-input generated result (always last) already exists, don't show it again
                    if (result.sourceFlags == MagicSearch.Source.Request.toInt()) {
                        val found = list.find {
                            it.address.weakEqual(address)
                        }
                        if (found != null) {
                            Log.i(
                                "$TAG Result generated from user input is a duplicate of an existing solution, preventing double"
                            )
                            continue
                        }
                    }

                    val model = SuggestionModel(address) {
                        onSuggestionClickedEvent.value = Event(it)
                    }
                    list.add(model)
                }
            }
        }

        suggestionsList.postValue(list)
        Log.i("$TAG Processed [${results.size}] results, extracted [${list.size}] suggestions")
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            applyFilter(
                filter,
                if (limitSearchToLinphoneAccounts) LinphoneApplication.corePreferences.defaultDomain else "",
                MagicSearch.Source.CallLogs.toInt() or MagicSearch.Source.ChatRooms.toInt() or MagicSearch.Source.Request.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }
    }

    @WorkerThread
    private fun applyFilter(
        filter: String,
        domain: String,
        sources: Int,
        aggregation: MagicSearch.Aggregation
    ) {
        if (previousFilter.isNotEmpty() && (
            previousFilter.length > filter.length ||
                (previousFilter.length == filter.length && previousFilter != filter)
            )
        ) {
            magicSearch.resetSearchCache()
        }
        currentFilter = filter
        previousFilter = filter

        Log.i(
            "$TAG Asking Magic search for contacts matching filter [$filter], domain [$domain] and in sources [$sources]"
        )
        magicSearch.getContactsListAsync(
            filter,
            domain,
            sources,
            aggregation
        )
    }

    @WorkerThread
    private fun createFriendFromSearchResult(searchResult: SearchResult): Friend {
        val friend = coreContext.core.createFriend()

        val address = searchResult.address
        if (address != null) {
            friend.address = address

            friend.name = LinphoneUtils.getDisplayName(address)
            friend.refKey = address.asStringUriOnly().hashCode().toString()
        }

        return friend
    }
}

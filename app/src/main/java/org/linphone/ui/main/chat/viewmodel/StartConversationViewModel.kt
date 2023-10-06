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
package org.linphone.ui.main.chat.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contacts.ContactsManager.ContactsListener
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.history.model.ContactOrSuggestionModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.LinphoneUtils

class StartConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Start Conversation ViewModel]"
    }

    val searchFilter = MutableLiveData<String>()

    val contactsList = MutableLiveData<ArrayList<ContactOrSuggestionModel>>()

    val hideGroupChatButton = MutableLiveData<Boolean>()

    val isGroupChatAvailable = MutableLiveData<Boolean>()

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
                if (limitSearchToLinphoneAccounts) corePreferences.defaultDomain else "",
                MagicSearch.Source.Friends.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }
    }

    init {
        updateGroupChatButtonVisibility()

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

    @UiThread
    fun updateGroupChatButtonVisibility() {
        coreContext.postOnCoreThread { core ->
            val hideGroupChat = !LinphoneUtils.isGroupChatAvailable(core)
            hideGroupChatButton.postValue(hideGroupChat)
        }
    }

    @WorkerThread
    fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("$TAG Processing [${results.size}] results")

        val contactsList = arrayListOf<ContactOrSuggestionModel>()
        var previousLetter = ""

        for (result in results) {
            val address = result.address
            if (address != null) {
                val friend = coreContext.contactsManager.findContactByAddress(address)
                if (friend != null) {
                    val model = ContactOrSuggestionModel(address, friend)
                    model.contactAvatarModel = ContactAvatarModel(friend)

                    val currentLetter = friend.name?.get(0).toString()
                    val displayLetter = previousLetter.isEmpty() || currentLetter != previousLetter
                    if (currentLetter != previousLetter) {
                        previousLetter = currentLetter
                    }
                    model.contactAvatarModel.firstContactStartingByThatLetter.postValue(
                        displayLetter
                    )

                    contactsList.add(model)
                }
            }
        }

        val list = arrayListOf<ContactOrSuggestionModel>()
        list.addAll(contactsList)
        this.contactsList.postValue(list)
        Log.i("$TAG Processed [${results.size}] results, extracted [${list.size}] suggestions")
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            applyFilter(
                filter,
                if (limitSearchToLinphoneAccounts) corePreferences.defaultDomain else "",
                MagicSearch.Source.Friends.toInt(),
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
}

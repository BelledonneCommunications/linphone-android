/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.contact

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.viewmodels.MessageNotifierViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

open class ContactsSelectionViewModel : MessageNotifierViewModel() {
    val contactsList = MutableLiveData<ArrayList<SearchResult>>()

    val sipContactsSelected = MutableLiveData<Boolean>()

    val selectedAddresses = MutableLiveData<ArrayList<Address>>()

    val filter = MutableLiveData<String>()
    private var previousFilter = "NotSet"

    val fetchInProgress = MutableLiveData<Boolean>()
    private var searchResultsPending: Boolean = false
    private var fastFetchJob: Job? = null

    val moreResultsAvailableEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Contacts Selection] Contacts have changed")
            applyFilter()
        }
    }

    private val magicSearchListener = object : MagicSearchListenerStub() {
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            searchResultsPending = false
            processMagicSearchResults(magicSearch.lastSearch)
            fetchInProgress.value = false
        }

        override fun onLdapHaveMoreResults(magicSearch: MagicSearch, ldap: Ldap) {
            moreResultsAvailableEvent.value = Event(true)
        }
    }

    init {
        sipContactsSelected.value = coreContext.contactsManager.shouldDisplaySipContactsList()

        selectedAddresses.value = arrayListOf()

        coreContext.contactsManager.addListener(contactsUpdatedListener)
        coreContext.contactsManager.magicSearch.addListener(magicSearchListener)
    }

    override fun onCleared() {
        coreContext.contactsManager.magicSearch.removeListener(magicSearchListener)
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    fun applyFilter() {
        val filterValue = filter.value.orEmpty()

        if (previousFilter.isNotEmpty() && (
            previousFilter.length > filterValue.length ||
                (previousFilter.length == filterValue.length && previousFilter != filterValue)
            )
        ) {
            coreContext.contactsManager.magicSearch.resetSearchCache()
        }
        previousFilter = filterValue

        val domain = if (sipContactsSelected.value == true) coreContext.core.defaultAccount?.params?.domain ?: "" else ""
        searchResultsPending = true
        fastFetchJob?.cancel()
        coreContext.contactsManager.magicSearch.getContactsListAsync(filter.value.orEmpty(), domain, MagicSearchSource.All.toInt(), MagicSearchAggregation.None)

        val spinnerDelay = corePreferences.delayBeforeShowingContactsSearchSpinner.toLong()
        fastFetchJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                delay(spinnerDelay)
                withContext(Dispatchers.Main) {
                    if (searchResultsPending) {
                        fetchInProgress.value = true
                    }
                }
            }
        }
    }

    fun clearFilter() {
        filter.value = ""
    }

    fun toggleSelectionForSearchResult(searchResult: SearchResult) {
        val address = searchResult.address
        if (address != null) {
            toggleSelectionForAddress(address)
        }
    }

    fun toggleSelectionForAddress(address: Address) {
        val list = arrayListOf<Address>()
        list.addAll(selectedAddresses.value.orEmpty())

        val found = list.find {
            it.weakEqual(address)
        }

        if (found != null) {
            list.remove(found)
        } else {
            val contact = coreContext.contactsManager.findContactByAddress(address)
            if (contact != null) {
                val clone = address.clone()
                clone.displayName = contact.name
                list.add(clone)
            } else {
                list.add(address)
            }
        }

        selectedAddresses.value = list
    }

    private fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("[Contacts Selection] Processing ${results.size} results")
        val list = arrayListOf<SearchResult>()
        for (result in results) {
            list.add(result)
        }
        contactsList.postValue(list)
    }
}

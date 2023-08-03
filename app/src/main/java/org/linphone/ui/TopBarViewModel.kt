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
package org.linphone.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.utils.Event

abstract class TopBarViewModel : ViewModel() {
    val title = MutableLiveData<String>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var previousFilter = "NotSet"

    private lateinit var magicSearch: MagicSearch

    private val magicSearchListener = object : MagicSearchListenerStub() {
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            // Core thread
            Log.i("[Contacts] Magic search contacts available")
            processMagicSearchResults(magicSearch.lastSearch)
        }
    }

    init {
        searchBarVisible.value = false
        coreContext.postOnCoreThread { core ->
            magicSearch = core.createMagicSearch()
            magicSearch.limitedSearch = false
            magicSearch.addListener(magicSearchListener)
        }
    }

    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            magicSearch.removeListener(magicSearchListener)
        }
        super.onCleared()
    }

    fun openSearchBar() {
        // UI thread
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    fun closeSearchBar() {
        // UI thread
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    fun clearFilter() {
        // UI thread
        searchFilter.value = ""
    }

    fun applyFilter(domain: String, sources: Int, aggregation: MagicSearch.Aggregation) {
        // Core thread
        val filterValue = searchFilter.value.orEmpty()
        if (previousFilter.isNotEmpty() && (
            previousFilter.length > filterValue.length ||
                (previousFilter.length == filterValue.length && previousFilter != filterValue)
            )
        ) {
            magicSearch.resetSearchCache()
        }
        previousFilter = filterValue

        Log.i(
            "[Contacts] Asking Magic search for contacts matching filter [$filterValue], domain [$domain] and in sources [$sources]"
        )
        magicSearch.getContactsListAsync(
            filterValue,
            domain,
            sources,
            aggregation
        )
    }

    abstract fun processMagicSearchResults(results: Array<SearchResult>)
}

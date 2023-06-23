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
package org.linphone.ui.conversations

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log

class NewConversationViewModel : ViewModel() {
    val contactsList = MutableLiveData<ArrayList<SearchResult>>()

    val filter = MutableLiveData<String>()
    private var previousFilter = "NotSet"

    private val magicSearch: MagicSearch by lazy {
        val magicSearch = coreContext.core.createMagicSearch()
        magicSearch.limitedSearch = false
        magicSearch
    }

    private val magicSearchListener = object : MagicSearchListenerStub() {
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            processMagicSearchResults(magicSearch.lastSearch)
        }
    }

    init {
        magicSearch.addListener(magicSearchListener)
        applyFilter("")
    }

    override fun onCleared() {
        magicSearch.removeListener(magicSearchListener)
        super.onCleared()
    }

    fun applyFilter(filterValue: String) {
        Log.i("[New Conversation ViewModel] Filtering contacts using [$filterValue]")
        if (previousFilter.isNotEmpty() && (
            previousFilter.length > filterValue.length ||
                (previousFilter.length == filterValue.length && previousFilter != filterValue)
            )
        ) {
            coreContext.postOnCoreThread { core ->
                magicSearch.resetSearchCache()
            }
        }
        previousFilter = filterValue

        coreContext.postOnCoreThread { core ->
            magicSearch.getContactsListAsync(
                filterValue,
                "",
                MagicSearch.Source.Friends.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }
    }

    private fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("[New Conversation ViewModel] [${results.size}] matching results")
        val list = arrayListOf<SearchResult>()
        list.addAll(results)
        contactsList.postValue(list)
    }
}

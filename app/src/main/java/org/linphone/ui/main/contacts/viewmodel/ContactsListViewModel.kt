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
package org.linphone.ui.main.contacts.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contacts.ContactsListener
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel

class ContactsListViewModel : ViewModel() {
    companion object {
        const val TAG = "[Contacts List ViewModel]"
    }

    val contactsList = MutableLiveData<ArrayList<ContactAvatarModel>>()

    val favourites = MutableLiveData<ArrayList<ContactAvatarModel>>()

    val showFavourites = MutableLiveData<Boolean>()

    val isListFiltered = MutableLiveData<Boolean>()

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
                MagicSearch.Source.Friends.toInt() or MagicSearch.Source.LdapServers.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }
    }

    init {
        showFavourites.value = true

        coreContext.postOnCoreThread { core ->
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
    fun toggleFavouritesVisibility() {
        showFavourites.value = showFavourites.value == false
    }

    @WorkerThread
    fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("$TAG Processing [${results.size}] results")
        contactsList.value.orEmpty().forEach(ContactAvatarModel::destroy)

        val list = arrayListOf<ContactAvatarModel>()
        val favouritesList = arrayListOf<ContactAvatarModel>()
        var previousLetter = ""

        for (result in results) {
            val friend = result.friend

            var currentLetter = ""
            val model = if (friend != null) {
                currentLetter = friend.name?.get(0).toString()
                ContactAvatarModel(friend)
            } else {
                Log.w("$TAG SearchResult [$result] has no Friend!")
                val fakeFriend =
                    createFriendFromSearchResult(result)
                currentLetter = fakeFriend.name?.get(0).toString()
                ContactAvatarModel(fakeFriend)
            }

            val displayLetter = previousLetter.isEmpty() || currentLetter != previousLetter
            if (currentLetter != previousLetter) {
                previousLetter = currentLetter
            }
            model.firstContactStartingByThatLetter.postValue(displayLetter)

            list.add(model)
            if (friend?.starred == true) {
                favouritesList.add(model)
            }
        }

        favourites.postValue(favouritesList)
        contactsList.postValue(list)

        Log.i("$TAG Processed [${results.size}] results")
    }

    @UiThread
    fun applyFilter(filter: String) {
        isListFiltered.value = filter.isNotEmpty()
        coreContext.postOnCoreThread {
            applyFilter(
                filter,
                if (limitSearchToLinphoneAccounts) corePreferences.defaultDomain else "",
                MagicSearch.Source.Friends.toInt() or MagicSearch.Source.LdapServers.toInt(),
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
        val searchResultFriend = searchResult.friend
        if (searchResultFriend != null) return searchResultFriend

        val friend = coreContext.core.createFriend()

        val address = searchResult.address
        if (address != null) {
            friend.address = address
        }

        val number = searchResult.phoneNumber
        if (number != null) {
            friend.addPhoneNumber(number)

            if (address != null && address.username == number) {
                friend.removeAddress(address)
            }
        }

        return friend
    }
}

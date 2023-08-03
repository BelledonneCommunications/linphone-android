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
package org.linphone.ui.contacts.viewmodel

import androidx.lifecycle.MutableLiveData
import java.util.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactsListener
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.TopBarViewModel
import org.linphone.ui.contacts.model.ContactModel

class ContactsListViewModel : TopBarViewModel() {
    val bottomNavBarVisible = MutableLiveData<Boolean>()

    val contactsList = MutableLiveData<ArrayList<ContactModel>>()

    private val contactsListener = object : ContactsListener {
        override fun onContactsLoaded() {
            // Core thread
            applyFilter()
        }
    }

    init {
        title.value = "Contacts"
        bottomNavBarVisible.value = true
        coreContext.postOnCoreThread {
            coreContext.contactsManager.addListener(contactsListener)
        }
        applyFilter()
    }

    override fun onCleared() {
        coreContext.postOnCoreThread {
            coreContext.contactsManager.removeListener(contactsListener)
        }
        super.onCleared()
    }

    override fun processMagicSearchResults(results: Array<SearchResult>) {
        // Core thread
        Log.i("[Contacts List] Processing ${results.size} results")
        contactsList.value.orEmpty().forEach(ContactModel::destroy)

        val list = arrayListOf<ContactModel>()

        for (result in results) {
            val friend = result.friend

            val viewModel = if (friend != null) {
                ContactModel(friend)
            } else {
                Log.w("[Contacts] SearchResult [$result] has no Friend!")
                val fakeFriend =
                    createFriendFromSearchResult(result)
                ContactModel(fakeFriend)
            }

            list.add(viewModel)
        }

        contactsList.postValue(list)

        Log.i("[Contacts] Processed ${results.size} results")
    }

    fun applyFilter() {
        coreContext.postOnCoreThread {
            applyFilter(
                "",
                MagicSearch.Source.Friends.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }
    }

    private fun createFriendFromSearchResult(searchResult: SearchResult): Friend {
        // Core thread
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

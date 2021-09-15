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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.viewmodels.MessageNotifierViewModel
import org.linphone.core.Address
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log

open class ContactsSelectionViewModel : MessageNotifierViewModel() {
    val contactsList = MutableLiveData<ArrayList<SearchResult>>()

    val sipContactsSelected = MutableLiveData<Boolean>()

    val selectedAddresses = MutableLiveData<ArrayList<Address>>()

    val filter = MutableLiveData<String>()
    private var previousFilter = ""

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Contacts Selection] Contacts have changed")
            updateContactsList()
        }
    }

    init {
        sipContactsSelected.value = coreContext.contactsManager.shouldDisplaySipContactsList()

        selectedAddresses.value = arrayListOf()

        coreContext.contactsManager.addListener(contactsUpdatedListener)
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    fun applyFilter() {
        val filterValue = filter.value.orEmpty()
        if (previousFilter == filterValue) return

        if (previousFilter.isNotEmpty() && previousFilter.length > filterValue.length) {
            coreContext.contactsManager.magicSearch.resetSearchCache()
        }
        previousFilter = filterValue

        updateContactsList()
    }

    fun updateContactsList() {
        val domain = if (sipContactsSelected.value == true) coreContext.core.defaultAccount?.params?.domain ?: "" else ""
        val results = coreContext.contactsManager.magicSearch.getContactListFromFilter(filter.value.orEmpty(), domain)

        val list = arrayListOf<SearchResult>()
        for (result in results) {
            list.add(result)
        }
        contactsList.value = list
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
                clone.displayName = contact.fullName
                list.add(clone)
            } else {
                list.add(address)
            }
        }

        selectedAddresses.value = list
    }
}

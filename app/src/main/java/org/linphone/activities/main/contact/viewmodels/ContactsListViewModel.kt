/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.contact.viewmodels

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.*
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contact.Contact
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.contact.NativeContact
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class ContactsListViewModel : ViewModel() {
    val sipContactsSelected = MutableLiveData<Boolean>()

    val contactsList = MutableLiveData<ArrayList<ContactViewModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()
    private var searchResultsPending: Boolean = false
    private var fastFetchJob: Job? = null

    val filter = MutableLiveData<String>()
    private var previousFilter = "NotSet"

    val moreResultsAvailableEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Contacts] Contacts have changed")
            updateContactsList(true)
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
        fetchInProgress.value = false

        coreContext.contactsManager.addListener(contactsUpdatedListener)
        coreContext.contactsManager.magicSearch.addListener(magicSearchListener)
    }

    override fun onCleared() {
        contactsList.value.orEmpty().forEach(ContactViewModel::destroy)
        coreContext.contactsManager.magicSearch.removeListener(magicSearchListener)
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    fun updateContactsList(clearCache: Boolean) {
        val filterValue = filter.value.orEmpty()

        if (clearCache || (
            previousFilter.isNotEmpty() && (
                previousFilter.length > filterValue.length ||
                    (previousFilter.length == filterValue.length && previousFilter != filterValue)
                )
            )
        ) {
            coreContext.contactsManager.magicSearch.resetSearchCache()
        }
        previousFilter = filterValue

        val domain = if (sipContactsSelected.value == true) coreContext.core.defaultAccount?.params?.domain ?: "" else ""
        val filter = MagicSearchSource.Friends.toInt() or MagicSearchSource.LdapServers.toInt()
        searchResultsPending = true
        fastFetchJob?.cancel()
        coreContext.contactsManager.magicSearch.getContactsAsync(filterValue, domain, filter)

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

    private fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("[Contacts] Processing ${results.size} results")
        contactsList.value.orEmpty().forEach(ContactViewModel::destroy)

        val list = arrayListOf<ContactViewModel>()
        for (result in results) {
            val contact = searchMatchingContact(result) ?: Contact(searchResult = result)
            if (contact is NativeContact) {
                val found = list.find { contactViewModel ->
                    contactViewModel.contactInternal is NativeContact && contactViewModel.contactInternal.nativeId == contact.nativeId
                }
                if (found != null) {
                    Log.d("[Contacts] Found a search result that matches a native contact [$contact] we already have, skipping")
                    continue
                }
            } else {
                val found = list.find { contactViewModel ->
                    contactViewModel.displayName.value == contact.fullName
                }
                if (found != null) {
                    Log.i("[Contacts] Found a search result that matches a contact [$contact] we already have, updating it with the new information")
                    found.contactInternal.addAddressAndPhoneNumberFromSearchResult(result)
                    found.updateNumbersAndAddresses(found.contactInternal)
                    continue
                }
            }
            list.add(ContactViewModel(contact))
        }

        contactsList.postValue(list)
    }

    fun deleteContact(contact: Contact?) {
        contact ?: return

        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = ArrayList<ContentProviderOperation>()

        if (contact is NativeContact) {
            val nativeContact: NativeContact = contact
            Log.i("[Contacts] Adding Android contact id ${nativeContact.nativeId} to batch removal")
            val args = arrayOf(nativeContact.nativeId)
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection(select, args)
                    .build()
            )
        }

        if (contact.friend != null) {
            Log.i("[Contacts] Removing friend")
            contact.friend?.remove()
        }

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contacts] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contacts] $e")
            }
        }
    }

    fun deleteContacts(list: ArrayList<Contact>) {
        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = ArrayList<ContentProviderOperation>()

        for (contact in list) {
            if (contact is NativeContact) {
                val nativeContact: NativeContact = contact
                Log.i("[Contacts] Adding Android contact id ${nativeContact.nativeId} to batch removal")
                val args = arrayOf(nativeContact.nativeId)
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection(select, args)
                        .build()
                )
            }

            if (contact.friend != null) {
                Log.i("[Contacts] Removing friend")
                contact.friend?.remove()
            }
        }

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contacts] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contacts] $e")
            }
        }
    }

    private fun searchMatchingContact(searchResult: SearchResult): Contact? {
        val friend = searchResult.friend
        var displayName = ""
        if (friend != null) {
            displayName = friend.name ?: ""
            val contact: Contact? = friend.userData as? Contact
            if (contact != null) return contact

            val friendContact = coreContext.contactsManager.findContactByFriend(friend)
            if (friendContact != null) return friendContact
        }

        val address = searchResult.address
        if (address != null) {
            if (displayName.isEmpty()) displayName = address.displayName ?: ""
            val contact = coreContext.contactsManager.findContactByAddress(address, ignoreLocalContact = true)
            if (contact != null && (displayName.isEmpty() || contact.fullName == displayName)) return contact
        }

        val phoneNumber = searchResult.phoneNumber
        if (phoneNumber != null && address?.username != phoneNumber) {
            val contact = coreContext.contactsManager.findContactByPhoneNumber(phoneNumber)
            if (contact != null && (displayName.isEmpty() || contact.fullName == displayName)) return contact
        }

        return null
    }
}

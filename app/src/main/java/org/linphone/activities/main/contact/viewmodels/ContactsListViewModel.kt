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
import kotlin.collections.HashMap
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

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
            Log.i("[Contacts Loader] Magic search contacts available")
            searchResultsPending = false
            processMagicSearchResults(magicSearch.lastSearch)
            // Use coreContext.contactsManager.fetchInProgress instead of false in case contacts are still being loaded
            fetchInProgress.value = coreContext.contactsManager.fetchInProgress.value
        }

        override fun onLdapHaveMoreResults(magicSearch: MagicSearch, ldap: Ldap) {
            moreResultsAvailableEvent.value = Event(true)
        }
    }

    init {
        sipContactsSelected.value = coreContext.contactsManager.shouldDisplaySipContactsList()

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
        Log.i("[Contacts Loader] Asking Magic search for contacts matching filter [$filterValue], domain [$domain] and in sources [$filter]")
        coreContext.contactsManager.magicSearch.getContactsAsync(filterValue, domain, filter)

        val spinnerDelay = corePreferences.delayBeforeShowingContactsSearchSpinner.toLong()
        fastFetchJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                delay(spinnerDelay)
            }
            withContext(Dispatchers.Main) {
                if (searchResultsPending) {
                    fetchInProgress.value = true
                }
            }
        }
    }

    private fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("[Contacts Loader] Processing ${results.size} results")
        contactsList.value.orEmpty().forEach(ContactViewModel::destroy)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val list = arrayListOf<ContactViewModel>()
                val viewModels = HashMap<String, ContactViewModel>()

                for (result in results) {
                    val friend = result.friend
                    val name = friend?.name ?: LinphoneUtils.getDisplayName(result.address)
                    val found = viewModels[name]
                    if (found != null && friend != null) {
                        continue
                    }

                    val viewModel = if (friend != null) {
                        ContactViewModel(friend, true)
                    } else {
                        val fakeFriend = coreContext.contactsManager.createFriendFromSearchResult(result)
                        ContactViewModel(fakeFriend, true)
                    }

                    list.add(viewModel)
                    if (found == null) {
                        viewModels[name] = viewModel
                    }
                }

                contactsList.postValue(list)
                viewModels.clear()
            }

            withContext(Dispatchers.Main) {
                Log.i("[Contacts Loader] Processed ${results.size} results")
            }
        }
    }

    fun deleteContact(friend: Friend) {
        friend.remove() // TODO: FIXME: friend is const here!

        val id = friend.refKey
        if (id == null) {
            Log.w("[Contacts] Friend has no refkey, can't delete it from native address book")
            return
        }

        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = ArrayList<ContentProviderOperation>()

        Log.i("[Contacts] Adding Android contact id $id to batch removal")
        val args = arrayOf(id)
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                .withSelection(select, args)
                .build()
        )

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contacts] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contacts] $e")
            }
        }
    }

    fun deleteContacts(list: ArrayList<Friend>) {
        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = ArrayList<ContentProviderOperation>()

        for (friend in list) {
            val id = friend.refKey
            if (id != null) {
                Log.i("[Contacts] Adding Android contact id $id to batch removal")
                val args = arrayOf(id)
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection(select, args)
                        .build()
                )
            }
            friend.remove()
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
}

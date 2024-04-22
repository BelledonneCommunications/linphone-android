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
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.Collator
import java.util.ArrayList
import java.util.Locale
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contacts.ContactsManager.ContactsListener
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class ContactsListViewModel @UiThread constructor() : AbstractTopBarViewModel() {
    companion object {
        private const val TAG = "[Contacts List ViewModel]"
    }

    val contactsList = MutableLiveData<ArrayList<ContactAvatarModel>>()

    val favourites = MutableLiveData<ArrayList<ContactAvatarModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val showFavourites = MutableLiveData<Boolean>()

    val isListFiltered = MutableLiveData<Boolean>()

    val isDefaultAccountLinphone = MutableLiveData<Boolean>()

    val vCardTerminatedEvent: MutableLiveData<Event<Pair<String, File>>> by lazy {
        MutableLiveData<Event<Pair<String, File>>>()
    }

    private var previousFilter = "NotSet"
    private var domainFilter = ""

    private lateinit var magicSearch: MagicSearch

    private var firstLoad = true

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
            magicSearch.resetSearchCache()

            applyFilter(
                currentFilter,
                domainFilter
            )
        }
    }

    init {
        fetchInProgress.value = true
        showFavourites.value = true

        coreContext.postOnCoreThread { core ->
            updateDomainFilter()

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
    override fun filter() {
        isListFiltered.value = currentFilter.isNotEmpty()
        coreContext.postOnCoreThread {
            applyFilter(
                currentFilter,
                domainFilter
            )
        }
    }

    @UiThread
    fun applyCurrentDefaultAccountFilter() {
        coreContext.postOnCoreThread {
            updateDomainFilter()
        }

        applyFilter(currentFilter)
    }

    @UiThread
    fun changeContactsFilter(onlyLinphoneContacts: Boolean, onlySipContacts: Boolean) {
        domainFilter = if (onlyLinphoneContacts) {
            corePreferences.defaultDomain
        } else if (onlySipContacts) {
            "*"
        } else {
            ""
        }
        applyFilter(currentFilter)
    }

    fun areAllContactsDisplayed(): Boolean {
        return domainFilter.isEmpty()
    }

    @UiThread
    fun toggleFavouritesVisibility() {
        showFavourites.value = showFavourites.value == false
    }

    @WorkerThread
    private fun updateDomainFilter() {
        val defaultAccount = coreContext.core.defaultAccount
        isDefaultAccountLinphone.postValue(
            defaultAccount?.isInSecureMode() == true && defaultAccount.params.domain == corePreferences.defaultDomain
        )
        domainFilter = if (defaultAccount?.isInSecureMode() == true) {
            corePreferences.defaultDomain
        } else {
            "*"
        }
    }

    @UiThread
    fun exportContactAsVCard(friend: Friend) {
        coreContext.postOnCoreThread {
            val vCard = friend.vcard?.asVcard4String()
            if (!vCard.isNullOrEmpty()) {
                Log.i("$TAG Friend has been successfully dumped as vCard string")
                val fileName = friend.name.orEmpty().replace(" ", "_").lowercase(
                    Locale.getDefault()
                )
                val file = FileUtils.getFileStorageCacheDir(
                    "$fileName.vcf",
                    overrideExisting = true
                )
                viewModelScope.launch {
                    if (FileUtils.dumpStringToFile(vCard, file)) {
                        Log.i("$TAG vCard string saved as file in cache folder")
                        vCardTerminatedEvent.postValue(Event(Pair(friend.name.orEmpty(), file)))
                    } else {
                        Log.e("$TAG Failed to save vCard string as file in cache folder")
                    }
                }
            } else {
                Log.e("$TAG Failed to dump contact as vCard string")
            }
        }
    }

    @WorkerThread
    private fun applyFilter(
        filter: String,
        domain: String
    ) {
        if (contactsList.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

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
            "$TAG Asking Magic search for contacts matching filter [$filter], domain [$domain] and in sources Friends/LDAP"
        )
        magicSearch.getContactsListAsync(
            filter,
            domain,
            MagicSearch.Source.Friends.toInt() or MagicSearch.Source.LdapServers.toInt(),
            MagicSearch.Aggregation.Friend
        )
    }

    @WorkerThread
    private fun processMagicSearchResults(results: Array<SearchResult>) {
        // Do not call destroy() on previous list items as they are cached and will be re-used
        Log.i("$TAG Processing [${results.size}] results")

        val list = arrayListOf<ContactAvatarModel>()
        val favouritesList = arrayListOf<ContactAvatarModel>()
        var count = 0

        for (result in results) {
            val friend = result.friend
            if (friend != null && result.sourceFlags == MagicSearch.Source.LdapServers.toInt()) {
                // TODO FIXME: Fix issue in SDK, each LDAP friend should have a proper refKey
                friend.refKey = friend.name
            }

            val model = if (friend != null) {
                coreContext.contactsManager.getContactAvatarModelForFriend(friend)
            } else {
                coreContext.contactsManager.getContactAvatarModelForAddress(result.address)
            }

            list.add(model)
            count += 1

            val starred = friend?.starred ?: false
            model.isFavourite.postValue(starred)
            if (starred) {
                favouritesList.add(model)
            }

            if (firstLoad && count == 20) {
                contactsList.postValue(list)
            }
        }

        val collator = Collator.getInstance(Locale.getDefault())
        favouritesList.sortWith { model1, model2 ->
            collator.compare(model1.friend.name, model2.friend.name)
        }
        list.sortWith { model1, model2 ->
            collator.compare(model1.friend.name, model2.friend.name)
        }

        favourites.postValue(favouritesList)
        contactsList.postValue(list)

        Log.i("$TAG Processed [${results.size}] results")
        firstLoad = false
    }
}

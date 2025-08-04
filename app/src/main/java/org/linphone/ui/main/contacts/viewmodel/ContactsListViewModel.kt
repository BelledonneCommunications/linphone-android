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
import org.linphone.R
import org.linphone.contacts.ContactsManager.ContactsListener
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.viewmodel.AbstractMainViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class ContactsListViewModel
    @UiThread
    constructor() : AbstractMainViewModel() {
    companion object {
        private const val TAG = "[Contacts List ViewModel]"
    }

    val contactsList = MutableLiveData<ArrayList<ContactAvatarModel>>()

    val favouritesList = MutableLiveData<ArrayList<ContactAvatarModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val showFavourites = MutableLiveData<Boolean>()

    val showFilter = MutableLiveData<Boolean>()

    val isListFiltered = MutableLiveData<Boolean>()

    val areAllContactsDisplayed = MutableLiveData<Boolean>()

    val searchInProgress = MutableLiveData<Boolean>()

    val isDefaultAccountLinphone = MutableLiveData<Boolean>()

    val showResultsLimitReached = MutableLiveData<Boolean>()

    val vCardTerminatedEvent: MutableLiveData<Event<Pair<String, File>>> by lazy {
        MutableLiveData<Event<Pair<String, File>>>()
    }

    private var previousFilter = "NotSet"
    private var domainFilter = ""

    private lateinit var magicSearch: MagicSearch

    private lateinit var favouritesMagicSearch: MagicSearch

    private var firstLoad = true

    private val magicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            Log.i("$TAG Magic search contacts available")
            processMagicSearchResults(magicSearch.lastSearch, favourites = false)
        }

        @WorkerThread
        override fun onResultsLimitReached(magicSearch: MagicSearch, sourcesFlag: Int) {
            Log.w("$TAG Results limit reached (configured limit is [${magicSearch.searchLimit}]) for source(s) [$sourcesFlag], user should refine it's search")
            if (searchFilter.value.orEmpty().isNotEmpty()) {
                showResultsLimitReached.postValue(true)
            }
        }
    }

    private val favouritesMagicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            Log.i("$TAG Magic search favourites contacts available")
            processMagicSearchResults(magicSearch.lastSearch, favourites = true)
        }
    }

    private val contactsListener = object : ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            magicSearch.resetSearchCache()
            favouritesMagicSearch.resetSearchCache()

            applyFilter(
                currentFilter,
                domainFilter
            )
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        fetchInProgress.value = true
        showFavourites.value = corePreferences.showFavoriteContacts
        showFilter.value = !corePreferences.hidePhoneNumbers && !corePreferences.hideSipAddresses

        coreContext.postOnCoreThread { core ->
            domainFilter = corePreferences.contactsFilter
            areAllContactsDisplayed.postValue(domainFilter.isEmpty())
            checkIfDefaultAccountOnDefaultDomain()

            coreContext.contactsManager.addListener(contactsListener)
            magicSearch = core.createMagicSearch()
            magicSearch.limitedSearch = true
            magicSearch.searchLimit = corePreferences.magicSearchResultsLimit
            magicSearch.addListener(magicSearchListener)

            favouritesMagicSearch = core.createMagicSearch()
            favouritesMagicSearch.limitedSearch = false
            favouritesMagicSearch.addListener(favouritesMagicSearchListener)

            coreContext.postOnMainThread {
                applyFilter(currentFilter)
            }
        }
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread {
            magicSearch.removeListener(magicSearchListener)
            favouritesMagicSearch.removeListener(favouritesMagicSearchListener)
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
            domainFilter = corePreferences.contactsFilter
            areAllContactsDisplayed.postValue(domainFilter.isEmpty())
            checkIfDefaultAccountOnDefaultDomain()

            coreContext.postOnMainThread {
                applyFilter(currentFilter)
            }
        }
    }

    @UiThread
    fun changeContactsFilter(onlyLinphoneContacts: Boolean, onlySipContacts: Boolean) {
        coreContext.postOnCoreThread {
            domainFilter = if (onlyLinphoneContacts) {
                corePreferences.defaultDomain
            } else if (onlySipContacts) {
                "*"
            } else {
                ""
            }
            areAllContactsDisplayed.postValue(domainFilter.isEmpty())
            corePreferences.contactsFilter = domainFilter
            Log.i("$TAG Newly set filter is [${corePreferences.contactsFilter}]")

            coreContext.postOnMainThread {
                applyFilter(currentFilter, domainFilter, filterChanged = true)
            }
        }
    }

    @UiThread
    fun toggleFavouritesVisibility() {
        val show = showFavourites.value == false
        showFavourites.value = show
        corePreferences.showFavoriteContacts = show
    }

    @UiThread
    fun exportContactAsVCard(friend: Friend) {
        coreContext.postOnCoreThread {
            val vCard = friend.dumpVcard()
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

    @UiThread
    fun toggleContactFavoriteFlag(contactModel: ContactAvatarModel) {
        coreContext.postOnCoreThread {
            contactModel.friend.edit()
            val starred = !contactModel.friend.starred
            Log.i(
                "$TAG Friend [${contactModel.name.value}] will be [${if (starred) "added to" else "removed from"}] favourites"
            )
            contactModel.friend.starred = starred
            contactModel.friend.done()
            coreContext.contactsManager.notifyContactsListChanged()
        }
    }

    @UiThread
    fun deleteContact(contactModel: ContactAvatarModel) {
        coreContext.postOnCoreThread {
            Log.w("$TAG Removing friend [${contactModel.contactName}]")
            coreContext.contactsManager.contactRemoved(contactModel.friend)
            contactModel.friend.remove()
            coreContext.contactsManager.notifyContactsListChanged()
            showGreenToast(R.string.contact_deleted_toast, R.drawable.warning_circle)
        }
    }

    @WorkerThread
    private fun applyFilter(
        filter: String,
        domain: String,
        filterChanged: Boolean = false
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
            "$TAG Asking Magic search for contacts matching filter [$filter], domain [$domain] and in sources Friends/LDAP/CardDAV"
        )
        searchInProgress.postValue(filter.isNotEmpty())
        showResultsLimitReached.postValue(false)

        if (filter.isEmpty() && (favouritesList.value.orEmpty().isEmpty() || filterChanged)) {
            favouritesMagicSearch.getContactsListAsync(
                filter,
                domain,
                MagicSearch.Source.FavoriteFriends.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }

        magicSearch.getContactsListAsync(
            filter,
            domain,
            MagicSearch.Source.Friends.toInt() or MagicSearch.Source.LdapServers.toInt() or MagicSearch.Source.RemoteCardDAV.toInt(),
            MagicSearch.Aggregation.Friend
        )
    }

    @WorkerThread
    private fun processMagicSearchResults(results: Array<SearchResult>, favourites: Boolean) {
        // Do not call destroy() on previous list items as they are cached and will be re-used
        Log.i("$TAG Processing [${results.size}] results, favourites is [$favourites]")

        val list = arrayListOf<ContactAvatarModel>()
        var count = 0
        val collator = Collator.getInstance(Locale.getDefault())
        val hideEmptyContacts = corePreferences.hideContactsWithoutPhoneNumberOrSipAddress

        for (result in results) {
            val friend = result.friend
            if (friend != null) {
                if (hideEmptyContacts && friend.addresses.isEmpty() && friend.phoneNumbers.isEmpty()) {
                    Log.i("$TAG Friend [${friend.name}] has no SIP address nor phone number, do not show it")
                    continue
                }

                if (friend.refKey.orEmpty().isEmpty()) {
                    if (friend.vcard != null) {
                        friend.vcard?.generateUniqueId()
                        friend.refKey = friend.vcard?.uid
                    } else {
                        Log.w(
                            "$TAG Friend [${friend.name}] found in SearchResults doesn't have a refKey, using name instead"
                        )
                        friend.refKey = friend.name
                    }
                }
            }

            val model = if (friend != null) {
                coreContext.contactsManager.getContactAvatarModelForFriend(friend)
            } else {
                coreContext.contactsManager.getContactAvatarModelForAddress(result.address)
            }
            model.refreshSortingName()

            list.add(model)
            count += 1

            val starred = friend?.starred == true
            model.isFavourite.postValue(starred)

            if (!favourites && firstLoad && count == 20) {
                list.sortWith { model1, model2 ->
                    collator.compare(model1.getNameToUseForSorting(), model2.getNameToUseForSorting())
                }
                contactsList.postValue(list)
            }
        }

        list.sortWith { model1, model2 ->
            collator.compare(model1.getNameToUseForSorting(), model2.getNameToUseForSorting())
        }

        searchInProgress.postValue(false)
        if (favourites) {
            favouritesList.postValue(list)
        } else {
            contactsList.postValue(list)
            firstLoad = false
        }

        Log.i("$TAG Processed [${results.size}] results into [${list.size} contacts]")
    }

    @WorkerThread
    private fun checkIfDefaultAccountOnDefaultDomain() {
        val defaultAccount = coreContext.core.defaultAccount
        val defaultDomain = corePreferences.defaultDomain
        val isAccountOnDefaultDomain = defaultAccount?.params?.domain == defaultDomain
        isDefaultAccountLinphone.postValue(isAccountOnDefaultDomain)
    }
}

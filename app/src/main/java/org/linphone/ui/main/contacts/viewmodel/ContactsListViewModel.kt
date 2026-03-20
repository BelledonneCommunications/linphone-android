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
import com.hansol.siphone.R
import org.linphone.constants.SHOW_CONTACTS_FILTER
import org.linphone.contacts.ContactsManager.ContactsListener
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.OrgCategoryModel
import org.linphone.ui.main.contacts.model.OrgListItem
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

    val mixedList = MutableLiveData<ArrayList<OrgListItem>>()

    val navigationPath = MutableLiveData<List<String>>(emptyList())

    val fetchInProgress = MutableLiveData<Boolean>()

    val showFavourites = MutableLiveData<Boolean>()

    val showFilter = MutableLiveData<Boolean>(SHOW_CONTACTS_FILTER)

    val isListFiltered = MutableLiveData<Boolean>()

    val areAllContactsDisplayed = MutableLiveData<Boolean>()

    val searchInProgress = MutableLiveData<Boolean>()

    val isDefaultAccountLinphone = MutableLiveData<Boolean>()

    val showResultsLimitReached = MutableLiveData<Boolean>()

    val disableAddContact = MutableLiveData<Boolean>()

    val vCardTerminatedEvent: MutableLiveData<Event<Pair<String, File>>> by lazy {
        MutableLiveData<Event<Pair<String, File>>>()
    }

    val cardDavSynchronizationCompletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var previousFilter = "NotSet"
    private var domainFilter = ""

    private lateinit var magicSearch: MagicSearch

    private lateinit var favouritesMagicSearch: MagicSearch

    private var firstLoad = true

    @Volatile private var cachedContacts = arrayListOf<ContactAvatarModel>()

    @Volatile private var currentNavPath = listOf<String>()

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

    private val friendListListener = object : FriendListListenerStub() {
        @WorkerThread
        override fun onSyncStatusChanged(
            friendList: FriendList,
            status: FriendList.SyncStatus?,
            message: String?
        ) {
            Log.i("$TAG Synchronization status changed to [$status] for friend list [${friendList.displayName}] with message [$message]")
            if (status == FriendList.SyncStatus.Successful || status == FriendList.SyncStatus.Failure) {
                friendList.removeListener(this)
                cardDavSynchronizationCompletedEvent.postValue(Event(true))
            }
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
                domainFilter,
                true
            )
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        fetchInProgress.value = true
        showFavourites.value = corePreferences.showFavoriteContacts
        disableAddContact.value = corePreferences.disableAddContact

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

            coreContext.postOnCoreThread {
                applyFilter(currentFilter, domainFilter, filterChanged = true)
            }
        }
    }

    @UiThread
    fun toggleFavouritesVisibility() {
        val show = showFavourites.value == false
        showFavourites.value = show

        coreContext.postOnCoreThread {
            corePreferences.showFavoriteContacts = show
        }
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

    @UiThread
    fun refreshCardDavContacts() {
        coreContext.postOnCoreThread { core ->
            for (friendList in core.friendsLists) {
                if (friendList.type == FriendList.Type.CardDAV) {
                    Log.i("$TAG Found CardDAV friend list [${friendList.displayName}], starting update")
                    friendList.addListener(friendListListener)
                    friendList.synchronizeFriendsFromServer()
                }
            }
        }
    }

    // Navigate into a category
    @UiThread
    fun navigateIntoCategory(category: OrgCategoryModel) {
        currentNavPath = category.path
        navigationPath.value = currentNavPath
        coreContext.postOnCoreThread {
            refreshDisplayAtCurrentPath()
        }
    }

    // Navigate back up one level; returns true if went up, false if already at root
    @UiThread
    fun navigateBack(): Boolean {
        val path = currentNavPath
        if (path.isEmpty()) return false
        val parentPath = path.dropLast(1)
        currentNavPath = parentPath
        navigationPath.value = parentPath
        coreContext.postOnCoreThread {
            refreshDisplayAtCurrentPath()
        }
        return true
    }

    // Navigate directly to a specific path (used by breadcrumb clicks)
    @UiThread
    fun navigateToPath(path: List<String>) {
        currentNavPath = path
        navigationPath.value = path
        coreContext.postOnCoreThread {
            refreshDisplayAtCurrentPath()
        }
    }

    @WorkerThread
    private fun refreshDisplayAtCurrentPath() {
        val path = currentNavPath

        val mixed = if (currentFilter.isNotEmpty()) {
            // When searching, show flat contact list
            ArrayList<OrgListItem>(cachedContacts.map { OrgListItem.Contact(it) })
        } else {
            buildMixedListAtPath(path)
        }

        Log.i("$TAG Showing [${mixed.size}] items at path $path")
        mixedList.postValue(mixed)
        // Also update contactsList for empty-state check
        contactsList.postValue(cachedContacts)
        fetchInProgress.postValue(false)
    }

    // Build a mixed list of categories + direct contacts at the given path level.
    // Categories come first (sorted), then direct contacts (sorted by name).
    // Organization field uses ";" as separator for hierarchy levels (vCard ORG format).
    @WorkerThread
    private fun buildMixedListAtPath(path: List<String>): ArrayList<OrgListItem> {
        val result = arrayListOf<OrgListItem>()
        val categoryMap = linkedMapOf<String, Int>()
        val directContacts = arrayListOf<ContactAvatarModel>()
        val collator = Collator.getInstance(Locale.getDefault())

        for (contact in cachedContacts) {
            val levels = getOrgLevels(contact.friend)

            if (path.isEmpty()) {
                if (levels.isEmpty()) {
                    // Root level: contacts with no organization
                    directContacts.add(contact)
                } else {
                    // Root level: group by first org level
                    val key = levels[0]
                    categoryMap[key] = (categoryMap[key] ?: 0) + 1
                }
            } else {
                // Check if this contact is under the current path
                if (levels.size < path.size) continue
                val matchesPath = path.indices.all { i -> levels[i] == path[i] }
                if (!matchesPath) continue

                if (levels.size == path.size) {
                    // Exact match: direct contact at this level
                    directContacts.add(contact)
                } else {
                    // Goes deeper: contributes to a sub-category
                    val key = levels[path.size]
                    categoryMap[key] = (categoryMap[key] ?: 0) + 1
                }
            }
        }

        // Add categories first, sorted by name
        categoryMap.entries
            .sortedWith { a, b -> collator.compare(a.key, b.key) }
            .forEach { (name, count) ->
                result.add(OrgListItem.Category(OrgCategoryModel(name, path + name, count)))
            }

        // Add direct contacts, sorted by name
        directContacts.sortWith { a, b ->
            collator.compare(a.getNameToUseForSorting(), b.getNameToUseForSorting())
        }
        directContacts.forEach { result.add(OrgListItem.Contact(it)) }

        return result
    }

    // Parse the organization field into hierarchy levels using ";" as separator
    @WorkerThread
    private fun getOrgLevels(friend: Friend): List<String> {
        val org = friend.organization?.trim() ?: return emptyList()
        if (org.isEmpty()) return emptyList()
        return org.split(";").map { it.trim() }.filter { it.isNotEmpty() }
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
                // During initial fast load, cache and display categories
                cachedContacts = list
                refreshDisplayAtCurrentPath()
            }
        }

        list.sortWith { model1, model2 ->
            collator.compare(model1.getNameToUseForSorting(), model2.getNameToUseForSorting())
        }

        searchInProgress.postValue(false)
        if (favourites) {
            favouritesList.postValue(list)
        } else {
            cachedContacts = list
            firstLoad = false
            refreshDisplayAtCurrentPath()
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

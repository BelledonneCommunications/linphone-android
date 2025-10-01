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
package org.linphone.ui.main.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.text.Collator
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.ContactsManager
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.mediastream.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.ui.main.model.SelectedAddressModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import kotlin.collections.find
import kotlin.collections.orEmpty

abstract class AddressSelectionViewModel
    @UiThread
    constructor() : DefaultAccountChangedViewModel() {
    companion object {
        private const val TAG = "[Address Selection ViewModel]"
    }

    val multipleSelectionMode = MutableLiveData<Boolean>()

    val selection = MutableLiveData<ArrayList<SelectedAddressModel>>()

    val selectionCount = MutableLiveData<String>()

    val searchFilter = MutableLiveData<String>()

    val searchInProgress = MutableLiveData<Boolean>()

    val modelsList = MutableLiveData<ArrayList<ConversationContactOrSuggestionModel>>()

    val isEmpty = MutableLiveData<Boolean>()

    val showResultsLimitReached = MutableLiveData<Boolean>()

    val showNumberOrAddressPickerDialogEvent: MutableLiveData<Event<List<ContactNumberOrAddressModel>>> by lazy {
        MutableLiveData<Event<List<ContactNumberOrAddressModel>>>()
    }

    val dismissNumberOrAddressPickerDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    protected var magicSearchSourceFlags = MagicSearch.Source.All.toInt()

    protected var skipConversation: Boolean = true

    private var currentFilter = ""
    private var previousFilter = "NotSet"

    private lateinit var magicSearch: MagicSearch

    private lateinit var favouritesMagicSearch: MagicSearch

    protected val numberOrAddressClickListener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            coreContext.postOnCoreThread {
                if (address != null) {
                    Log.i(
                        "$TAG Selected address [${model.address.asStringUriOnly()}] from friend [${model.friend.name}]"
                    )
                    onAddressSelected(model.address, model.friend)
                }
            }

            dismissNumberOrAddressPickerDialogEvent.postValue(Event(true))
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
        }
    }

    private val magicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            Log.i("$TAG Magic search contacts available")
            processMagicSearchResults(magicSearch.lastSearch)
        }

        @WorkerThread
        override fun onResultsLimitReached(magicSearch: MagicSearch, sourcesFlag: Int) {
            Log.w("$TAG Results limit reached (configured limit is [${magicSearch.searchLimit}]) for source(s) [$sourcesFlag], user should refine it's search")
            if (searchFilter.value.orEmpty().isNotEmpty()) {
                showResultsLimitReached.postValue(true)
            }
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            magicSearch.resetSearchCache()
            favouritesMagicSearch.resetSearchCache()

            applyFilter(
                currentFilter,
                magicSearchSourceFlags
            )
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    @WorkerThread
    abstract fun onSingleAddressSelected(address: Address, friend: Friend?)

    init {
        multipleSelectionMode.value = false
        isEmpty.value = true

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.addListener(contactsListener)
            magicSearch = core.createMagicSearch()
            magicSearch.limitedSearch = true
            magicSearch.searchLimit = corePreferences.magicSearchResultsLimit
            magicSearch.addListener(magicSearchListener)

            favouritesMagicSearch = core.createMagicSearch()
            favouritesMagicSearch.limitedSearch = false
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
    fun clearFilter() {
        if (searchFilter.value.orEmpty().isNotEmpty()) {
            searchFilter.value = ""
        }
    }

    @UiThread
    fun switchToMultipleSelectionMode() {
        Log.i("$TAG Multiple selection mode ON")
        multipleSelectionMode.value = true

        selectionCount.postValue(
            AppUtils.getStringWithPlural(
                R.plurals.selection_count_label,
                0,
                "0"
            )
        )
    }

    @WorkerThread
    fun updateSelectedParticipants(selectedAddresses: List<Address>) {
        for (address in selectedAddresses) {
            val found = modelsList.value.orEmpty().find {
                it.address.weakEqual(address)
            }
            found?.selected?.postValue(true)
        }
    }

    @WorkerThread
    fun addAddressModelToSelection(model: SelectedAddressModel) {
        val actual = selection.value.orEmpty()
        if (actual.find {
            it.address.weakEqual(model.address)
        } == null
        ) {
            Log.i("$TAG Adding [${model.address.asStringUriOnly()}] address to selection")

            val list = arrayListOf<SelectedAddressModel>()
            list.add(model)
            list.addAll(actual)

            val found = modelsList.value.orEmpty().find {
                it.address.weakEqual(model.address) || it.friend == model.avatarModel?.friend
            }
            found?.selected?.postValue(true)

            selectionCount.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.selection_count_label,
                    list.size,
                    list.size.toString()
                )
            )
            selection.postValue(list)
        } else {
            Log.w("$TAG Address is already in selection, doing nothing")
        }
    }

    @WorkerThread
    fun removeAddressModelFromSelection(model: SelectedAddressModel) {
        val actual = selection.value.orEmpty()
        if (actual.find {
            it.address.weakEqual(model.address)
        } != null
        ) {
            Log.i("$TAG Removing [${model.address.asStringUriOnly()}] address from selection")

            val list = arrayListOf<SelectedAddressModel>()
            list.addAll(actual)
            model.avatarModel?.destroy()
            list.remove(model)

            val found = modelsList.value.orEmpty().find {
                it.address.weakEqual(model.address) || it.friend == model.avatarModel?.friend
            }
            found?.selected?.postValue(false)

            selectionCount.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.selection_count_label,
                    list.size,
                    list.size.toString()
                )
            )
            selection.postValue(list)
        } else {
            Log.w("$TAG Address isn't in selection, doing nothing")
        }
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            applyFilter(
                filter,
                magicSearchSourceFlags
            )
        }
    }

    @WorkerThread
    private fun applyFilter(
        filter: String,
        sources: Int
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

        val domain = corePreferences.contactsFilter
        Log.i(
            "$TAG Asking Magic search for contacts matching filter [$filter], domain [$domain] and in sources [$sources]"
        )
        searchInProgress.postValue(filter.isNotEmpty())
        showResultsLimitReached.postValue(false)
        magicSearch.getContactsListAsync(
            filter,
            domain,
            sources,
            MagicSearch.Aggregation.Friend
        )
    }

    @WorkerThread
    private fun processMagicSearchResults(results: Array<SearchResult>) {
        Log.i("$TAG Processing [${results.size}] results")

        val conversationsList = if (!skipConversation) {
            getConversationsList(currentFilter)
        } else {
            arrayListOf()
        }

        val defaultAccountDomain = LinphoneUtils.getDefaultAccount()?.params?.domain
        val favoritesList = arrayListOf<ConversationContactOrSuggestionModel>()
        val domain = corePreferences.contactsFilter
        // Make a quick synchronous search for favorites (in case of total results exceed magic search limit to prevent missing ones)
        // TODO FIXME: to improve like it's done in ContactsListViewModel but will require UI changes
        val favorites = favouritesMagicSearch.getContactsList(currentFilter, domain, MagicSearch.Source.FavoriteFriends.toInt(), MagicSearch.Aggregation.Friend)
        for (result in favorites) {
            val address = result.address
            val friend = result.friend ?: continue

            val found = favoritesList.find { it.friend == friend }
            if (found != null) continue

            val mainAddress = address ?: LinphoneUtils.getFirstAvailableAddressForFriend(friend)
            if (mainAddress != null) {
                val model = ConversationContactOrSuggestionModel(mainAddress, friend = friend)
                val avatarModel = coreContext.contactsManager.getContactAvatarModelForFriend(
                    friend
                )
                model.avatarModel.postValue(avatarModel)
                favoritesList.add(model)
            } else {
                Log.w("$TAG Found favorite friend [${friend.name}] in search results but no Address could be found, skipping it")
            }
        }

        val contactsList = arrayListOf<ConversationContactOrSuggestionModel>()
        val suggestionsList = arrayListOf<ConversationContactOrSuggestionModel>()

        for (result in results) {
            val address = result.address
            val friend = result.friend
            if (friend != null) {
                // Starred friends are processed separately to prevent missing some due to magic search limit
                if (friend.starred) continue

                val found = contactsList.find { it.friend == friend }
                if (found != null) continue

                val mainAddress = address ?: LinphoneUtils.getFirstAvailableAddressForFriend(friend)
                if (mainAddress != null) {
                    val model = ConversationContactOrSuggestionModel(mainAddress, friend = friend)
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForFriend(
                        friend
                    )
                    model.avatarModel.postValue(avatarModel)
                    contactsList.add(model)
                } else {
                    Log.w("$TAG Found friend [${friend.name}] in search results but no Address could be found, skipping it")
                }
            } else if (address != null) {
                if (result.sourceFlags == MagicSearch.Source.Request.toInt()) {
                    val model = ConversationContactOrSuggestionModel(address) {
                        coreContext.startAudioCall(address)
                    }
                    val avatarModel = getContactAvatarModelForAddress(address)
                    model.avatarModel.postValue(avatarModel)
                    suggestionsList.add(model)
                    continue
                }

                val defaultAccountAddress = coreContext.core.defaultAccount?.params?.identityAddress
                if (defaultAccountAddress != null && address.weakEqual(defaultAccountAddress)) {
                    Log.i("$TAG Removing from suggestions current default account address")
                    continue
                }

                val model = ConversationContactOrSuggestionModel(address, defaultAccountDomain = defaultAccountDomain) {
                    coreContext.startAudioCall(address)
                }
                val avatarModel = getContactAvatarModelForAddress(address)
                model.avatarModel.postValue(avatarModel)
                suggestionsList.add(model)
            }
        }

        val collator = Collator.getInstance(Locale.getDefault())
        favoritesList.sortWith { model1, model2 ->
            collator.compare(model1.name, model2.name)
        }
        contactsList.sortWith { model1, model2 ->
            collator.compare(model1.name, model2.name)
        }
        suggestionsList.sortWith { model1, model2 ->
            collator.compare(model1.name, model2.name)
        }

        val list = arrayListOf<ConversationContactOrSuggestionModel>()
        list.addAll(conversationsList)
        list.addAll(favoritesList)
        list.addAll(contactsList)
        list.addAll(suggestionsList)

        searchInProgress.postValue(false)
        modelsList.postValue(list)
        isEmpty.postValue(list.isEmpty())
        Log.i(
            "$TAG Processed [${results.size}] results: [${conversationsList.size}] conversations, [${favoritesList.size}] favorites, [${contactsList.size}] contacts and [${suggestionsList.size}] suggestions"
        )
    }

    @WorkerThread
    private fun getConversationsList(filter: String): ArrayList<ConversationContactOrSuggestionModel> {
        val conversationsList = arrayListOf<ConversationContactOrSuggestionModel>()
        for (chatRoom in LinphoneUtils.getDefaultAccount()?.chatRooms.orEmpty()) {
            // Do not list conversations in which we can't send a message
            val isBasic = chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())
            if (chatRoom.isReadOnly || (!isBasic && chatRoom.participants.isEmpty())) continue

            val isOneToOne = chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())
            val conversationId = LinphoneUtils.getConversationId(chatRoom)
            val remoteAddress = chatRoom.peerAddress
            val matchesFilter: Any? = if (filter.isEmpty()) {
                null
            } else {
                if (isBasic) {
                    // Search in address but also in contact name if exists
                    val model =
                        coreContext.contactsManager.getContactAvatarModelForAddress(remoteAddress)
                    if (model.contactName?.contains(filter, ignoreCase = true) == true ||
                        remoteAddress.asStringUriOnly().contains(
                                filter,
                                ignoreCase = true
                            )
                    ) {
                        model
                    } else {
                        null
                    }
                } else {
                    if (chatRoom.subject.orEmpty().contains(filter, ignoreCase = true)) {
                        chatRoom
                    } else {
                        chatRoom.participants.find {
                            // Search in address but also in contact name if exists
                            val model =
                                coreContext.contactsManager.getContactAvatarModelForAddress(
                                    it.address
                                )
                            model.contactName?.contains(
                                filter,
                                ignoreCase = true
                            ) == true || it.address.asStringUriOnly().contains(
                                filter,
                                ignoreCase = true
                            )
                        }
                    }
                }
            }
            if (filter.isEmpty() || matchesFilter != null) {
                val friend = if (isBasic) {
                    coreContext.contactsManager.findContactByAddress(remoteAddress)
                } else {
                    val participantAddress = chatRoom.participants.firstOrNull()?.address
                    if (participantAddress != null) {
                        val friendFound = coreContext.contactsManager.findContactByAddress(
                            participantAddress
                        )
                        if (friendFound == null) {
                            val fakeFriend = coreContext.core.createFriend()
                            fakeFriend.name = LinphoneUtils.getDisplayName(participantAddress)
                            fakeFriend
                        } else {
                            friendFound
                        }
                    } else {
                        null
                    }
                }
                val subject = if (isOneToOne) {
                    friend?.name
                } else {
                    chatRoom.subject
                }
                val model = ConversationContactOrSuggestionModel(
                    remoteAddress,
                    conversationId,
                    subject,
                    friend
                )

                val avatarModel = if (!isOneToOne) {
                    val fakeFriend = coreContext.core.createFriend()
                    fakeFriend.name = chatRoom.subject
                    val avatarModel = ContactAvatarModel(fakeFriend)
                    avatarModel.defaultToConversationIcon.postValue(true)
                    avatarModel
                } else {
                    coreContext.contactsManager.getContactAvatarModelForFriend(friend)
                }
                model.avatarModel.postValue(avatarModel)
                conversationsList.add(model)
            }
        }
        return conversationsList
    }

    @UiThread
    fun handleClickOnContactModel(model: ConversationContactOrSuggestionModel) {
        if (model.selected.value == true) {
            org.linphone.core.tools.Log.i(
                "$TAG User clicked on already selected item [${model.name}], removing it from selection"
            )
            val found = selection.value.orEmpty().find {
                it.address.weakEqual(model.address) || it.avatarModel?.friend == model.friend
            }
            if (found != null) {
                coreContext.postOnCoreThread {
                    removeAddressModelFromSelection(found)
                }
                return
            } else {
                org.linphone.core.tools.Log.e("$TAG Failed to find already selected entry matching the one clicked")
            }
        }

        coreContext.postOnCoreThread { core ->
            val friend = model.friend
            if (friend == null) {
                org.linphone.core.tools.Log.i("$TAG Friend is null, using address [${model.address.asStringUriOnly()}]")
                val fakeFriend = core.createFriend()
                fakeFriend.addAddress(model.address)
                onAddressSelected(model.address, fakeFriend)
                return@postOnCoreThread
            }

            val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
            if (singleAvailableAddress != null) {
                org.linphone.core.tools.Log.i(
                    "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], using it"
                )
                onAddressSelected(singleAvailableAddress, friend)
            } else {
                val list = friend.getListOfSipAddressesAndPhoneNumbers(numberOrAddressClickListener)
                org.linphone.core.tools.Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )

                showNumberOrAddressPickerDialogEvent.postValue(Event(list))
            }
        }
    }

    @WorkerThread
    private fun onAddressSelected(address: Address, friend: Friend) {
        if (multipleSelectionMode.value == true) {
            val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val model = SelectedAddressModel(address, avatarModel) {
                removeAddressModelFromSelection(it)
            }
            addAddressModelToSelection(model)
        } else {
            onSingleAddressSelected(address, friend)
        }

        // Clear filter after it was used
        coreContext.postOnMainThread {
            clearFilter()
        }
    }

    @WorkerThread
    private fun getContactAvatarModelForAddress(address: Address): ContactAvatarModel {
        val fakeFriend = coreContext.core.createFriend()
        fakeFriend.name = LinphoneUtils.getDisplayName(address)
        fakeFriend.address = address
        return ContactAvatarModel(fakeFriend)
    }
}

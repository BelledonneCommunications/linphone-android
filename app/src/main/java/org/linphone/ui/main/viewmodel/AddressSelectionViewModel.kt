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
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.Friend
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.mediastream.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.ui.main.model.SelectedAddressModel
import org.linphone.ui.main.model.isEndToEndEncryptionMandatory
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

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

    val modelsList = MutableLiveData<ArrayList<ConversationContactOrSuggestionModel>>()

    val isEmpty = MutableLiveData<Boolean>()

    protected var magicSearchSourceFlags = MagicSearch.Source.All.toInt()

    protected var skipConversation: Boolean = true

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

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            applyFilter(
                currentFilter,
                if (limitSearchToLinphoneAccounts) corePreferences.defaultDomain else "",
                magicSearchSourceFlags
            )
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        multipleSelectionMode.value = false
        isEmpty.value = true

        coreContext.postOnCoreThread { core ->
            limitSearchToLinphoneAccounts = isEndToEndEncryptionMandatory()

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
                if (limitSearchToLinphoneAccounts) corePreferences.defaultDomain else "",
                magicSearchSourceFlags
            )
        }
    }

    @WorkerThread
    private fun applyFilter(
        filter: String,
        domain: String,
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

        Log.i(
            "$TAG Asking Magic search for contacts matching filter [$filter], domain [$domain] and in sources [$sources]"
        )
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

        val favoritesList = arrayListOf<ConversationContactOrSuggestionModel>()
        val contactsList = arrayListOf<ConversationContactOrSuggestionModel>()
        val suggestionsList = arrayListOf<ConversationContactOrSuggestionModel>()

        for (result in results) {
            val address = result.address
            if (address != null) {
                if (result.sourceFlags == MagicSearch.Source.Request.toInt()) {
                    val model = ConversationContactOrSuggestionModel(address) {
                        coreContext.startAudioCall(address)
                    }
                    suggestionsList.add(model)
                    continue
                }

                val friend = result.friend ?: coreContext.contactsManager.findContactByAddress(
                    address
                )
                if (friend != null) {
                    val found = contactsList.find { it.friend == friend }
                    if (found != null) continue

                    val model = ConversationContactOrSuggestionModel(address, friend = friend)
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForFriend(
                        friend
                    )
                    model.avatarModel.postValue(avatarModel)

                    if (friend.starred) {
                        favoritesList.add(model)
                    } else {
                        contactsList.add(model)
                    }
                } else {
                    val defaultAccountAddress = coreContext.core.defaultAccount?.params?.identityAddress
                    if (defaultAccountAddress != null && address.weakEqual(defaultAccountAddress)) {
                        Log.i("$TAG Removing from suggestions current default account address")
                        continue
                    }

                    val model = ConversationContactOrSuggestionModel(address) {
                        coreContext.startAudioCall(address)
                    }

                    suggestionsList.add(model)
                }
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
        modelsList.postValue(list)
        isEmpty.postValue(list.isEmpty)
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
                val localAddress = chatRoom.localAddress
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
                    localAddress,
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
}

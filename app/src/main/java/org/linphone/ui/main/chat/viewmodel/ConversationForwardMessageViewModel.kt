/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.ui.main.chat.viewmodel

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
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.ChatRoomParams
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.chat.model.ConversationContactOrSuggestionModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.model.isEndToEndEncryptionMandatory
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationForwardMessageViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Conversation Forward Message ViewModel]"
    }

    protected var magicSearchSourceFlags = MagicSearch.Source.All.toInt()

    private var currentFilter = ""
    private var previousFilter = "NotSet"

    val searchFilter = MutableLiveData<String>()

    val conversationsContactsAndSuggestionsList = MutableLiveData<ArrayList<ConversationContactOrSuggestionModel>>()

    private var limitSearchToLinphoneAccounts = true

    private lateinit var magicSearch: MagicSearch

    val operationInProgress = MutableLiveData<Boolean>()

    val chatRoomCreatedEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    val showNumberOrAddressPickerDialogEvent: MutableLiveData<Event<ArrayList<ContactNumberOrAddressModel>>> by lazy {
        MutableLiveData<Event<ArrayList<ContactNumberOrAddressModel>>>()
    }

    val hideNumberOrAddressPickerDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

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
    }

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            coreContext.postOnCoreThread {
                if (address != null) {
                    Log.i("$TAG Selected address is [${model.address.asStringUriOnly()}]")
                    onAddressSelected(model.address)
                }
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
        }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            val state = chatRoom.state
            val id = LinphoneUtils.getChatRoomId(chatRoom)
            Log.i("$TAG Conversation [$id] (${chatRoom.subject}) state changed: [$state]")

            if (state == ChatRoom.State.Created) {
                Log.i("$TAG Conversation [$id] successfully created")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                chatRoomCreatedEvent.postValue(
                    Event(
                        Pair(
                            chatRoom.localAddress.asStringUriOnly(),
                            chatRoom.peerAddress.asStringUriOnly()
                        )
                    )
                )
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("$TAG Conversation [$id] creation has failed!")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                showRedToastEvent.postValue(
                    Event(
                        Pair(
                            R.string.conversation_failed_to_create_toast,
                            R.drawable.warning_circle
                        )
                    )
                )
            }
        }
    }

    init {
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

        val conversationsList = arrayListOf<ConversationContactOrSuggestionModel>()
        for (chatRoom in LinphoneUtils.getDefaultAccount()?.chatRooms.orEmpty()) {
            // Only get group conversations
            if (!chatRoom.currentParams.isGroupEnabled) {
                continue
            }

            val found = if (currentFilter.isEmpty()) {
                null
            } else {
                chatRoom.participants.find {
                    // Search in address but also in contact name if exists
                    val model =
                        coreContext.contactsManager.getContactAvatarModelForAddress(it.address)
                    model.contactName?.contains(
                        currentFilter,
                        ignoreCase = true
                    ) == true || it.address.asStringUriOnly().contains(
                        currentFilter,
                        ignoreCase = true
                    )
                }
            }
            if (
                currentFilter.isEmpty() ||
                found != null ||
                chatRoom.peerAddress.asStringUriOnly().contains(currentFilter, ignoreCase = true) ||
                chatRoom.subject.orEmpty().contains(currentFilter, ignoreCase = true)
            ) {
                val localAddress = chatRoom.localAddress
                val remoteAddress = chatRoom.peerAddress
                val model = ConversationContactOrSuggestionModel(
                    remoteAddress,
                    localAddress,
                    chatRoom.subject
                )

                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.name = chatRoom.subject
                val avatarModel = ContactAvatarModel(fakeFriend)
                avatarModel.defaultToConversationIcon.postValue(true)

                model.avatarModel.postValue(avatarModel)
                conversationsList.add(model)
            }
        }

        val contactsList = arrayListOf<ConversationContactOrSuggestionModel>()
        val suggestionsList = arrayListOf<ConversationContactOrSuggestionModel>()

        for (result in results) {
            val address = result.address
            if (address != null) {
                val friend = coreContext.contactsManager.findContactByAddress(address)
                if (friend != null) {
                    val model = ConversationContactOrSuggestionModel(address, friend = friend)
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                        address
                    )
                    model.avatarModel.postValue(avatarModel)

                    contactsList.add(model)
                } else {
                    // If user-input generated result (always last) already exists, don't show it again
                    if (result.sourceFlags == MagicSearch.Source.Request.toInt()) {
                        val found = suggestionsList.find {
                            it.address.weakEqual(address)
                        }
                        if (found != null) {
                            Log.i(
                                "$TAG Result generated from user input is a duplicate of an existing solution, preventing double"
                            )
                            continue
                        }
                    }
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
        contactsList.sortWith { model1, model2 ->
            collator.compare(model1.name, model2.name)
        }
        suggestionsList.sortWith { model1, model2 ->
            collator.compare(model1.name, model2.name)
        }

        val list = arrayListOf<ConversationContactOrSuggestionModel>()
        list.addAll(conversationsList)
        list.addAll(contactsList)
        list.addAll(suggestionsList)
        conversationsContactsAndSuggestionsList.postValue(list)
        Log.i(
            "$TAG Processed [${results.size}] results, including [${conversationsList.size}] conversations, [${contactsList.size}] contacts and [${suggestionsList.size}] suggestions"
        )
    }

    @WorkerThread
    private fun onAddressSelected(address: Address) {
        hideNumberOrAddressPickerDialogEvent.postValue(Event(true))

        createOneToOneChatRoomWith(address)

        if (searchFilter.value.orEmpty().isNotEmpty()) {
            // Clear filter after it was used
            coreContext.postOnMainThread {
                clearFilter()
            }
        }
    }

    @UiThread
    fun handleClickOnModel(model: ConversationContactOrSuggestionModel) {
        coreContext.postOnCoreThread { core ->
            if (model.localAddress != null) {
                Log.i("$TAG User clicked on an existing conversation")
                chatRoomCreatedEvent.postValue(
                    Event(
                        Pair(
                            model.localAddress.asStringUriOnly(),
                            model.address.asStringUriOnly()
                        )
                    )
                )
                if (searchFilter.value.orEmpty().isNotEmpty()) {
                    // Clear filter after it was used
                    coreContext.postOnMainThread {
                        clearFilter()
                    }
                }
                return@postOnCoreThread
            }

            val friend = model.friend
            if (friend == null) {
                Log.i("$TAG Friend is null, using address [${model.address}]")
                onAddressSelected(model.address)
                return@postOnCoreThread
            }

            val addressesCount = friend.addresses.size
            val numbersCount = friend.phoneNumbers.size

            // Do not consider phone numbers if default account is in secure mode
            val enablePhoneNumbers = !isEndToEndEncryptionMandatory()

            if (addressesCount == 1 && (numbersCount == 0 || !enablePhoneNumbers)) {
                val address = friend.addresses.first()
                Log.i("$TAG Only 1 SIP address found for contact [${friend.name}], using it")
                onAddressSelected(address)
            } else if (addressesCount == 0 && numbersCount == 1 && enablePhoneNumbers) {
                val number = friend.phoneNumbers.first()
                val address = core.interpretUrl(number, LinphoneUtils.applyInternationalPrefix())
                if (address != null) {
                    Log.i("$TAG Only 1 phone number found for contact [${friend.name}], using it")
                    onAddressSelected(address)
                } else {
                    Log.e("$TAG Failed to interpret phone number [$number] as SIP address")
                }
            } else {
                val list = friend.getListOfSipAddressesAndPhoneNumbers(listener)
                Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )

                showNumberOrAddressPickerDialogEvent.postValue(Event(list))
                coreContext.postOnMainThread {
                }
            }
        }
    }

    @WorkerThread
    fun createOneToOneChatRoomWith(remote: Address) {
        val core = coreContext.core
        val account = core.defaultAccount
        if (account == null) {
            Log.e(
                "$TAG No default account found, can't create conversation with [${remote.asStringUriOnly()}]!"
            )
            return
        }

        operationInProgress.postValue(true)

        val params: ChatRoomParams = coreContext.core.createDefaultChatRoomParams()
        params.isGroupEnabled = false
        params.subject = AppUtils.getString(R.string.conversation_one_to_one_hidden_subject)
        params.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default

        val sameDomain = remote.domain == corePreferences.defaultDomain && remote.domain == account.params.domain
        if (isEndToEndEncryptionMandatory() && sameDomain) {
            Log.i("$TAG Account is in secure mode & domain matches, creating a E2E conversation")
            params.backend = ChatRoom.Backend.FlexisipChat
            params.isEncryptionEnabled = true
        } else if (!isEndToEndEncryptionMandatory()) {
            if (LinphoneUtils.isEndToEndEncryptedChatAvailable(core)) {
                Log.i(
                    "$TAG Account is in interop mode but LIME is available, creating a E2E conversation"
                )
                params.backend = ChatRoom.Backend.FlexisipChat
                params.isEncryptionEnabled = true
            } else {
                Log.i(
                    "$TAG Account is in interop mode but LIME isn't available, creating a SIP simple conversation"
                )
                params.backend = ChatRoom.Backend.Basic
                params.isEncryptionEnabled = false
            }
        } else {
            Log.e(
                "$TAG Account is in secure mode, can't chat with SIP address of different domain [${remote.asStringUriOnly()}]"
            )
            operationInProgress.postValue(false)
            showRedToastEvent.postValue(
                Event(
                    Pair(
                        R.string.conversation_invalid_participant_due_to_security_mode_toast,
                        R.drawable.warning_circle
                    )
                )
            )
            return
        }

        val participants = arrayOf(remote)
        val localAddress = account.params.identityAddress
        val existingChatRoom = core.searchChatRoom(params, localAddress, null, participants)
        if (existingChatRoom == null) {
            Log.i(
                "$TAG No existing 1-1 conversation between local account [${localAddress?.asStringUriOnly()}] and remote [${remote.asStringUriOnly()}] was found for given parameters, let's create it"
            )
            val chatRoom = core.createChatRoom(params, localAddress, participants)
            if (chatRoom != null) {
                if (params.backend == ChatRoom.Backend.FlexisipChat) {
                    if (chatRoom.state == ChatRoom.State.Created) {
                        val id = LinphoneUtils.getChatRoomId(chatRoom)
                        Log.i("$TAG 1-1 conversation [$id] has been created")
                        operationInProgress.postValue(false)
                        chatRoomCreatedEvent.postValue(
                            Event(
                                Pair(
                                    chatRoom.localAddress.asStringUriOnly(),
                                    chatRoom.peerAddress.asStringUriOnly()
                                )
                            )
                        )
                    } else {
                        Log.i("$TAG Conversation isn't in Created state yet, wait for it")
                        chatRoom.addListener(chatRoomListener)
                    }
                } else {
                    val id = LinphoneUtils.getChatRoomId(chatRoom)
                    Log.i("$TAG Conversation successfully created [$id]")
                    operationInProgress.postValue(false)
                    chatRoomCreatedEvent.postValue(
                        Event(
                            Pair(
                                chatRoom.localAddress.asStringUriOnly(),
                                chatRoom.peerAddress.asStringUriOnly()
                            )
                        )
                    )
                }
            } else {
                Log.e("$TAG Failed to create 1-1 conversation with [${remote.asStringUriOnly()}]!")
                operationInProgress.postValue(false)
                showRedToastEvent.postValue(
                    Event(
                        Pair(
                            R.string.conversation_failed_to_create_toast,
                            R.drawable.warning_circle
                        )
                    )
                )
            }
        } else {
            Log.w(
                "$TAG A 1-1 conversation between local account [${localAddress?.asStringUriOnly()}] and remote [${remote.asStringUriOnly()}] for given parameters already exists!"
            )
            operationInProgress.postValue(false)
            chatRoomCreatedEvent.postValue(
                Event(
                    Pair(
                        existingChatRoom.localAddress.asStringUriOnly(),
                        existingChatRoom.peerAddress.asStringUriOnly()
                    )
                )
            )
        }
    }
}

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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.Conference
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.ui.main.viewmodel.AddressSelectionViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationForwardMessageViewModel
    @UiThread
    constructor() : AddressSelectionViewModel() {
    companion object {
        private const val TAG = "[Conversation Forward Message ViewModel]"
    }

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
            if (state == ChatRoom.State.Instantiated) return

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
        skipConversation = false
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

            val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
            if (singleAvailableAddress != null) {
                Log.i(
                    "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], using it"
                )
                onAddressSelected(singleAvailableAddress)
            } else {
                val list = friend.getListOfSipAddressesAndPhoneNumbers(listener)
                Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )

                showNumberOrAddressPickerDialogEvent.postValue(Event(list))
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

        val params = coreContext.core.createConferenceParams(null)
        params.isChatEnabled = true
        params.isGroupEnabled = false
        params.subject = AppUtils.getString(R.string.conversation_one_to_one_hidden_subject)
        val chatParams = params.chatParams ?: return
        chatParams.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default

        val sameDomain = remote.domain == corePreferences.defaultDomain && remote.domain == account.params.domain
        if (account.params.instantMessagingEncryptionMandatory && sameDomain) {
            Log.i("$TAG Account is in secure mode & domain matches, creating a E2E conversation")
            chatParams.backend = ChatRoom.Backend.FlexisipChat
            params.securityLevel = Conference.SecurityLevel.EndToEnd
        } else if (!account.params.instantMessagingEncryptionMandatory) {
            if (LinphoneUtils.isEndToEndEncryptedChatAvailable(core)) {
                Log.i(
                    "$TAG Account is in interop mode but LIME is available, creating a E2E conversation"
                )
                chatParams.backend = ChatRoom.Backend.FlexisipChat
                params.securityLevel = Conference.SecurityLevel.EndToEnd
            } else {
                Log.i(
                    "$TAG Account is in interop mode but LIME isn't available, creating a SIP simple conversation"
                )
                chatParams.backend = ChatRoom.Backend.Basic
                params.securityLevel = Conference.SecurityLevel.None
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
                if (chatParams.backend == ChatRoom.Backend.FlexisipChat) {
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

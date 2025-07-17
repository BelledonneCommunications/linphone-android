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
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.ContactsManager
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.Conference
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.SecurityLevel
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactDeviceModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

class ContactViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Contact ViewModel]"

        private const val START_AUDIO_CALL = 0
        private const val START_VIDEO_CALL = 1
        private const val START_CONVERSATION = 2
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val sipAddressesAndPhoneNumbers = MutableLiveData<ArrayList<ContactNumberOrAddressModel>>()
    val atLeastOneSipAddressOrPhoneNumber = MediatorLiveData<Boolean>()

    val devices = MutableLiveData<ArrayList<ContactDeviceModel>>()

    val trustedDevicesPercentage = MutableLiveData<Int>()
    val trustedDevicesPercentageFloat = MutableLiveData<Float>()

    val company = MutableLiveData<String>()

    val title = MutableLiveData<String>()

    val isFavourite = MutableLiveData<Boolean>()

    val showBackButton = MutableLiveData<Boolean>()

    val expandNumbersAndAddresses = MutableLiveData<Boolean>()

    val showContactTrustAndDevices = MutableLiveData<Boolean>()

    val expandDevicesTrust = MutableLiveData<Boolean>()

    val contactFoundEvent = MutableLiveData<Event<Boolean>>()

    val isStored = MutableLiveData<Boolean>()

    val isReadOnly = MutableLiveData<Boolean>()

    val chatDisabled = MutableLiveData<Boolean>()

    val videoCallDisabled = MutableLiveData<Boolean>()

    val operationInProgress = MutableLiveData<Boolean>()

    val chatRoomCreationErrorEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    val showLongPressMenuForNumberOrAddressEvent: MutableLiveData<Event<ContactNumberOrAddressModel>> by lazy {
        MutableLiveData<Event<ContactNumberOrAddressModel>>()
    }

    val showNumberOrAddressPickerDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openNativeContactEditor: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val openLinphoneContactEditor: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val goToConversationEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val vCardTerminatedEvent: MutableLiveData<Event<Pair<String, File>>> by lazy {
        MutableLiveData<Event<Pair<String, File>>>()
    }

    val displayTrustProcessDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val startCallToDeviceToIncreaseTrustEvent: MutableLiveData<Event<Triple<String, String, String>>> by lazy {
        MutableLiveData<Event<Triple<String, String, String>>>()
    }

    val contactRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var expectedAction: Int = START_AUDIO_CALL
    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            if (model.isEnabled && address != null) {
                coreContext.postOnCoreThread { core ->
                    when (expectedAction) {
                        START_AUDIO_CALL -> {
                            Log.i("$TAG Audio calling SIP address [${address.asStringUriOnly()}]")
                            coreContext.startAudioCall(address)
                        }
                        START_VIDEO_CALL -> {
                            Log.i("$TAG Video calling SIP address [${address.asStringUriOnly()}]")
                            coreContext.startVideoCall(address)
                        }
                        START_CONVERSATION -> {
                            Log.i(
                                "$TAG Going to conversation with SIP address [${address.asStringUriOnly()}]"
                            )
                            goToConversation(address)
                        }
                    }
                }
            } else if (!model.isEnabled) {
                Log.w(
                    "$TAG Can't call SIP address [${address?.asStringUriOnly()}], it is disabled due to currently selected mode"
                )
                // TODO: Explain why user can't call that number
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
            showLongPressMenuForNumberOrAddressEvent.value = Event(model)
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            if (!::friend.isInitialized) return

            val found = coreContext.contactsManager.findContactById(refKey)
            if (found != null && found != friend) {
                Log.i(
                    "$TAG Found contact [${found.name}] matching ref key [$refKey] after contacts have been loaded/updated"
                )
                friend = found
                refreshContactInfo()
            }
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            val state = chatRoom.state
            if (state == ChatRoom.State.Instantiated) return

            val id = LinphoneUtils.getConversationId(chatRoom)
            Log.i("$TAG Conversation [$id] (${chatRoom.subject}) state changed: [$state]")

            if (state == ChatRoom.State.Created) {
                Log.i("$TAG Conversation [$id] successfully created")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("$TAG Conversation [$id] creation has failed!")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                chatRoomCreationErrorEvent.postValue(
                    Event(R.string.conversation_failed_to_create_toast)
                )
            }
        }
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (call.state == Call.State.End) {
                // Updates trust if need be
                fetchDevicesAndTrust()
            }
        }
    }

    private lateinit var friend: Friend

    private var refKey: String = ""

    init {
        isStored.value = false
        isReadOnly.value = false

        expandNumbersAndAddresses.value = true
        trustedDevicesPercentage.value = 0

        atLeastOneSipAddressOrPhoneNumber.value = false
        atLeastOneSipAddressOrPhoneNumber.addSource(sipAddressesAndPhoneNumbers) {
            atLeastOneSipAddressOrPhoneNumber.value = it.isNotEmpty()
        }

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            chatDisabled.postValue(corePreferences.disableChat)
            videoCallDisabled.postValue(!core.isVideoEnabled)

            val defaultDomain = LinphoneUtils.getDefaultAccount()?.params?.domain == corePreferences.defaultDomain
            // Only show contact's devices for Linphone accounts
            showContactTrustAndDevices.postValue(defaultDomain)

            expandDevicesTrust.postValue(defaultDomain)
            coreContext.contactsManager.addListener(contactsListener)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
            coreContext.contactsManager.removeListener(contactsListener)
            contact.value?.destroy()
        }
    }

    @UiThread
    fun findContact(displayedFriend: Friend?, refKey: String) {
        this.refKey = refKey

        coreContext.postOnCoreThread {
            if (displayedFriend != null && ::friend.isInitialized && friend == displayedFriend) {
                Log.i("$TAG Contact object already in memory, skipping")

                refreshContactInfo()
                contactFoundEvent.postValue(Event(true))
                return@postOnCoreThread
            }

            if (displayedFriend != null && (!::friend.isInitialized || friend != displayedFriend)) {
                if (displayedFriend.refKey == refKey) {
                    friend = displayedFriend
                    Log.i("$TAG Friend object available in sharedViewModel, using it")

                    refreshContactInfo()
                    contactFoundEvent.postValue(Event(true))
                    return@postOnCoreThread
                }
            }

            val found = coreContext.contactsManager.findContactById(refKey)
            if (found != null) {
                friend = found
                Log.i("$TAG Found contact [${friend.name}] matching ref key [$refKey]")

                refreshContactInfo()
                contactFoundEvent.postValue(Event(true))
            }
        }
    }

    @WorkerThread
    fun refreshContactInfo() {
        isFavourite.postValue(friend.starred)
        // Do not show edit contact button for contacts not stored in a FriendList or
        // if they are in a temporary one (for example if they are from a remote directory such as LDAP or CardDAV)
        isStored.postValue(!coreContext.contactsManager.isContactTemporary(friend))
        isReadOnly.postValue(friend.isReadOnly)

        contact.value?.destroy()
        contact.postValue(ContactAvatarModel(friend))

        val organization = friend.organization
        if (!organization.isNullOrEmpty()) {
            company.postValue(organization!!)
        }
        val jobTitle = friend.jobTitle
        if (!jobTitle.isNullOrEmpty()) {
            title.postValue(jobTitle!!)
        }

        val addressesAndNumbers = friend.getListOfSipAddressesAndPhoneNumbers(listener)
        sipAddressesAndPhoneNumbers.postValue(addressesAndNumbers)

        fetchDevicesAndTrust()
    }

    @UiThread
    fun toggleNumbersAndAddressesExpand() {
        expandNumbersAndAddresses.value = expandNumbersAndAddresses.value == false
    }

    @UiThread
    fun toggleDevicesTrustExpand() {
        expandDevicesTrust.value = expandDevicesTrust.value == false
    }

    @UiThread
    fun displayTrustDialog() {
        displayTrustProcessDialogEvent.value = Event(true)
    }

    @UiThread
    fun editContact() {
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
                val uri = friend.nativeUri
                if (uri != null) {
                    Log.i(
                        "$TAG Contact [${friend.name}] is a native contact, opening native contact editor using URI [$uri]"
                    )
                    openNativeContactEditor.postValue(Event(uri))
                } else {
                    val id = contact.value?.id.orEmpty()
                    Log.i(
                        "$TAG Contact [${friend.name}] is a Linphone contact, opening in-app contact editor using ID [$id]"
                    )
                    openLinphoneContactEditor.postValue(Event(id))
                }
            }
        }
    }

    @UiThread
    fun exportContactAsVCard() {
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
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
    }

    @UiThread
    fun deleteContact() {
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
                Log.w("$TAG Deleting friend [${friend.name}]")
                coreContext.contactsManager.contactRemoved(friend)
                friend.remove()
                coreContext.contactsManager.notifyContactsListChanged()
                contactRemovedEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun toggleFavourite() {
        coreContext.postOnCoreThread {
            val favourite = friend.starred
            Log.i(
                "$TAG Flagging contact [${friend.name}] as ${if (favourite) "no longer favourite" else "favourite"}"
            )

            friend.edit()
            friend.starred = !favourite
            friend.done()

            isFavourite.postValue(friend.starred)
            coreContext.contactsManager.notifyContactsListChanged()
        }
    }

    @UiThread
    fun startAudioCall() {
        coreContext.postOnCoreThread {
            val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
            if (singleAvailableAddress != null) {
                Log.i(
                    "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], starting audio call directly"
                )
                coreContext.startAudioCall(singleAvailableAddress)
            } else {
                expectedAction = START_AUDIO_CALL
                val list = sipAddressesAndPhoneNumbers.value.orEmpty()
                Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )
                showNumberOrAddressPickerDialogEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun startVideoCall() {
        coreContext.postOnCoreThread {
            val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
            if (singleAvailableAddress != null) {
                Log.i(
                    "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], starting video call directly"
                )
                coreContext.startVideoCall(singleAvailableAddress)
            } else {
                expectedAction = START_VIDEO_CALL
                val list = sipAddressesAndPhoneNumbers.value.orEmpty()
                Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )
                showNumberOrAddressPickerDialogEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun goToConversation() {
        coreContext.postOnCoreThread {
            val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
            if (singleAvailableAddress != null) {
                Log.i(
                    "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], sending message directly"
                )
                goToConversation(singleAvailableAddress)
            } else {
                expectedAction = START_CONVERSATION
                val list = sipAddressesAndPhoneNumbers.value.orEmpty()
                Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )
                showNumberOrAddressPickerDialogEvent.postValue(Event(true))
            }
        }
    }

    @WorkerThread
    private fun goToConversation(remote: Address) {
        val core = coreContext.core
        val account = core.defaultAccount
        val localSipUri = account?.params?.identityAddress?.asStringUriOnly()
        if (!localSipUri.isNullOrEmpty()) {
            val remoteSipUri = remote.asStringUriOnly()
            Log.i(
                "$TAG Looking for existing conversation between [$localSipUri] and [$remoteSipUri]"
            )

            val params = coreContext.core.createConferenceParams(null)
            params.isChatEnabled = true
            params.isGroupEnabled = false
            params.subject = AppUtils.getString(R.string.conversation_one_to_one_hidden_subject)
            params.account = account

            val chatParams = params.chatParams ?: return
            chatParams.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default

            val sameDomain = remote.domain == corePreferences.defaultDomain && remote.domain == account.params.domain
            if (account.params.instantMessagingEncryptionMandatory && sameDomain) {
                Log.i(
                    "$TAG Account is in secure mode & domain matches, creating an E2E encrypted conversation"
                )
                chatParams.backend = ChatRoom.Backend.FlexisipChat
                params.securityLevel = Conference.SecurityLevel.EndToEnd
            } else if (!account.params.instantMessagingEncryptionMandatory) {
                if (LinphoneUtils.isEndToEndEncryptedChatAvailable(core)) {
                    Log.i(
                        "$TAG Account is in interop mode but LIME is available, creating an E2E encrypted conversation"
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
                // TODO: show error
                return
            }

            val participants = arrayOf(remote)
            val localAddress = account.params.identityAddress
            val existingChatRoom = core.searchChatRoom(params, localAddress, null, participants)
            if (existingChatRoom != null) {
                Log.i(
                    "$TAG Found existing conversation [${LinphoneUtils.getConversationId(
                        existingChatRoom
                    )}], going to it"
                )
                goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(existingChatRoom)))
            } else {
                Log.i(
                    "$TAG No existing conversation between [$localSipUri] and [$remoteSipUri] was found, let's create it"
                )
                operationInProgress.postValue(true)
                val chatRoom = core.createChatRoom(params, participants)
                if (chatRoom != null) {
                    if (chatParams.backend == ChatRoom.Backend.FlexisipChat) {
                        if (chatRoom.state == ChatRoom.State.Created) {
                            val id = LinphoneUtils.getConversationId(chatRoom)
                            Log.i("$TAG 1-1 conversation [$id] has been created")
                            operationInProgress.postValue(false)
                            goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
                        } else {
                            Log.i("$TAG Conversation isn't in Created state yet, wait for it")
                            chatRoom.addListener(chatRoomListener)
                        }
                    } else {
                        val id = LinphoneUtils.getConversationId(chatRoom)
                        Log.i("$TAG Conversation successfully created [$id]")
                        operationInProgress.postValue(false)
                        goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
                    }
                } else {
                    Log.e(
                        "$TAG Failed to create 1-1 conversation with [${remote.asStringUriOnly()}]!"
                    )
                    operationInProgress.postValue(false)
                    chatRoomCreationErrorEvent.postValue(
                        Event(R.string.conversation_failed_to_create_toast)
                    )
                }
            }
        }
    }

    @WorkerThread
    private fun fetchDevicesAndTrust() {
        val devicesList = arrayListOf<ContactDeviceModel>()

        val friendDevices = friend.devices
        if (friendDevices.isEmpty()) {
            Log.w("$TAG No device found for friend [${friend.name}]")
        } else {
            val devicesCount = friendDevices.size
            var trustedDevicesCount = 0
            for (device in friendDevices) {
                val trusted = device.securityLevel == SecurityLevel.EndToEndEncryptedAndVerified
                devicesList.add(
                    ContactDeviceModel(
                        device.displayName ?: AppUtils.getString(
                            R.string.contact_device_without_name
                        ),
                        device.address,
                        trusted
                    ) {
                        if (::friend.isInitialized) {
                            startCallToDeviceToIncreaseTrustEvent.value =
                                Event(Triple(friend.name.orEmpty(), it.name, it.address.asStringUriOnly()))
                        }
                    }
                )
                if (trusted) {
                    trustedDevicesCount += 1
                }
            }

            if (devicesList.isNotEmpty()) {
                val percentage = trustedDevicesCount * 100 / devicesCount
                trustedDevicesPercentage.postValue(percentage)
                if (percentage == 0) {
                    trustedDevicesPercentageFloat.postValue(0.5f)
                } else {
                    trustedDevicesPercentageFloat.postValue(percentage / 100f / 2)
                }
                if (percentage == 100) {
                    expandDevicesTrust.postValue(false)
                }
            }
        }

        devices.postValue(devicesList)
    }
}

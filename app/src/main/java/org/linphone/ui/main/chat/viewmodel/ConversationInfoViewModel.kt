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
package org.linphone.ui.main.chat.viewmodel

import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.ContactsManager
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationInfoViewModel
    @UiThread
    constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation Info ViewModel]"
    }

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val participantsLabel = MutableLiveData<String>()

    val isGroup = MutableLiveData<Boolean>()

    val hideSipAddresses = MutableLiveData<Boolean>()

    val isEndToEndEncrypted = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val sipUri = MutableLiveData<String>()

    val peerSipUri = MutableLiveData<String>()

    val showPeerSipUri = MutableLiveData<Boolean>()

    val isReadOnly = MutableLiveData<Boolean>()

    val isMyselfAdmin = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val ephemeralLifetime = MutableLiveData<Long>()

    val expandParticipants = MutableLiveData<Boolean>()

    val oneToOneParticipantRefKey = MutableLiveData<String>()

    val friendAvailable = MutableLiveData<Boolean>()

    val disableAddContact = MutableLiveData<Boolean>()

    val groupLeftEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val infoChangedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showParticipantAdminPopupMenuEvent: MutableLiveData<Event<Pair<View, ParticipantModel>>> by lazy {
        MutableLiveData<Event<Pair<View, ParticipantModel>>>()
    }

    val goToScheduleMeetingEvent: MutableLiveData<Event<Pair<String, ArrayList<String>>>> by lazy {
        MutableLiveData<Event<Pair<String, ArrayList<String>>>>()
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been added to the group [${chatRoom.subject}]")
            val message = AppUtils.getFormattedString(
                R.string.conversation_info_participant_added_to_conversation_toast,
                getParticipant(eventLog)
            )
            showFormattedGreenToast(message, R.drawable.user_circle_plus)

            computeParticipantsList()
            infoChangedEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been removed from the group [${chatRoom.subject}]")
            val message = AppUtils.getFormattedString(
                R.string.conversation_info_participant_removed_from_conversation_toast,
                getParticipant(eventLog)
            )
            showFormattedGreenToast(message, R.drawable.user_circle_minus)

            computeParticipantsList()
            infoChangedEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i(
                "$TAG A participant has been given/removed administration rights for group [${chatRoom.subject}]"
            )
            if (eventLog.type == EventLog.Type.ConferenceParticipantSetAdmin) {
                val message = AppUtils.getFormattedString(
                    R.string.conversation_info_participant_has_been_granted_admin_rights_toast,
                    getParticipant(eventLog)
                )
                showFormattedGreenToast(message, R.drawable.user_circle_check)
            } else {
                val message = AppUtils.getFormattedString(
                    R.string.conversation_info_participant_no_longer_has_admin_rights_toast,
                    getParticipant(eventLog)
                )
                showFormattedGreenToast(message, R.drawable.user_circle_dashed)
            }

            computeParticipantsList()
        }

        @WorkerThread
        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i(
                "$TAG Conversation [${LinphoneUtils.getConversationId(chatRoom)}] has a new subject [${chatRoom.subject}]"
            )
            showGreenToast(R.string.conversation_subject_changed_toast, R.drawable.check)

            subject.postValue(chatRoom.subject)
            computeParticipantsList()
            infoChangedEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Ephemeral event [${eventLog.type}]")
            when (eventLog.type) {
                EventLog.Type.ConferenceEphemeralMessageEnabled -> {
                    showGreenToast(R.string.conversation_ephemeral_messages_enabled_toast, R.drawable.clock_countdown)
                }
                EventLog.Type.ConferenceEphemeralMessageDisabled -> {
                    showGreenToast(R.string.conversation_ephemeral_messages_disabled_toast, R.drawable.clock_countdown)
                }
                else -> {
                    showGreenToast(R.string.conversation_ephemeral_messages_lifetime_changed_toast, R.drawable.clock_countdown)
                }
            }
        }
    }

    private val contactsListener = object : ContactsManager.ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
            Log.i("$TAG Contacts have been (re)loaded, updating list")
            computeParticipantsList()
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) { }
    }

    init {
        expandParticipants.value = true
        showPeerSipUri.value = false
        disableAddContact.value = corePreferences.disableAddContact

        coreContext.postOnCoreThread {
            hideSipAddresses.postValue(corePreferences.hideSipAddresses)
            coreContext.contactsManager.addListener(contactsListener)
        }
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            coreContext.contactsManager.removeListener(contactsListener)
            if (isChatRoomInitialized()) {
                chatRoom.removeListener(chatRoomListener)
            }
        }
    }

    override fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
        if (!sameOne) {
            chatRoom.addListener(chatRoomListener)
            configureChatRoom()
        }
    }

    @UiThread
    fun leaveGroup() {
        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                Log.i("$TAG Leaving conversation [${LinphoneUtils.getConversationId(chatRoom)}]")
                chatRoom.leave()
            }
            groupLeftEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun deleteHistory() {
        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                Log.i(
                    "$TAG Cleaning conversation [${LinphoneUtils.getConversationId(chatRoom)}] history"
                )
                chatRoom.deleteHistory()
            }
            historyDeletedEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun toggleMute() {
        coreContext.postOnCoreThread {
            chatRoom.muted = !chatRoom.muted
            isMuted.postValue(chatRoom.muted)
        }
    }

    @UiThread
    fun scheduleMeeting() {
        coreContext.postOnCoreThread {
            if (LinphoneUtils.isChatRoomAGroup(chatRoom)) {
                val participantsList = arrayListOf<String>()
                for (participant in chatRoom.participants) {
                    participantsList.add(participant.address.asStringUriOnly())
                }
                goToScheduleMeetingEvent.postValue(
                    Event(Pair(chatRoom.subject.orEmpty(), participantsList))
                )
            } else {
                val firstParticipant = chatRoom.participants.firstOrNull()
                val address = firstParticipant?.address
                if (address != null) {
                    val participantsList = arrayListOf(address.asStringUriOnly())
                    goToScheduleMeetingEvent.postValue(Event(Pair("", participantsList)))
                } else {
                    Log.e("$TAG Failed to find participant to call!")
                }
            }
        }
    }

    @UiThread
    fun toggleParticipantsExpand() {
        expandParticipants.value = expandParticipants.value == false
    }

    @UiThread
    fun removeParticipant(participantModel: ParticipantModel) {
        coreContext.postOnCoreThread {
            val address = participantModel.address
            Log.i(
                "$TAG Removing participant [$address] from the conversation [${LinphoneUtils.getConversationId(
                    chatRoom
                )}]"
            )
            val participant = chatRoom.participants.find {
                it.address.weakEqual(address)
            }
            if (participant != null) {
                chatRoom.removeParticipant(participant)
                Log.i("$TAG Participant removed")
            } else {
                Log.e("$TAG Couldn't find participant matching address [$address]!")
            }
        }
    }

    @UiThread
    fun giveAdminRightsTo(participantModel: ParticipantModel) {
        coreContext.postOnCoreThread {
            val address = participantModel.address
            Log.i(
                "$TAG Granting admin rights to participant [$address] from the conversation [${LinphoneUtils.getConversationId(
                    chatRoom
                )}]"
            )
            val participant = chatRoom.participants.find {
                it.address.weakEqual(address)
            }
            if (participant != null) {
                chatRoom.setParticipantAdminStatus(participant, true)
                Log.i("$TAG Participant will become admin soon")
            } else {
                Log.e("$TAG Couldn't find participant matching address [$address]!")
            }
        }
    }

    @UiThread
    fun removeAdminRightsFrom(participantModel: ParticipantModel) {
        coreContext.postOnCoreThread {
            val address = participantModel.address
            Log.i(
                "$TAG Removing admin rights from participant [$address] from the conversation [${LinphoneUtils.getConversationId(
                    chatRoom
                )}]"
            )
            val participant = chatRoom.participants.find {
                it.address.weakEqual(address)
            }
            if (participant != null) {
                chatRoom.setParticipantAdminStatus(participant, false)
                Log.i("$TAG Participant will be removed as admin soon")
            } else {
                Log.e("$TAG Couldn't find participant matching address [$address]!")
            }
        }
    }

    @UiThread
    fun setParticipants(newList: ArrayList<String>) {
        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                if (!LinphoneUtils.isChatRoomAGroup(chatRoom)) {
                    Log.e("$TAG Can't add participants to a conversation that's not a group!")
                    return@postOnCoreThread
                }

                val toRemoveList = arrayListOf<Participant>()
                for (participant in chatRoom.participants) {
                    val address = participant.address
                    // Do not remove ourselves if not in participants list anymore
                    if (LinphoneUtils.getDefaultAccount()?.params?.identityAddress?.weakEqual(
                            address
                        ) == true
                    ) {
                        continue
                    }

                    val uri = address.asStringUriOnly()
                    val found = newList.find {
                        it == uri
                    }
                    if (found != null) {
                        Log.i(
                            "$TAG Participant [$uri] is still in new participants list, do nothing"
                        )
                    } else {
                        Log.i("$TAG Participant [$uri] will be removed from this conversation")
                        toRemoveList.add(participant)
                    }
                }

                if (toRemoveList.isNotEmpty()) {
                    Log.i(
                        "$TAG Removing [${toRemoveList.size}] participants from conversation"
                    )
                    chatRoom.removeParticipants(toRemoveList.toTypedArray())
                }

                val toAddList = arrayListOf<Address>()
                for (participant in newList) {
                    val address = Factory.instance().createAddress(participant)
                    if (address == null) {
                        Log.e("$TAG Failed to parse [$participant] as address!")
                    } else {
                        val found = participants.value.orEmpty().find {
                            it.address.weakEqual(address)
                        }
                        if (found != null) {
                            Log.i(
                                "$TAG Participant [${address.asStringUriOnly()}] is already in group, do nothing"
                            )
                        } else {
                            Log.i(
                                "$TAG Participant [${address.asStringUriOnly()}] will be added to this conversation"
                            )
                            toAddList.add(address)
                        }
                    }
                }

                if (toAddList.isNotEmpty()) {
                    Log.i(
                        "$TAG Adding [${toAddList.size}] new participants to conversation"
                    )
                    val ok = chatRoom.addParticipants(toAddList.toTypedArray())
                    if (!ok) {
                        Log.w("$TAG Failed to add some/all participants to the group!")
                        showRedToast(R.string.conversation_failed_to_add_participant_to_group_conversation_toast, R.drawable.warning_circle)
                    }
                }
            }
        }
    }

    @UiThread
    fun showDebugInfo(): Boolean {
        showPeerSipUri.value = true
        return true
    }

    @UiThread
    fun updateSubject(newSubject: String) {
        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                Log.i("$TAG Updating conversation subject to [$newSubject]")
                chatRoom.subject = newSubject
            }
        }
    }

    @UiThread
    fun updateEphemeralLifetime(lifetime: Long) {
        coreContext.postOnCoreThread {
            LinphoneUtils.chatRoomConfigureEphemeralMessagesLifetime(chatRoom, lifetime)
            ephemeralLifetime.postValue(
                if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
            )
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        isMuted.postValue(chatRoom.muted)

        isMyselfAdmin.postValue(chatRoom.me?.isAdmin)

        val isGroupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        isGroup.postValue(isGroupChatRoom)
        isEndToEndEncrypted.postValue(
            chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt())
        )

        val readOnly = chatRoom.isReadOnly
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Conversation with subject [${chatRoom.subject}] is read only!")
        }

        subject.postValue(chatRoom.subject)
        peerSipUri.postValue(chatRoom.peerAddress.asStringUriOnly())

        val firstParticipant = chatRoom.participants.firstOrNull()
        if (firstParticipant != null) {
            val address = firstParticipant.address
            val uri = if (corePreferences.onlyDisplaySipUriUsername) {
                address.username ?: ""
            } else {
                LinphoneUtils.getAddressAsCleanStringUriOnly(address)
            }
            sipUri.postValue(uri)

            val friend = coreContext.contactsManager.findContactByAddress(address)
            if (friend == null) {
                oneToOneParticipantRefKey.postValue("")
                friendAvailable.postValue(false)
            } else {
                oneToOneParticipantRefKey.postValue(friend.refKey)
                friendAvailable.postValue(coreContext.contactsManager.isContactAvailable(friend))
            }
        }

        ephemeralLifetime.postValue(
            if (!chatRoom.isEphemeralEnabled) 0L else chatRoom.ephemeralLifetime
        )

        computeParticipantsList()
    }

    @WorkerThread
    private fun computeParticipantsList() {
        val groupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        val selfAdmin = if (groupChatRoom) chatRoom.me?.isAdmin == true else false

        val friends = arrayListOf<Friend>()
        val participantsList = arrayListOf<ParticipantModel>()
        if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            val model = ParticipantModel(
                chatRoom.peerAddress,
                selfAdmin,
                isParticipantAdmin = false,
                showMenu = true,
                isParticipantMyself = false,
                onMenuClicked = { view, model ->
                    // openMenu
                    showParticipantAdminPopupMenuEvent.postValue(Event(Pair(view, model)))
                }
            )
            friends.add(model.avatarModel.friend)
            participantsList.add(model)
        } else {
            for (participant in chatRoom.participants) {
                val isParticipantAdmin = if (groupChatRoom) participant.isAdmin else false
                val model = ParticipantModel(
                    participant.address,
                    selfAdmin,
                    isParticipantAdmin = isParticipantAdmin,
                    showMenu = true,
                    isParticipantMyself = false,
                    onMenuClicked = { view, model ->
                        // openMenu
                        showParticipantAdminPopupMenuEvent.postValue(Event(Pair(view, model)))
                    }
                )
                friends.add(model.avatarModel.friend)
                participantsList.add(model)
            }

            if (!chatRoom.isReadOnly) {
                // Add ourselves at the end of the list
                val meParticipant = chatRoom.me
                if (meParticipant != null) {
                    val model = ParticipantModel(
                        meParticipant.address,
                        selfAdmin,
                        isParticipantAdmin = selfAdmin,
                        showMenu = false,
                        isParticipantMyself = true,
                        onMenuClicked = { view, model ->
                            // openMenu
                            showParticipantAdminPopupMenuEvent.postValue(Event(Pair(view, model)))
                        }
                    )
                    participantsList.add(model)
                }
            }
        }

        val avatar = if (groupChatRoom) {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = chatRoom.subject
            val model = ContactAvatarModel(fakeFriend)
            model.defaultToConversationIcon.postValue(true)
            model.updateSecurityLevelUsingConversation(chatRoom)
            model
        } else {
            participantsList.first().avatarModel
        }
        if (!avatar.compare(avatarModel.value)) {
            avatarModel.postValue(avatar)
        }

        participants.postValue(participantsList)
        participantsLabel.postValue(
            AppUtils.getFormattedString(
                R.string.conversation_info_participants_list_title,
                participantsList.size.toString()
            )
        )
    }

    @WorkerThread
    private fun getParticipant(eventLog: EventLog): String {
        val participantAddress = eventLog.participantAddress
        return if (participantAddress != null) {
            val model = participants.value.orEmpty().find {
                it.address.weakEqual(participantAddress)
            }
            model?.avatarModel?.contactName ?: LinphoneUtils.getDisplayName(participantAddress)
        } else {
            ""
        }
    }
}

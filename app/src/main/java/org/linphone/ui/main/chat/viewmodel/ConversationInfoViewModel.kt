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
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.ConferenceScheduler
import org.linphone.core.ConferenceSchedulerListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.Participant
import org.linphone.core.ParticipantInfo
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationInfoViewModel @UiThread constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation Info ViewModel]"
    }

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val isGroup = MutableLiveData<Boolean>()

    val isEndToEndEncrypted = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val sipUri = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val isMyselfAdmin = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val ephemeralLifetime = MutableLiveData<Long>()

    val expandParticipants = MutableLiveData<Boolean>()

    val oneToOneParticipantRefKey = MutableLiveData<String>()

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

    val goToScheduleMeetingEvent: MutableLiveData<Event<ArrayList<String>>> by lazy {
        MutableLiveData<Event<ArrayList<String>>>()
    }

    val showGreenToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    val showRedToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been added to the group [${chatRoom.subject}]")
            val message = AppUtils.getFormattedString(
                R.string.toast_participant_added_to_conversation,
                getParticipant(eventLog)
            )
            showGreenToastEvent.postValue(Event(Pair(message, R.drawable.user_circle)))

            computeParticipantsList()
            infoChangedEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been removed from the group [${chatRoom.subject}]")
            val message = AppUtils.getFormattedString(
                R.string.toast_participant_removed_from_conversation,
                getParticipant(eventLog)
            )
            showGreenToastEvent.postValue(Event(Pair(message, R.drawable.user_circle)))

            computeParticipantsList()
            infoChangedEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i(
                "$TAG A participant has been given/removed administration rights for group [${chatRoom.subject}]"
            )
            val message = if (eventLog.type == EventLog.Type.ConferenceParticipantSetAdmin) {
                AppUtils.getFormattedString(
                    R.string.toast_participant_has_been_granted_admin_rights,
                    getParticipant(eventLog)
                )
            } else {
                AppUtils.getFormattedString(
                    R.string.toast_participant_no_longer_has_admin_rights,
                    getParticipant(eventLog)
                )
            }
            showGreenToastEvent.postValue(Event(Pair(message, R.drawable.user_circle)))

            computeParticipantsList()
        }

        @WorkerThread
        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i(
                "$TAG Conversation [${LinphoneUtils.getChatRoomId(chatRoom)}] has a new subject [${chatRoom.subject}]"
            )
            val message = AppUtils.getString(
                R.string.toast_conversation_subject_changed
            )
            showGreenToastEvent.postValue(Event(Pair(message, R.drawable.check)))

            subject.postValue(chatRoom.subject)
            infoChangedEvent.postValue(Event(true))
        }

        @WorkerThread
        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Ephemeral event [${eventLog.type}]")
            val message = when (eventLog.type) {
                EventLog.Type.ConferenceEphemeralMessageEnabled -> {
                    AppUtils.getString(
                        R.string.toast_conversation_ephemeral_messages_enabled
                    )
                }
                EventLog.Type.ConferenceEphemeralMessageDisabled -> {
                    AppUtils.getString(
                        R.string.toast_conversation_ephemeral_messages_disabled
                    )
                }
                else -> {
                    AppUtils.getString(
                        R.string.toast_conversation_ephemeral_messages_lifetime_changed
                    )
                }
            }
            showGreenToastEvent.postValue(Event(Pair(message, R.drawable.clock_countdown)))
        }
    }

    private val conferenceSchedulerListener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State
        ) {
            Log.i("$TAG Conference scheduler state is $state")
            if (state == ConferenceScheduler.State.Ready) {
                conferenceScheduler.removeListener(this)

                val conferenceAddress = conferenceScheduler.info?.uri
                if (conferenceAddress != null) {
                    Log.i(
                        "$TAG Conference info created, address is ${conferenceAddress.asStringUriOnly()}"
                    )
                    coreContext.startVideoCall(conferenceAddress)
                } else {
                    Log.e("$TAG Conference info URI is null!")
                    // TODO: notify error to user
                }
            } else if (state == ConferenceScheduler.State.Error) {
                conferenceScheduler.removeListener(this)
                Log.e("$TAG Failed to create group call!")
                // TODO: notify error to user
            }
        }
    }

    init {
        expandParticipants.value = true
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
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
                Log.i("$TAG Leaving conversation [${LinphoneUtils.getChatRoomId(chatRoom)}]")
                chatRoom.leave()
            }
            groupLeftEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun deleteHistory() {
        coreContext.postOnCoreThread {
            // TODO: confirmation dialog ?
            if (isChatRoomInitialized()) {
                Log.i(
                    "$TAG Cleaning conversation [${LinphoneUtils.getChatRoomId(chatRoom)}] history"
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
    fun call() {
        coreContext.postOnCoreThread { core ->
            if (LinphoneUtils.isChatRoomAGroup(chatRoom) && chatRoom.participants.size >= 2) {
                createGroupCall()
            } else {
                val firstParticipant = chatRoom.participants.firstOrNull()
                val address = firstParticipant?.address
                if (address != null) {
                    Log.i("$TAG Audio calling SIP address [${address.asStringUriOnly()}]")
                    coreContext.startAudioCall(address)
                } else {
                    Log.e("$TAG Failed to find participant to call!")
                }
            }
        }
    }

    @UiThread
    fun scheduleMeeting() {
        coreContext.postOnCoreThread {
            if (LinphoneUtils.isChatRoomAGroup(chatRoom)) {
                val participantsList = arrayListOf<String>()
                for (participant in chatRoom.participants) {
                    participantsList.add(participant.address.asStringUriOnly())
                    goToScheduleMeetingEvent.postValue(Event(participantsList))
                }
            } else {
                val firstParticipant = chatRoom.participants.firstOrNull()
                val address = firstParticipant?.address
                if (address != null) {
                    val participantsList = arrayListOf(address.asStringUriOnly())
                    goToScheduleMeetingEvent.postValue(Event(participantsList))
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
                "$TAG Removing participant [$address] from the conversation [${LinphoneUtils.getChatRoomId(
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
                "$TAG Granting admin rights to participant [$address] from the conversation [${LinphoneUtils.getChatRoomId(
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
                "$TAG Removing admin rights from participant [$address] from the conversation [${LinphoneUtils.getChatRoomId(
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
                        Log.i("$TAG Removing participant [$uri] from this conversation")
                        chatRoom.removeParticipant(participant)
                    }
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
                            toAddList.add(address)
                        }
                    }
                }

                if (toAddList.isNotEmpty()) {
                    val participantsToAdd = arrayOfNulls<Address>(toAddList.size)
                    toAddList.toArray(participantsToAdd)
                    Log.i(
                        "$TAG Adding [${participantsToAdd.size}] new participants to conversation"
                    )
                    val ok = chatRoom.addParticipants(participantsToAdd)
                    if (!ok) {
                        Log.w("$TAG Failed to add some/all participants to the group!")
                        val message = AppUtils.getString(
                            R.string.toast_failed_to_add_participant_to_group_conversation
                        )
                        showRedToastEvent.postValue(Event(Pair(message, R.drawable.warning_circle)))
                    }
                }
            }
        }
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

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Conversation with subject [${chatRoom.subject}] is read only!")
        }

        subject.postValue(chatRoom.subject)

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
            oneToOneParticipantRefKey.postValue(friend?.refKey ?: "")
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

        val avatar = if (groupChatRoom) {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = chatRoom.subject
            val model = ContactAvatarModel(fakeFriend)
            model.defaultToConversationIcon.postValue(true)
            model
        } else {
            participantsList.first().avatarModel
        }
        avatarModel.postValue(avatar)

        participants.postValue(participantsList)
    }

    @WorkerThread
    private fun createGroupCall() {
        val core = coreContext.core
        val account = core.defaultAccount
        if (account == null) {
            Log.e(
                "$TAG No default account found, can't create group call!"
            )
            return
        }

        val conferenceInfo = Factory.instance().createConferenceInfo()
        conferenceInfo.organizer = account.params.identityAddress
        conferenceInfo.subject = subject.value

        val participants = arrayOfNulls<ParticipantInfo>(chatRoom.participants.size)
        var index = 0
        for (participant in chatRoom.participants) {
            val info = Factory.instance().createParticipantInfo(participant.address)
            // For meetings, all participants must have Speaker role
            info?.role = Participant.Role.Speaker
            participants[index] = info
            index += 1
        }
        conferenceInfo.setParticipantInfos(participants)

        Log.i(
            "$TAG Creating group call with subject ${subject.value} and ${participants.size} participant(s)"
        )
        val conferenceScheduler = core.createConferenceScheduler()
        conferenceScheduler.addListener(conferenceSchedulerListener)
        conferenceScheduler.account = account
        // Will trigger the conference creation/update automatically
        conferenceScheduler.info = conferenceInfo
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

/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.chat.GroupChatRoomMember
import org.linphone.activities.main.chat.data.GroupInfoParticipantData
import org.linphone.activities.main.viewmodels.MessageNotifierViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class GroupInfoViewModelFactory(private val chatRoom: ChatRoom?) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GroupInfoViewModel(chatRoom) as T
    }
}

class GroupInfoViewModel(val chatRoom: ChatRoom?) : MessageNotifierViewModel() {
    val createdChatRoomEvent = MutableLiveData<Event<ChatRoom>>()
    val updatedChatRoomEvent = MutableLiveData<Event<ChatRoom>>()

    val subject = MutableLiveData<String>()

    val participants = MutableLiveData<ArrayList<GroupInfoParticipantData>>()

    val isEncrypted = MutableLiveData<Boolean>()

    val isMeAdmin = MutableLiveData<Boolean>()

    val canLeaveGroup = MutableLiveData<Boolean>()

    val waitForChatRoomCreation = MutableLiveData<Boolean>()

    val meAdminChangedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                waitForChatRoomCreation.value = false
                createdChatRoomEvent.value = Event(chatRoom) // To trigger going to the chat room
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[Chat Room Group Info] Group chat room creation has failed !")
                waitForChatRoomCreation.value = false
                onMessageToNotifyEvent.value = Event(R.string.chat_room_creation_failed_snack)
            }
        }

        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            subject.value = chatRoom.subject
        }

        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            updateParticipants()
        }

        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            updateParticipants()
        }

        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            val admin = chatRoom.me?.isAdmin ?: false
            if (admin != isMeAdmin.value) {
                isMeAdmin.value = admin
                meAdminChangedEvent.value = Event(admin)
            }
            updateParticipants()
        }
    }

    init {
        subject.value = chatRoom?.subject
        isMeAdmin.value = chatRoom == null || (chatRoom.me?.isAdmin == true && !chatRoom.isReadOnly)
        canLeaveGroup.value = chatRoom != null && !chatRoom.isReadOnly
        isEncrypted.value = corePreferences.forceEndToEndEncryptedChat || chatRoom?.hasCapability(ChatRoomCapabilities.Encrypted.toInt()) == true

        if (chatRoom != null) updateParticipants()

        chatRoom?.addListener(listener)
        waitForChatRoomCreation.value = false
    }

    override fun onCleared() {
        participants.value.orEmpty().forEach(GroupInfoParticipantData::destroy)
        chatRoom?.removeListener(listener)

        super.onCleared()
    }

    fun createChatRoom() {
        waitForChatRoomCreation.value = true
        val params: ChatRoomParams = coreContext.core.createDefaultChatRoomParams()
        params.isEncryptionEnabled = corePreferences.forceEndToEndEncryptedChat || isEncrypted.value == true
        params.isGroupEnabled = true
        if (params.isEncryptionEnabled) {
            params.ephemeralMode = if (corePreferences.useEphemeralPerDeviceMode)
                ChatRoomEphemeralMode.DeviceManaged
            else
                ChatRoomEphemeralMode.AdminManaged
        }
        params.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default
        Log.i("[Chat Room Group Info] Ephemeral mode is ${params.ephemeralMode}, lifetime is ${params.ephemeralLifetime}")
        params.subject = subject.value

        val addresses = arrayOfNulls<Address>(participants.value.orEmpty().size)
        var index = 0
        for (participant in participants.value.orEmpty()) {
            addresses[index] = participant.participant.address
            Log.i("[Chat Room Group Info] Participant ${participant.sipUri} will be added to group")
            index += 1
        }

        val chatRoom: ChatRoom? = coreContext.core.createChatRoom(params, coreContext.core.defaultAccount?.params?.identityAddress, addresses)
        chatRoom?.addListener(listener)
        if (chatRoom == null) {
            Log.e("[Chat Room Group Info] Couldn't create chat room!")
            waitForChatRoomCreation.value = false
            onMessageToNotifyEvent.value = Event(R.string.chat_room_creation_failed_snack)
        }
    }

    fun updateRoom() {
        if (chatRoom != null) {
            // Subject
            val newSubject = subject.value.orEmpty()
            if (newSubject.isNotEmpty() && newSubject != chatRoom.subject) {
                Log.i("[Chat Room Group Info] Subject changed to $newSubject")
                chatRoom.subject = newSubject
            }

            // Removed participants
            val participantsToRemove = arrayListOf<Participant>()
            for (participant in chatRoom.participants) {
                val member = participants.value.orEmpty().find { member ->
                    participant.address.weakEqual(member.participant.address)
                }
                if (member == null) {
                    Log.w("[Chat Room Group Info] Participant ${participant.address.asStringUriOnly()} will be removed from group")
                    participantsToRemove.add(participant)
                }
            }
            val toRemove = arrayOfNulls<Participant>(participantsToRemove.size)
            participantsToRemove.toArray(toRemove)
            chatRoom.removeParticipants(toRemove)

            // Added participants & new admins
            val participantsToAdd = arrayListOf<Address>()
            for (member in participants.value.orEmpty()) {
                val participant = chatRoom.participants.find { participant ->
                    participant.address.weakEqual(member.participant.address)
                }
                if (participant != null) {
                    // Participant found, check if admin status needs to be updated
                    if (member.participant.isAdmin != participant.isAdmin) {
                        if (chatRoom.me?.isAdmin == true) {
                            Log.i("[Chat Room Group Info] Participant ${member.sipUri} will be admin? ${member.isAdmin}")
                            chatRoom.setParticipantAdminStatus(participant, member.participant.isAdmin)
                        }
                    }
                } else {
                    Log.i("[Chat Room Group Info] Participant ${member.sipUri} will be added to group")
                    participantsToAdd.add(member.participant.address)
                }
            }
            val toAdd = arrayOfNulls<Address>(participantsToAdd.size)
            participantsToAdd.toArray(toAdd)
            chatRoom.addParticipants(toAdd)

            // Go back to chat room
            updatedChatRoomEvent.value = Event(chatRoom)
        }
    }

    fun leaveGroup() {
        if (chatRoom != null) {
            Log.w("[Chat Room Group Info] Leaving group")
            chatRoom.leave()
            updatedChatRoomEvent.value = Event(chatRoom)
        }
    }

    fun removeParticipant(participant: GroupChatRoomMember) {
        val list = arrayListOf<GroupInfoParticipantData>()
        for (data in participants.value.orEmpty()) {
            if (!data.participant.address.weakEqual(participant.address)) {
                list.add(data)
            }
        }
        participants.value = list
    }

    private fun updateParticipants() {
        val list = arrayListOf<GroupInfoParticipantData>()

        if (chatRoom != null) {
            for (participant in chatRoom.participants) {
                list.add(
                    GroupInfoParticipantData(
                        GroupChatRoomMember(participant.address, participant.isAdmin, participant.securityLevel, canBeSetAdmin = true)
                    )
                )
            }
        }

        participants.value = list
    }
}

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

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationInfoViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Conversation Info ViewModel]"
    }

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val isMyselfAdmin = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val expandParticipants = MutableLiveData<Boolean>()

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    val groupLeftEvent = MutableLiveData<Event<Boolean>>()

    val historyDeletedEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var chatRoom: ChatRoom

    private val avatarsMap = hashMapOf<String, ParticipantModel>()

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }

        @WorkerThread
        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }

        @WorkerThread
        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            subject.postValue(chatRoom.subject)
        }
    }

    init {
        expandParticipants.value = true
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                chatRoom.removeListener(chatRoomListener)
            }

            avatarModel.value?.destroy()
            avatarsMap.values.forEach(ParticipantModel::destroy)
        }
    }

    @UiThread
    fun findChatRoom(localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for chat room with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)
            if (localAddress != null && remoteAddress != null) {
                val found = core.searchChatRoom(
                    null,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls(
                        0
                    )
                )
                if (found != null) {
                    chatRoom = found
                    chatRoom.addListener(chatRoomListener)

                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                } else {
                    Log.e("$TAG Failed to find chat room given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun leaveGroup() {
        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                Log.i("$TAG Leaving chat room [${LinphoneUtils.getChatRoomId(chatRoom)}]")
                chatRoom.leave()
            }
            groupLeftEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun deleteHistory() {
        coreContext.postOnCoreThread {
            // TODO: confirmation dialog ?
            if (::chatRoom.isInitialized) {
                Log.i("$TAG Cleaning chat room [${LinphoneUtils.getChatRoomId(chatRoom)}] history")
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
    fun toggleParticipantsExpand() {
        expandParticipants.value = expandParticipants.value == false
    }

    @WorkerThread
    private fun configureChatRoom() {
        isMuted.postValue(chatRoom.muted)

        isMyselfAdmin.postValue(chatRoom.me?.isAdmin)

        val isGroupChatRoom = isChatRoomAGroup()
        isGroup.postValue(isGroupChatRoom)

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Chat room with subject [${chatRoom.subject}] is read only!")
        }

        subject.postValue(chatRoom.subject)

        computeParticipantsList()
    }

    @WorkerThread
    private fun computeParticipantsList() {
        avatarModel.value?.destroy()
        avatarsMap.values.forEach(ParticipantModel::destroy)

        val groupChatRoom = isChatRoomAGroup()

        val friends = arrayListOf<Friend>()
        val participantsList = arrayListOf<ParticipantModel>()
        if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            val model = getParticipantModelForAddress(chatRoom.peerAddress, false)
            friends.add(model.friend)
            participantsList.add(model)
        } else {
            for (participant in chatRoom.participants) {
                val model = getParticipantModelForAddress(
                    participant.address,
                    if (groupChatRoom) participant.isAdmin else false
                )
                friends.add(model.friend)
                participantsList.add(model)
            }
        }

        val avatar = if (groupChatRoom) {
            val fakeFriend = coreContext.core.createFriend()
            ContactAvatarModel(fakeFriend)
        } else {
            participantsList.first()
        }
        avatar.setPicturesFromFriends(friends)
        avatarModel.postValue(avatar)

        participants.postValue(participantsList)
    }

    @WorkerThread
    private fun getParticipantModelForAddress(address: Address?, isAdmin: Boolean): ParticipantModel {
        Log.i("$TAG Looking for participant model with address [${address?.asStringUriOnly()}]")
        if (address == null) {
            val fakeFriend = coreContext.core.createFriend()
            return ParticipantModel(fakeFriend, isMyselfAdmin.value == true, false)
        }

        val clone = address.clone()
        clone.clean()
        val key = clone.asStringUriOnly()

        val foundInMap = if (avatarsMap.keys.contains(key)) avatarsMap[key] else null
        if (foundInMap != null) return foundInMap

        val friend = coreContext.contactsManager.findContactByAddress(clone)
        val avatar = if (friend != null) {
            ParticipantModel(friend, isMyselfAdmin.value == true, isAdmin)
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = clone
            ParticipantModel(fakeFriend, isMyselfAdmin.value == true, isAdmin)
        }

        avatarsMap[key] = avatar
        return avatar
    }

    @WorkerThread
    private fun isChatRoomAGroup(): Boolean {
        return if (::chatRoom.isInitialized) {
            LinphoneUtils.isChatRoomAGroup(chatRoom)
        } else {
            false
        }
    }
}

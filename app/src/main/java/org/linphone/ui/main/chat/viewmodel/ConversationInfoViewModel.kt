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

    val sipUri = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val isMyselfAdmin = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val ephemeralLifetime = MutableLiveData<Int>()

    val expandParticipants = MutableLiveData<Boolean>()

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    val groupLeftEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showParticipantAdminPopupMenuEvent: MutableLiveData<Event<Pair<View, ParticipantModel>>> by lazy {
        MutableLiveData<Event<Pair<View, ParticipantModel>>>()
    }

    private lateinit var chatRoom: ChatRoom

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been added to the group [${chatRoom.subject}]")
            // TODO: show toast
            computeParticipantsList()
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG A participant has been removed from the group [${chatRoom.subject}]")
            // TODO: show toast
            computeParticipantsList()
        }

        @WorkerThread
        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i(
                "$TAG A participant has been given/removed administration rights for group [${chatRoom.subject}]"
            )
            // TODO: show toast
            // TODO FIXME: list doesn't have the changes...
            computeParticipantsList()
        }

        @WorkerThread
        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i(
                "$TAG Chat room [${LinphoneUtils.getChatRoomId(chatRoom)}] has a new subject [${chatRoom.subject}]"
            )
            // TODO: show toast
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
        }
    }

    @UiThread
    fun findChatRoom(room: ChatRoom?, localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for chat room with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )
            if (room != null && ::chatRoom.isInitialized && chatRoom == room) {
                Log.i("$TAG Chat room object already in memory, skipping")
                chatRoomFoundEvent.postValue(Event(true))
                return@postOnCoreThread
            }

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)

            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (localAddress?.weakEqual(room.localAddress) == true && remoteAddress?.weakEqual(
                        room.peerAddress
                    ) == true
                ) {
                    Log.i("$TAG Chat room object available in sharedViewModel, using it")
                    chatRoom = room
                    chatRoom.addListener(chatRoomListener)
                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                    return@postOnCoreThread
                }
            }

            if (localAddress != null && remoteAddress != null) {
                Log.i("$TAG Searching for chat room in Core using local & peer SIP addresses")
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
    fun call() {
        // TODO
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
    fun addParticipants(toAdd: ArrayList<String>) {
        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                if (!LinphoneUtils.isChatRoomAGroup(chatRoom)) {
                    Log.e("$TAG Can't add participants to a chat room that's not a group!")
                    return@postOnCoreThread
                }

                val list = arrayListOf<Address>()
                for (participant in toAdd) {
                    val address = Factory.instance().createAddress(participant)
                    if (address == null) {
                        Log.e("$TAG Failed to parse [$participant] as address!")
                    } else {
                        list.add(address)
                    }
                }

                val participantsToAdd = arrayOfNulls<Address>(list.size)
                list.toArray(participantsToAdd)
                Log.i("$TAG Adding [${participantsToAdd.size}] new participants to chat room")
                val ok = chatRoom.addParticipants(participantsToAdd)
                if (!ok) {
                    Log.w("$TAG Failed to add some/all participants to the group!")
                    // TODO: show toast
                }
            }
        }
    }

    @UiThread
    fun updateSubject(newSubject: String) {
        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                Log.i("$TAG Updating chat room subject to [$newSubject]")
                chatRoom.subject = newSubject
            }
        }
    }

    @UiThread
    fun updateEphemeralLifetime(lifetime: Int) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Updating chat messages ephemeral lifetime to [$lifetime]")
            chatRoom.ephemeralLifetime = lifetime.toLong()
            chatRoom.isEphemeralEnabled = lifetime != 0
            ephemeralLifetime.postValue(chatRoom.ephemeralLifetime.toInt())
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        isMuted.postValue(chatRoom.muted)

        isMyselfAdmin.postValue(chatRoom.me?.isAdmin)

        val isGroupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        isGroup.postValue(isGroupChatRoom)

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Chat room with subject [${chatRoom.subject}] is read only!")
        }

        subject.postValue(chatRoom.subject)
        sipUri.postValue(chatRoom.participants.firstOrNull()?.address?.asStringUriOnly())

        ephemeralLifetime.postValue(chatRoom.ephemeralLifetime.toInt())

        computeParticipantsList()
    }

    @WorkerThread
    private fun computeParticipantsList() {
        val groupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        val selfAdmin = if (groupChatRoom) chatRoom.me?.isAdmin == true else false

        val friends = arrayListOf<Friend>()
        val participantsList = arrayListOf<ParticipantModel>()
        if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            val model = ParticipantModel(chatRoom.peerAddress, selfAdmin, false) { view, model ->
                // openMenu
                showParticipantAdminPopupMenuEvent.postValue(Event(Pair(view, model)))
            }
            friends.add(model.avatarModel.friend)
            participantsList.add(model)
        } else {
            for (participant in chatRoom.participants) {
                val isParticipantAdmin = if (groupChatRoom) participant.isAdmin else false
                val model = ParticipantModel(participant.address, selfAdmin, isParticipantAdmin, onMenuClicked = { view, model ->
                    // openMenu
                    showParticipantAdminPopupMenuEvent.postValue(Event(Pair(view, model)))
                })
                friends.add(model.avatarModel.friend)
                participantsList.add(model)
            }
        }

        val avatar = if (groupChatRoom) {
            val fakeFriend = coreContext.core.createFriend()
            val model = ContactAvatarModel(fakeFriend)
            model.defaultToConferenceIcon.postValue(true)
            model.setPicturesFromFriends(friends)
            model
        } else {
            participantsList.first().avatarModel
        }
        avatarModel.postValue(avatar)

        participants.postValue(participantsList)
    }
}

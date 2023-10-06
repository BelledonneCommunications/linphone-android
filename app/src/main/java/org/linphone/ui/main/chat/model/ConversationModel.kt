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
package org.linphone.ui.main.chat.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoom.Capabilities
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ConversationModel @WorkerThread constructor(private val chatRoom: ChatRoom) {
    companion object {
        private const val TAG = "[Conversation Model]"
    }

    val id = LinphoneUtils.getChatRoomId(chatRoom)

    val localSipUri = chatRoom.localAddress.asStringUriOnly()

    val remoteSipUri = chatRoom.peerAddress.asStringUriOnly()

    val isGroup = !chatRoom.hasCapability(Capabilities.OneToOne.toInt())

    val lastUpdateTime = MutableLiveData<Long>()

    val isComposing = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val isEphemeral = MutableLiveData<Boolean>()

    val composingLabel = MutableLiveData<Boolean>()

    val lastMessage = MutableLiveData<String>()

    val lastMessageIcon = MutableLiveData<Int>()

    val isLastMessageOutgoing = MutableLiveData<Boolean>()

    val dateTime = MutableLiveData<String>()

    val unreadMessageCount = MutableLiveData<Int>()

    val avatarModel: ContactAvatarModel

    init {
        lastUpdateTime.postValue(chatRoom.lastUpdateTime)

        val address = if (chatRoom.hasCapability(Capabilities.Basic.toInt())) {
            Log.i("$TAG Chat room [$id] is 'Basic'")
            chatRoom.peerAddress
        } else {
            val firstParticipant = chatRoom.participants.firstOrNull()
            if (isGroup) {
                Log.i("$TAG Group chat room [$id] has [${chatRoom.nbParticipants}] participant(s)")
            } else {
                Log.i(
                    "$TAG Chat room [$id] is with participant [${firstParticipant?.address?.asStringUriOnly()}]"
                )
            }
            firstParticipant?.address ?: chatRoom.peerAddress
        }

        val friend = coreContext.contactsManager.findContactByAddress(address)
        if (friend != null) {
            avatarModel = ContactAvatarModel(friend)
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = address
            avatarModel = ContactAvatarModel(fakeFriend)
        }

        isMuted.postValue(chatRoom.muted)
        isEphemeral.postValue(chatRoom.isEphemeralEnabled)

        updateLastMessage()

        updateLastUpdatedTime()

        unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
    }

    @UiThread
    fun markAsRead() {
        coreContext.postOnCoreThread {
            chatRoom.markAsRead()
            unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
            Log.i("$TAG Conversation [$id] has been marked as read")
        }
    }

    @UiThread
    fun toggleMute() {
        coreContext.postOnCoreThread {
            chatRoom.muted = !chatRoom.muted
            val muted = chatRoom.muted
            if (muted) {
                Log.i("$TAG Conversation [$id] is now muted")
            } else {
                Log.i("$TAG Conversation [$id] is no longer muted")
            }
            isMuted.postValue(muted)
        }
    }

    @UiThread
    fun call() {
        coreContext.postOnCoreThread {
            val address = chatRoom.participants.firstOrNull()?.address ?: chatRoom.peerAddress
            Log.i("$TAG Calling [${address.asStringUriOnly()}]")
            coreContext.startCall(address)
        }
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            core.deleteChatRoom(chatRoom)
            Log.i("$TAG Conversation [$id] has been deleted")
        }
    }

    @UiThread
    fun leaveGroup() {
        coreContext.postOnCoreThread {
            chatRoom.leave()
            Log.i("$TAG Group conversation [$id] has been leaved")
        }
    }

    @WorkerThread
    private fun updateLastMessage() {
        val message = chatRoom.lastMessageInHistory
        if (message != null) {
            val text = LinphoneUtils.getTextDescribingMessage(message)
            lastMessage.postValue(text)

            val isOutgoing = message.isOutgoing
            isLastMessageOutgoing.postValue(isOutgoing)
            if (isOutgoing) {
                val icon = when (message.state) {
                    ChatMessage.State.Displayed -> {
                        R.drawable.checks
                    }
                    ChatMessage.State.DeliveredToUser -> {
                        R.drawable.check
                    }
                    ChatMessage.State.Delivered -> {
                        R.drawable.sent
                    }
                    ChatMessage.State.InProgress, ChatMessage.State.FileTransferInProgress -> {
                        R.drawable.in_progress
                    }
                    ChatMessage.State.NotDelivered, ChatMessage.State.FileTransferError -> {
                        R.drawable.warning_circle
                    }
                    else -> {
                        R.drawable.info
                    }
                }
                lastMessageIcon.postValue(icon)
            }
        } else {
            Log.w("$TAG No last message to display for chat room [$id]")
        }
    }

    @WorkerThread
    private fun updateLastUpdatedTime() {
        val timestamp = chatRoom.lastUpdateTime
        val humanReadableTimestamp = when {
            TimestampUtils.isToday(timestamp) -> {
                TimestampUtils.timeToString(chatRoom.lastUpdateTime)
            }
            TimestampUtils.isYesterday(timestamp) -> {
                val time = TimestampUtils.timeToString(chatRoom.lastUpdateTime)
                AppUtils.getFormattedString(R.string.conversation_yesterday_timestamp, time)
            }
            else -> {
                TimestampUtils.toString(chatRoom.lastUpdateTime, onlyDate = true)
            }
        }
        dateTime.postValue(humanReadableTimestamp)
    }
}

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
package org.linphone.ui.conversations

import android.text.SpannableStringBuilder
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ChatRoomData(val chatRoom: ChatRoom) {
    val id = LinphoneUtils.getChatRoomId(chatRoom)

    val contactName = MutableLiveData<String>()

    val subject = MutableLiveData<String>()

    val lastMessage = MutableLiveData<SpannableStringBuilder>()

    val unreadChatCount = MutableLiveData<Int>()

    val isComposing = MutableLiveData<Boolean>()

    val isSecure = MutableLiveData<Boolean>()

    val isSecureVerified = MutableLiveData<Boolean>()

    val isEphemeral = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val lastUpdate = MutableLiveData<String>()

    val showLastMessageImdnIcon = MutableLiveData<Boolean>()

    val lastMessageImdnIcon = MutableLiveData<Int>()

    var chatRoomDataListener: ChatRoomDataListener? = null

    val isOneToOne: Boolean by lazy {
        chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            composing: Boolean
        ) {
            isComposing.postValue(composing)
        }

        override fun onMessagesReceived(chatRoom: ChatRoom, chatMessages: Array<out ChatMessage>) {
            unreadChatCount.postValue(chatRoom.unreadMessagesCount)
            computeLastMessage()
        }

        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            computeLastMessage()
        }

        override fun onEphemeralMessageDeleted(chatRoom: ChatRoom, eventLog: EventLog) {
            computeLastMessage()
        }

        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            subject.postValue(
                chatRoom.subject ?: LinphoneUtils.getDisplayName(chatRoom.peerAddress)
            )
        }
    }

    init {
        if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            val remoteAddress = chatRoom.peerAddress
            val friend = chatRoom.core.findFriend(remoteAddress)
            contactName.postValue(friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress))
        } else {
            if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
                val first = chatRoom.participants.firstOrNull()
                if (first != null) {
                    val remoteAddress = first.address
                    val friend = chatRoom.core.findFriend(remoteAddress)
                    contactName.postValue(
                        friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress)
                    )
                } else {
                    Log.e("[Chat Room Data] No participant in the chat room!")
                }
            }
        }
        subject.postValue(chatRoom.subject ?: LinphoneUtils.getDisplayName(chatRoom.peerAddress))

        lastMessageImdnIcon.postValue(R.drawable.imdn_sent)
        showLastMessageImdnIcon.postValue(false)
        computeLastMessage()

        unreadChatCount.postValue(chatRoom.unreadMessagesCount)
        isComposing.postValue(chatRoom.isRemoteComposing)
        isSecure.postValue(chatRoom.securityLevel == ChatRoom.SecurityLevel.Encrypted)
        isSecureVerified.postValue(chatRoom.securityLevel == ChatRoom.SecurityLevel.Safe)
        isEphemeral.postValue(chatRoom.isEphemeralEnabled)
        isMuted.postValue(areNotificationsMuted())

        coreContext.postOnCoreThread { core ->
            chatRoom.addListener(chatRoomListener)
        }
    }

    fun onCleared() {
        coreContext.postOnCoreThread { core ->
            chatRoom.removeListener(chatRoomListener)
        }
    }

    fun onClicked() {
        chatRoomDataListener?.onClicked()
    }

    private fun computeLastMessageImdnIcon(message: ChatMessage) {
        val state = message.state
        showLastMessageImdnIcon.postValue(
            if (message.isOutgoing) {
                when (state) {
                    ChatMessage.State.DeliveredToUser, ChatMessage.State.Displayed,
                    ChatMessage.State.NotDelivered, ChatMessage.State.FileTransferError -> true
                    else -> false
                }
            } else {
                false
            }
        )

        lastMessageImdnIcon.postValue(
            when (state) {
                ChatMessage.State.DeliveredToUser -> R.drawable.imdn_delivered
                ChatMessage.State.Displayed -> R.drawable.imdn_read
                ChatMessage.State.InProgress -> R.drawable.imdn_sent
                // TODO FIXME
                else -> R.drawable.imdn_sent
            }
        )
    }

    private fun computeLastMessage() {
        val lastUpdateTime = chatRoom.lastUpdateTime
        lastUpdate.postValue(TimestampUtils.toString(lastUpdateTime, true))

        val builder = SpannableStringBuilder()

        val message = chatRoom.lastMessageInHistory
        if (message != null) {
            val senderAddress = message.fromAddress.clone()
            senderAddress.clean()

            if (message.isOutgoing && message.state != ChatMessage.State.Displayed) {
                message.addListener(object : ChatMessageListenerStub() {
                    override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
                        computeLastMessageImdnIcon(message)
                    }
                })
            }
            computeLastMessageImdnIcon(message)

            if (!isOneToOne) {
                val sender = chatRoom.core.findFriend(senderAddress)
                builder.append(sender?.name ?: LinphoneUtils.getDisplayName(senderAddress))
                builder.append(": ")
            }

            for (content in message.contents) {
                if (content.isFile || content.isFileTransfer) {
                    builder.append(content.name + " ")
                } else if (content.isText) {
                    builder.append(content.utf8Text + " ")
                }
            }
            builder.trim()
        }

        lastMessage.postValue(builder)
    }

    private fun areNotificationsMuted(): Boolean {
        return corePreferences.chatRoomMuted(id)
    }
}

abstract class ChatRoomDataListener {
    abstract fun onClicked()
}

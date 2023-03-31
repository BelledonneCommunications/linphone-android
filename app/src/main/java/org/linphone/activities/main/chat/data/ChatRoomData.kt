/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.data

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.ContactDataInterface
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ChatRoomData(val chatRoom: ChatRoom) : ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val showGroupChatAvatar: Boolean
        get() = conferenceChatRoom && !oneToOneChatRoom
    override val presenceStatus: MutableLiveData<ConsolidatedPresence> = MutableLiveData<ConsolidatedPresence>()
    override val coroutineScope: CoroutineScope = coreContext.coroutineScope

    val id = LinphoneUtils.getChatRoomId(chatRoom)

    val unreadMessagesCount = MutableLiveData<Int>()

    val subject = MutableLiveData<String>()

    val securityLevelIcon = MutableLiveData<Int>()

    val securityLevelContentDescription = MutableLiveData<Int>()

    val ephemeralEnabled = MutableLiveData<Boolean>()

    val lastUpdate = MutableLiveData<String>()

    val lastMessageText = MutableLiveData<SpannableStringBuilder>()

    val showLastMessageImdnIcon = MutableLiveData<Boolean>()

    val lastMessageImdnIcon = MutableLiveData<Int>()

    val notificationsMuted = MutableLiveData<Boolean>()

    private val basicChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())
    }

    val oneToOneChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
    }

    private val conferenceChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt())
    }

    val encryptedChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())
    }

    private val contactsListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            if (oneToOneChatRoom && contact.value == null) {
                searchMatchingContact()
                if (contact.value != null) {
                    formatLastMessage(chatRoom.lastMessageInHistory)
                }
            }
        }
    }

    init {
        coreContext.contactsManager.addListener(contactsListener)
    }

    fun destroy() {
        coreContext.contactsManager.removeListener(contactsListener)
    }

    fun update() {
        unreadMessagesCount.value = chatRoom.unreadMessagesCount
        presenceStatus.value = ConsolidatedPresence.Offline

        subject.value = chatRoom.subject
        updateSecurityIcon()
        ephemeralEnabled.value = chatRoom.isEphemeralEnabled

        contactLookup()
        formatLastMessage(chatRoom.lastMessageInHistory)

        notificationsMuted.value = areNotificationsMuted()
    }

    fun markAsRead() {
        chatRoom.markAsRead()
        unreadMessagesCount.value = 0
    }

    private fun updateSecurityIcon() {
        val level = chatRoom.securityLevel
        securityLevel.value = level

        securityLevelIcon.value = when (level) {
            ChatRoomSecurityLevel.Safe -> R.drawable.security_2_indicator
            ChatRoomSecurityLevel.Encrypted -> R.drawable.security_1_indicator
            else -> R.drawable.security_alert_indicator
        }
        securityLevelContentDescription.value = when (level) {
            ChatRoomSecurityLevel.Safe -> R.string.content_description_security_level_safe
            ChatRoomSecurityLevel.Encrypted -> R.string.content_description_security_level_encrypted
            else -> R.string.content_description_security_level_unsafe
        }
    }

    private fun contactLookup() {
        displayName.value = when {
            basicChatRoom -> LinphoneUtils.getDisplayName(
                chatRoom.peerAddress
            )
            oneToOneChatRoom -> LinphoneUtils.getDisplayName(
                chatRoom.participants.firstOrNull()?.address ?: chatRoom.peerAddress
            )
            conferenceChatRoom -> chatRoom.subject.orEmpty()
            else -> chatRoom.peerAddress.asStringUriOnly()
        }

        if (oneToOneChatRoom) {
            searchMatchingContact()
        }
    }

    private fun searchMatchingContact() {
        val remoteAddress = if (basicChatRoom) {
            chatRoom.peerAddress
        } else {
            val participants = chatRoom.participants
            if (participants.isNotEmpty()) {
                participants.first().address
            } else {
                Log.e("[Chat Room] ${chatRoom.peerAddress} doesn't have any participant (state ${chatRoom.state})!")
                null
            }
        }
        if (remoteAddress != null) {
            val friend = coreContext.contactsManager.findContactByAddress(remoteAddress)
            if (friend != null) {
                contact.value = friend!!
                presenceStatus.value = friend.consolidatedPresence
                friend.addListener {
                    presenceStatus.value = it.consolidatedPresence
                }
            }
        }
    }

    private fun formatLastMessage(msg: ChatMessage?) {
        val lastUpdateTime = chatRoom.lastUpdateTime
        lastUpdate.value = "00:00"
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                lastUpdate.postValue(TimestampUtils.toString(lastUpdateTime, true))
            }
        }

        val builder = SpannableStringBuilder()
        if (msg == null) {
            lastMessageText.value = builder
            showLastMessageImdnIcon.value = false
            return
        }

        if (msg.isOutgoing && msg.state != ChatMessage.State.Displayed) {
            msg.addListener(object : ChatMessageListenerStub() {
                override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
                    computeLastMessageImdnIcon(message)
                }
            })
        }
        computeLastMessageImdnIcon(msg)

        if (!chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            val sender: String =
                coreContext.contactsManager.findContactByAddress(msg.fromAddress)?.name
                    ?: LinphoneUtils.getDisplayName(msg.fromAddress)
            builder.append(coreContext.context.getString(R.string.chat_room_last_message_sender_format, sender))
            builder.append(" ")
        }

        for (content in msg.contents) {
            if (content.isIcalendar) {
                val body = AppUtils.getString(R.string.conference_invitation)
                builder.append(body)
                builder.setSpan(StyleSpan(Typeface.ITALIC), builder.length - body.length, builder.length, 0)
            } else if (content.isVoiceRecording) {
                val body = AppUtils.getString(R.string.chat_message_voice_recording)
                builder.append(body)
                builder.setSpan(StyleSpan(Typeface.ITALIC), builder.length - body.length, builder.length, 0)
            } else if (content.isFile || content.isFileTransfer) {
                builder.append(content.name + " ")
            } else if (content.isText) {
                builder.append(content.utf8Text + " ")
            }
        }

        builder.trim()
        lastMessageText.value = builder
    }

    private fun computeLastMessageImdnIcon(msg: ChatMessage) {
        val state = msg.state
        showLastMessageImdnIcon.value = if (msg.isOutgoing) {
            when (state) {
                ChatMessage.State.DeliveredToUser, ChatMessage.State.Displayed,
                ChatMessage.State.NotDelivered, ChatMessage.State.FileTransferError -> true
                else -> false
            }
        } else {
            false
        }
        lastMessageImdnIcon.value = when (state) {
            ChatMessage.State.DeliveredToUser -> R.drawable.chat_delivered
            ChatMessage.State.Displayed -> R.drawable.chat_read
            ChatMessage.State.FileTransferError, ChatMessage.State.NotDelivered -> R.drawable.chat_error
            else -> R.drawable.chat_error
        }
    }

    private fun areNotificationsMuted(): Boolean {
        val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
        return corePreferences.chatRoomMuted(id)
    }
}

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
import org.linphone.R
import org.linphone.contact.ContactDataInterface
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ChatRoomData(val chatRoom: ChatRoom) : ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoom.SecurityLevel> = MutableLiveData<ChatRoom.SecurityLevel>()
    override val showGroupChatAvatar: Boolean
        get() = !oneToOneChatRoom
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
        chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())
    }

    val oneToOneChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())
    }

    val encryptedChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt())
    }

    val contactNewlyFoundEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val contactsListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            if (contact.value == null && oneToOneChatRoom) {
                searchMatchingContact()
            }
            if (!oneToOneChatRoom) {
                formatLastMessage(chatRoom.lastMessageInHistory)
            }
        }
    }

    init {
        coreContext.contactsManager.addListener(contactsListener)

        lastUpdate.value = "00:00"
        presenceStatus.value = ConsolidatedPresence.Offline
    }

    fun destroy() {
        coreContext.contactsManager.removeListener(contactsListener)
    }

    fun update() {
        unreadMessagesCount.value = chatRoom.unreadMessagesCount

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
            ChatRoom.SecurityLevel.Safe -> R.drawable.security_2_indicator
            ChatRoom.SecurityLevel.Encrypted -> R.drawable.security_1_indicator
            else -> R.drawable.security_alert_indicator
        }
        securityLevelContentDescription.value = when (level) {
            ChatRoom.SecurityLevel.Safe -> R.string.content_description_security_level_safe
            ChatRoom.SecurityLevel.Encrypted -> R.string.content_description_security_level_encrypted
            else -> R.string.content_description_security_level_unsafe
        }
    }

    private fun contactLookup() {
        if (oneToOneChatRoom) {
            searchMatchingContact()
        } else {
            displayName.value = chatRoom.subject ?: chatRoom.peerAddress.asStringUriOnly()
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
                Log.e(
                    "[Chat Room] ${chatRoom.peerAddress} doesn't have any participant (state ${chatRoom.state})!"
                )
                null
            }
        }

        if (remoteAddress != null) {
            val friend = coreContext.contactsManager.findContactByAddress(remoteAddress)
            if (friend != null) {
                val newlyFound = contact.value == null

                contact.value = friend!!
                presenceStatus.value = friend.consolidatedPresence
                friend.addListener {
                    presenceStatus.value = it.consolidatedPresence
                }

                if (newlyFound) {
                    contactNewlyFoundEvent.value = Event(true)
                }
            } else {
                displayName.value = LinphoneUtils.getDisplayName(remoteAddress)
            }
        } else {
            displayName.value = chatRoom.peerAddress.asStringUriOnly()
        }
    }

    private fun formatLastMessage(msg: ChatMessage?) {
        val lastUpdateTime = chatRoom.lastUpdateTime
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

        if (!oneToOneChatRoom) {
            val sender: String =
                coreContext.contactsManager.findContactByAddress(msg.fromAddress)?.name
                    ?: LinphoneUtils.getDisplayName(msg.fromAddress)
            builder.append(
                coreContext.context.getString(R.string.chat_room_last_message_sender_format, sender)
            )
            builder.append(" ")
        }

        for (content in msg.contents) {
            if (content.isIcalendar) {
                val body = AppUtils.getString(R.string.conference_invitation)
                builder.append(body)
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    builder.length - body.length,
                    builder.length,
                    0
                )
                break
            } else if (content.isVoiceRecording) {
                val body = AppUtils.getString(R.string.chat_message_voice_recording)
                builder.append(body)
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    builder.length - body.length,
                    builder.length,
                    0
                )
                break
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
        return chatRoom.muted
    }
}

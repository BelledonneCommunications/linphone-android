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
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ChatRoomData(private val chatRoom: ChatRoom) : ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val showGroupChatAvatar: Boolean
        get() = conferenceChatRoom && !oneToOneChatRoom
    override val coroutineScope: CoroutineScope = coreContext.coroutineScope

    val unreadMessagesCount = MutableLiveData<Int>()

    val subject = MutableLiveData<String>()

    val securityLevelIcon = MutableLiveData<Int>()

    val securityLevelContentDescription = MutableLiveData<Int>()

    val ephemeralEnabled = MutableLiveData<Boolean>()

    val lastUpdate = MutableLiveData<String>()

    val lastMessageText = MutableLiveData<SpannableStringBuilder>()

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

    init {
        unreadMessagesCount.value = chatRoom.unreadMessagesCount

        subject.value = chatRoom.subject
        updateSecurityIcon()
        ephemeralEnabled.value = chatRoom.isEphemeralEnabled

        contactLookup()
        formatLastMessage(chatRoom.lastMessageInHistory)

        notificationsMuted.value = areNotificationsMuted()
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
            if (chatRoom.participants.isNotEmpty()) {
                chatRoom.participants[0].address
            } else {
                Log.e("[Chat Room] ${chatRoom.peerAddress} doesn't have any participant (state ${chatRoom.state})!")
                null
            }
        }
        if (remoteAddress != null) {
            contact.value = coreContext.contactsManager.findContactByAddress(remoteAddress)
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
            return
        }

        val sender: String =
            coreContext.contactsManager.findContactByAddress(msg.fromAddress)?.name
                ?: LinphoneUtils.getDisplayName(msg.fromAddress)
        builder.append(sender)
        builder.append(": ")

        for (content in msg.contents) {
            if (content.isIcalendar) {
                val body = AppUtils.getString(R.string.conference_invitation)
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

    private fun areNotificationsMuted(): Boolean {
        val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
        return corePreferences.chatRoomMuted(id)
    }
}

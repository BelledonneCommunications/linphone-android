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
package org.linphone.ui.conversations.data

import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.contacts.ContactData
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.utils.TimestampUtils

class ChatMessageData(private val chatMessage: ChatMessage) {
    val id = chatMessage.messageId

    val isOutgoing = chatMessage.isOutgoing

    val contactData = MutableLiveData<ContactData>()

    val state = MutableLiveData<ChatMessage.State>()

    val text = MutableLiveData<String>()

    val time = MutableLiveData<String>()

    val imdnIcon = MutableLiveData<Int>()

    private val chatMessageListener = object : ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            this@ChatMessageData.state.postValue(state)
            computeImdnIcon()
        }
    }

    init {
        state.postValue(chatMessage.state)
        chatMessage.addListener(chatMessageListener)

        computeImdnIcon()
        time.postValue(TimestampUtils.toString(chatMessage.time))
        for (content in chatMessage.contents) {
            if (content.isText) {
                text.postValue(content.utf8Text)
            }
            // TODO FIXME
        }
        contactLookup()
    }

    fun destroy() {
        chatMessage.removeListener(chatMessageListener)
    }

    fun contactLookup() {
        val remoteAddress = chatMessage.fromAddress
        val friend = chatMessage.chatRoom.core.findFriend(remoteAddress)
        if (friend != null) {
            contactData.postValue(ContactData(friend))
        }
    }

    private fun computeImdnIcon() {
        imdnIcon.postValue(
            when (chatMessage.state) {
                ChatMessage.State.DeliveredToUser -> R.drawable.imdn_delivered
                ChatMessage.State.Displayed -> R.drawable.imdn_read
                ChatMessage.State.InProgress -> R.drawable.imdn_sent
                // TODO FIXME
                else -> R.drawable.imdn_sent
            }
        )
    }
}

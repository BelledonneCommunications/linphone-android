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
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ChatMessageModel @WorkerThread constructor(
    val chatMessage: ChatMessage,
    val avatarModel: ContactAvatarModel
) {
    companion object {
        private const val TAG = "[Chat Message Model]"
    }

    val id = chatMessage.messageId

    val isOutgoing = chatMessage.isOutgoing

    val state = MutableLiveData<ChatMessage.State>()

    val text = LinphoneUtils.getTextDescribingMessage(chatMessage)

    val fromSipUri = chatMessage.fromAddress.asStringUriOnly()

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val dismissLongPressMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, messageState: ChatMessage.State?) {
            state.postValue(chatMessage.state)
        }

        @WorkerThread
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            Log.i(
                "$TAG New reaction [${reaction.body}] from [${reaction.fromAddress.asStringUriOnly()}] for chat message with ID [$id]"
            )
        }

        @WorkerThread
        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i("$TAG A reaction was removed for chat message with ID [$id]")
        }
    }

    init {
        chatMessage.addListener(chatMessageListener)
        state.postValue(chatMessage.state)
    }

    @WorkerThread
    fun destroy() {
        chatMessage.removeListener(chatMessageListener)
    }

    @UiThread
    fun sendReaction(emoji: String) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Sending reaction [$emoji] to chat message with ID [$id]")
            val reaction = chatMessage.createReaction(emoji)
            reaction.send()
            dismissLongPressMenuEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun showDeliveryInfo() {
    }
}

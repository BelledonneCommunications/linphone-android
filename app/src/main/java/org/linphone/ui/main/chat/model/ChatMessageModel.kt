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
    val avatarModel: ContactAvatarModel,
    val isFromGroup: Boolean,
    val isGroupedWithPreviousOne: Boolean,
    val isGroupedWithNextOne: Boolean
) {
    companion object {
        private const val TAG = "[Chat Message Model]"
    }

    val id = chatMessage.messageId

    val isOutgoing = chatMessage.isOutgoing

    val statusIcon = MutableLiveData<Int>()

    val text = LinphoneUtils.getTextDescribingMessage(chatMessage)

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val chatRoomIsReadOnly = chatMessage.chatRoom.isReadOnly

    val deliveryModels = MutableLiveData<ArrayList<ChatMessageDeliveryModel>>()

    val dismissLongPressMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, messageState: ChatMessage.State?) {
            computeStatusIcon(chatMessage.state)
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
        computeStatusIcon(chatMessage.state)

        computeDeliveryStatus()
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

    @WorkerThread
    private fun computeStatusIcon(state: ChatMessage.State) {
        val icon = when (state) {
            ChatMessage.State.Displayed -> {
                R.drawable.checks
            }
            ChatMessage.State.DeliveredToUser -> {
                R.drawable.check
            }
            ChatMessage.State.Delivered -> {
                R.drawable.envelope_simple
            }
            ChatMessage.State.NotDelivered -> {
                R.drawable.warning_circle
            }
            else -> {
                R.drawable.in_progress
            }
        }
        statusIcon.postValue(icon)
    }

    @WorkerThread
    private fun computeDeliveryStatus() {
        val list = arrayListOf<ChatMessageDeliveryModel>()

        /*for (participant in chatMessage.getParticipantsByImdnState(ChatMessage.State.Displayed)) {
            list.add(ChatMessageDeliveryModel(participant))
        }*/

        deliveryModels.postValue(list)
    }
}

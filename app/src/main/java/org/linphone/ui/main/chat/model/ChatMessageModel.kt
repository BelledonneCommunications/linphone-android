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

import android.text.Spannable
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.util.regex.Pattern
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PatternClickableSpan
import org.linphone.utils.TimestampUtils

class ChatMessageModel @WorkerThread constructor(
    val chatMessage: ChatMessage,
    val avatarModel: ContactAvatarModel,
    val isFromGroup: Boolean,
    val isReply: Boolean,
    val replyText: String,
    val replyToMessageId: String?,
    val isGroupedWithPreviousOne: Boolean,
    val isGroupedWithNextOne: Boolean
) {
    companion object {
        private const val TAG = "[Chat Message Model]"
    }

    val id = chatMessage.messageId

    val isOutgoing = chatMessage.isOutgoing

    val statusIcon = MutableLiveData<Int>()

    val text = MutableLiveData<Spannable>()

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val chatRoomIsReadOnly = chatMessage.chatRoom.isReadOnly

    val reactions = MutableLiveData<String>()

    val dismissLongPressMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, messageState: ChatMessage.State?) {
            statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))
        }

        @WorkerThread
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            Log.i(
                "$TAG New reaction [${reaction.body}] from [${reaction.fromAddress.asStringUriOnly()}] for chat message with ID [$id]"
            )
            updateReactionsList()
        }

        @WorkerThread
        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i("$TAG A reaction was removed for chat message with ID [$id]")
            updateReactionsList()
        }
    }

    init {
        chatMessage.addListener(chatMessageListener)
        statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))
        updateReactionsList()

        var textFound = false
        for (content in chatMessage.contents) {
            if (content.isText) {
                val textContent = content.utf8Text.orEmpty().trim()
                val spannable = Spannable.Factory.getInstance().newSpannable(textContent)
                text.postValue(
                    PatternClickableSpan()
                        .add(
                            Pattern.compile(
                                "(?:<?sips?:)[a-zA-Z0-9+_.\\-]+(?:@([a-zA-Z0-9+_.\\-;=~]+))+(>)?"
                            ),
                            object : PatternClickableSpan.SpannableClickedListener {
                                @UiThread
                                override fun onSpanClicked(text: String) {
                                    coreContext.postOnCoreThread {
                                        Log.i("$TAG Clicked on SIP URI: $text")
                                        val address = coreContext.core.interpretUrl(text)
                                        if (address != null) {
                                            coreContext.startCall(address)
                                        } else {
                                            Log.w("$TAG Failed to parse [$text] as SIP URI")
                                        }
                                    }
                                }
                            }
                        ).build(spannable)
                )
                textFound = true
            }
        }
        if (!textFound) {
            val describe = LinphoneUtils.getTextDescribingMessage(chatMessage)
            val spannable = Spannable.Factory.getInstance().newSpannable(describe)
            text.postValue(spannable)
        }
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
    private fun updateReactionsList() {
        var reactionsList = ""
        val allReactions = chatMessage.reactions

        var sameReactionTwiceOrMore = false
        if (allReactions.isNotEmpty()) {
            for (reaction in allReactions) {
                val body = reaction.body
                if (!reactionsList.contains(body)) {
                    reactionsList += body
                } else {
                    sameReactionTwiceOrMore = true
                }
            }

            if (sameReactionTwiceOrMore) {
                reactionsList += allReactions.size.toString()
            }
        }

        Log.i("$TAG Reactions for message [$id] are [$reactionsList]")
        reactions.postValue(reactionsList)
    }
}

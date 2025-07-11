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

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.tools.Log

class MessageReactionsModel
    @WorkerThread
    constructor(
    private val chatMessage: ChatMessage,
    private val onReactionsUpdated: ((model: MessageReactionsModel) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Message Reactions Model]"
    }

    val allReactions = MutableLiveData<ArrayList<MessageBottomSheetParticipantModel>>()

    val differentReactions = arrayListOf<String>()

    val reactionsMap = HashMap<String, Int>()

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i("$TAG Reaction has been removed, updating reactions list")
            computeReactions()
        }

        @WorkerThread
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            Log.i("$TAG A new reaction has been received, updating reactions list")
            computeReactions()
        }
    }

    init {
        chatMessage.addListener(chatMessageListener)
        computeReactions()
    }

    @WorkerThread
    fun destroy() {
        chatMessage.removeListener(chatMessageListener)
    }

    fun filterReactions(emoji: String): ArrayList<MessageBottomSheetParticipantModel> {
        val filteredList = arrayListOf<MessageBottomSheetParticipantModel>()

        for (reaction in allReactions.value.orEmpty()) {
            if (reaction.value == emoji) {
                filteredList.add(reaction)
            }
        }

        return filteredList
    }

    @WorkerThread
    private fun computeReactions() {
        reactionsMap.clear()
        differentReactions.clear()

        val allReactionsList = arrayListOf<MessageBottomSheetParticipantModel>()
        for (reaction in chatMessage.reactions) {
            val body = reaction.body
            val count = reactionsMap.getOrDefault(body, 0)
            reactionsMap[body] = count + 1
            Log.i("$TAG Found reaction with body [$body] (count = ${count + 1}) from [${reaction.fromAddress.asStringUriOnly()}]")

            val isOurOwn = reaction.fromAddress.weakEqual(chatMessage.chatRoom.localAddress)
            allReactionsList.add(
                MessageBottomSheetParticipantModel(
                    reaction.fromAddress,
                    reaction.body,
                    -1,
                    isOurOwn
                ) {
                    if (isOurOwn) {
                        coreContext.postOnCoreThread {
                            Log.i(
                                "$TAG Removing our own reaction for message [${chatMessage.messageId}]"
                            )
                            val removeReaction = chatMessage.createReaction("")
                            removeReaction.send()
                        }
                    }
                }
            )

            if (!differentReactions.contains(body)) {
                differentReactions.add(body)
            }
        }

        Log.i(
            "$TAG [${differentReactions.size}] reactions found on a total of [${allReactionsList.size}]"
        )
        allReactions.postValue(allReactionsList)
        onReactionsUpdated?.invoke(this)
    }
}

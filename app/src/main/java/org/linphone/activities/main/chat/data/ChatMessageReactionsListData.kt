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

import androidx.lifecycle.MutableLiveData
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.tools.Log

class ChatMessageReactionsListData(private val chatMessage: ChatMessage) {
    val reactions = MutableLiveData<ArrayList<ChatMessageReaction>>()

    val filteredReactions = MutableLiveData<ArrayList<ChatMessageReactionData>>()

    val reactionsMap = HashMap<String, Int>()

    val listener = object : ChatMessageListenerStub() {
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            val address = reaction.fromAddress
            Log.i(
                "[Chat Message Reactions List] Reaction received [${reaction.body}] from [${address.asStringUriOnly()}]"
            )
            updateReactionsList(message)
        }

        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i(
                "[Chat Message Reactions List] Reaction removed by [${address.asStringUriOnly()}]"
            )
            updateReactionsList(message)
        }
    }

    private var filter = ""

    init {
        chatMessage.addListener(listener)

        updateReactionsList(chatMessage)
    }

    fun onDestroy() {
        chatMessage.removeListener(listener)
    }

    fun updateFilteredReactions(newFilter: String) {
        filter = newFilter
        filteredReactions.value.orEmpty().forEach(ChatMessageReactionData::destroy)

        val reactionsList = arrayListOf<ChatMessageReactionData>()
        for (reaction in reactions.value.orEmpty()) {
            if (filter.isEmpty() || filter == reaction.body) {
                val data = ChatMessageReactionData(reaction)
                reactionsList.add(data)
            }
        }
        filteredReactions.value = reactionsList
    }

    private fun updateReactionsList(chatMessage: ChatMessage) {
        reactionsMap.clear()

        val reactionsList = arrayListOf<ChatMessageReaction>()
        for (reaction in chatMessage.reactions) {
            val body = reaction.body
            val count = if (reactionsMap.containsKey(body)) {
                reactionsMap[body] ?: 0
            } else {
                0
            }
            // getOrDefault isn't available for API 23 :'(
            reactionsMap[body] = count + 1
            reactionsList.add(reaction)
        }
        reactions.value = reactionsList

        updateFilteredReactions(filter)
    }
}

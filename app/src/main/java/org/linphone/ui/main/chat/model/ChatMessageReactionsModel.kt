package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ChatMessage
import org.linphone.core.tools.Log

class ChatMessageReactionsModel @WorkerThread constructor(
    private val chatMessage: ChatMessage
) {
    companion object {
        private const val TAG = "[Chat Message Reactions Model]"
    }

    val allReactions = arrayListOf<ChatMessageBottomSheetParticipantModel>()

    val differentReactions = MutableLiveData<ArrayList<String>>()

    val reactionsMap = HashMap<String, Int>()

    init {
        computeReactions()
        // TODO: add listener to update in real time the lists
    }

    fun filterReactions(emoji: String): ArrayList<ChatMessageBottomSheetParticipantModel> {
        val filteredList = arrayListOf<ChatMessageBottomSheetParticipantModel>()

        for (reaction in allReactions) {
            if (reaction.value == emoji) {
                filteredList.add(reaction)
            }
        }

        return filteredList
    }

    @WorkerThread
    private fun computeReactions() {
        reactionsMap.clear()
        allReactions.clear()

        val differentReactionsList = arrayListOf<String>()
        for (reaction in chatMessage.reactions) {
            val body = reaction.body
            val count = reactionsMap.getOrDefault(body, 0)
            reactionsMap[body] = count + 1

            val isOurOwn = reaction.fromAddress.weakEqual(chatMessage.chatRoom.localAddress)
            allReactions.add(
                ChatMessageBottomSheetParticipantModel(
                    reaction.fromAddress,
                    body,
                    isOurOwn
                ) {
                    if (isOurOwn) {
                        coreContext.postOnCoreThread {
                            Log.i(
                                "$TAG Removing our own reaction for chat message [${chatMessage.messageId}]"
                            )
                            val removeReaction = chatMessage.createReaction("")
                            removeReaction.send()
                        }
                    }
                }
            )

            if (!differentReactionsList.contains(body)) {
                differentReactionsList.add(body)
            }
        }

        Log.i(
            "$TAG [${differentReactionsList.size}] reactions found on a total of [${allReactions.size}]"
        )
        differentReactions.postValue(differentReactionsList)
    }
}

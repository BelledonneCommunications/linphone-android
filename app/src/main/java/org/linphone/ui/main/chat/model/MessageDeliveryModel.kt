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
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessage.State
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ParticipantImdnState
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.TimestampUtils

class MessageDeliveryModel
    @WorkerThread
    constructor(
    private val chatMessage: ChatMessage,
    private val onDeliveryUpdated: ((model: MessageDeliveryModel) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Message Delivery Model]"
    }

    val readLabel = MutableLiveData<String>()

    val receivedLabel = MutableLiveData<String>()

    val sentLabel = MutableLiveData<String>()

    val errorLabel = MutableLiveData<String>()

    val displayedModels = arrayListOf<MessageBottomSheetParticipantModel>()

    private val deliveredModels = arrayListOf<MessageBottomSheetParticipantModel>()

    private val sentModels = arrayListOf<MessageBottomSheetParticipantModel>()

    private val errorModels = arrayListOf<MessageBottomSheetParticipantModel>()

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onParticipantImdnStateChanged(
            message: ChatMessage,
            state: ParticipantImdnState
        ) {
            computeDeliveryStatus()
        }
    }

    init {
        chatMessage.addListener(chatMessageListener)
        computeDeliveryStatus()
    }

    @WorkerThread
    fun destroy() {
        chatMessage.removeListener(chatMessageListener)
    }

    @UiThread
    fun computeListForState(state: State): ArrayList<MessageBottomSheetParticipantModel> {
        return when (state) {
            State.DeliveredToUser -> {
                deliveredModels
            }
            State.Delivered -> {
                sentModels
            }
            State.NotDelivered -> {
                errorModels
            }
            else -> {
                displayedModels
            }
        }
    }

    @WorkerThread
    private fun computeDeliveryStatus() {
        displayedModels.clear()
        deliveredModels.clear()
        sentModels.clear()
        errorModels.clear()

        for (participant in chatMessage.getParticipantsByImdnState(State.Displayed)) {
            val timestamp = participant.stateChangeTime
            displayedModels.add(
                MessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.toString(timestamp),
                    timestamp
                )
            )
        }
        val readCount = displayedModels.size.toString()
        readLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_read_title,
                readCount
            )
        )

        for (participant in chatMessage.getParticipantsByImdnState(State.DeliveredToUser)) {
            val timestamp = participant.stateChangeTime
            deliveredModels.add(
                MessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.toString(timestamp),
                    timestamp
                )
            )
        }
        val receivedCount = deliveredModels.size.toString()
        receivedLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_received_title,
                receivedCount
            )
        )

        for (participant in chatMessage.getParticipantsByImdnState(State.Delivered)) {
            val timestamp = participant.stateChangeTime
            sentModels.add(
                MessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.toString(timestamp),
                    timestamp
                )
            )
        }
        val sentCount = sentModels.size.toString()
        sentLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_sent_title,
                sentCount
            )
        )

        for (participant in chatMessage.getParticipantsByImdnState(State.NotDelivered)) {
            val timestamp = participant.stateChangeTime
            errorModels.add(
                MessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.toString(timestamp),
                    timestamp
                )
            )
        }
        val errorCount = errorModels.size.toString()
        errorLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_error_title,
                errorCount
            )
        )

        displayedModels.sortBy { it.timestamp }
        deliveredModels.sortBy { it.timestamp }
        sentModels.sortBy { it.timestamp }
        errorModels.sortBy { it.timestamp }

        Log.i("$TAG Message ID [${chatMessage.messageId}] is in state [${chatMessage.state}]")
        Log.i(
            "$TAG There are [$readCount] that have read this message, [$receivedCount] that have received it, [$sentCount] that haven't received it yet and [$errorCount] that probably won't receive it due to an error"
        )
        onDeliveryUpdated?.invoke(this)
    }
}

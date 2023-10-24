package org.linphone.ui.main.chat.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessage.State
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.TimestampUtils

class ChatMessageDeliveryModel @WorkerThread constructor(
    private val chatMessage: ChatMessage
) {
    companion object {
        private const val TAG = "[Chat Message Delivery Model]"
    }

    val readLabel = MutableLiveData<String>()

    val receivedLabel = MutableLiveData<String>()

    val sentLabel = MutableLiveData<String>()

    val errorLabel = MutableLiveData<String>()

    val displayedModels = arrayListOf<ChatMessageBottomSheetParticipantModel>()

    private val deliveredModels = arrayListOf<ChatMessageBottomSheetParticipantModel>()

    private val sentModels = arrayListOf<ChatMessageBottomSheetParticipantModel>()

    private val errorModels = arrayListOf<ChatMessageBottomSheetParticipantModel>()

    init {
        computeDeliveryStatus()

        // TODO: add listener to update in real time the lists
    }

    @UiThread
    fun computeListForState(state: State): ArrayList<ChatMessageBottomSheetParticipantModel> {
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
        for (participant in chatMessage.getParticipantsByImdnState(State.Displayed)) {
            displayedModels.add(
                ChatMessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.timeToString(participant.stateChangeTime)
                )
            )
        }
        if (!chatMessage.isOutgoing) {
            // Always add ourselves to prevent empty list
            displayedModels.add(
                ChatMessageBottomSheetParticipantModel(
                    chatMessage.localAddress,
                    TimestampUtils.timeToString(chatMessage.time)
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
            deliveredModels.add(
                ChatMessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.timeToString(participant.stateChangeTime)
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
            sentModels.add(
                ChatMessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.timeToString(participant.stateChangeTime)
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
            errorModels.add(
                ChatMessageBottomSheetParticipantModel(
                    participant.participant.address,
                    TimestampUtils.timeToString(participant.stateChangeTime)
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

        Log.i("$TAG Message ID [${chatMessage.messageId}] is in state [${chatMessage.state}]")
        Log.i(
            "$TAG There are [$readCount] that have read this message, [$receivedCount] that have received it, [$sentCount] that haven't received it yet and [$errorCount] that probably won't receive it due to an error"
        )
    }
}

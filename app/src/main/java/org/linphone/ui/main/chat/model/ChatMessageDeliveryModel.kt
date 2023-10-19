package org.linphone.ui.main.chat.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessage.State
import org.linphone.utils.AppUtils

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

    val deliveryModels = MutableLiveData<ArrayList<ChatMessageParticipantDeliveryModel>>()

    private val displayedModels = arrayListOf<ChatMessageParticipantDeliveryModel>()

    private val deliveredModels = arrayListOf<ChatMessageParticipantDeliveryModel>()

    private val sentModels = arrayListOf<ChatMessageParticipantDeliveryModel>()

    private val errorModels = arrayListOf<ChatMessageParticipantDeliveryModel>()

    init {
        computeDeliveryStatus()
    }

    @UiThread
    fun computeListForState(state: State) {
        when (state) {
            State.DeliveredToUser -> {
                deliveryModels.value = deliveredModels
            }
            State.Delivered -> {
                deliveryModels.value = sentModels
            }
            State.NotDelivered -> {
                deliveryModels.value = errorModels
            }
            else -> {
                deliveryModels.value = displayedModels
            }
        }
    }

    @WorkerThread
    private fun computeDeliveryStatus() {
        for (participant in chatMessage.getParticipantsByImdnState(State.Displayed)) {
            displayedModels.add(ChatMessageParticipantDeliveryModel(participant))
        }
        readLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_read_title,
                displayedModels.size.toString()
            )
        )

        for (participant in chatMessage.getParticipantsByImdnState(State.DeliveredToUser)) {
            deliveredModels.add(ChatMessageParticipantDeliveryModel(participant))
        }
        receivedLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_received_title,
                deliveredModels.size.toString()
            )
        )

        for (participant in chatMessage.getParticipantsByImdnState(State.Delivered)) {
            sentModels.add(ChatMessageParticipantDeliveryModel(participant))
        }
        sentLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_sent_title,
                sentModels.size.toString()
            )
        )

        for (participant in chatMessage.getParticipantsByImdnState(State.NotDelivered)) {
            errorModels.add(ChatMessageParticipantDeliveryModel(participant))
        }
        errorLabel.postValue(
            AppUtils.getFormattedString(
                R.string.message_delivery_info_error_title,
                errorModels.size.toString()
            )
        )

        deliveryModels.postValue(displayedModels)
    }
}

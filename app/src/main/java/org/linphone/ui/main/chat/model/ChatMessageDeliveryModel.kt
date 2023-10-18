package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import org.linphone.core.Friend
import org.linphone.core.ParticipantImdnState
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.TimestampUtils

class ChatMessageDeliveryModel @WorkerThread constructor(
    friend: Friend,
    imdnState: ParticipantImdnState
) : ContactAvatarModel(friend) {
    val time = TimestampUtils.toString(imdnState.stateChangeTime)
}

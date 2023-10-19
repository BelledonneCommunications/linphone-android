package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication
import org.linphone.core.ParticipantImdnState
import org.linphone.utils.TimestampUtils

class ChatMessageParticipantDeliveryModel @WorkerThread constructor(
    imdnState: ParticipantImdnState
) {
    val address = imdnState.participant.address

    val avatarModel = LinphoneApplication.coreContext.contactsManager.getContactAvatarModelForAddress(
        address
    )

    val time = TimestampUtils.toString(imdnState.stateChangeTime)
}

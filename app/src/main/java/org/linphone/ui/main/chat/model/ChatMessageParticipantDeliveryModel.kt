package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.utils.TimestampUtils

class ChatMessageParticipantDeliveryModel @WorkerThread constructor(
    address: Address,
    timestamp: Long
) {
    val sipUri = address.asStringUriOnly()

    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)

    val time = TimestampUtils.toString(timestamp)
}

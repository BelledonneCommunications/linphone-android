package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address

class ChatMessageBottomSheetParticipantModel @WorkerThread constructor(
    address: Address,
    val value: String
) {
    val sipUri = address.asStringUriOnly()

    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)
}

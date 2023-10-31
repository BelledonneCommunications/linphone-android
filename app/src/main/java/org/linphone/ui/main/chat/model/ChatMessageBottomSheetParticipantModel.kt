package org.linphone.ui.main.chat.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address

class ChatMessageBottomSheetParticipantModel @WorkerThread constructor(
    address: Address,
    val value: String,
    val isOurOwnReaction: Boolean = false,
    val onClick: (() -> Unit)? = null
) {
    val sipUri = address.asStringUriOnly()

    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)

    @UiThread
    fun clicked() {
        onClick?.invoke()
    }
}

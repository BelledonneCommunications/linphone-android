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
package org.linphone.ui.main.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class ConversationContactOrSuggestionModel
    @WorkerThread
    constructor(
    val address: Address,
    val conversationId: String = "",
    conversationSubject: String? = null,
    val friend: Friend? = null,
    val defaultAccountDomain: String? = null,
    private val onClicked: ((Address) -> Unit)? = null
) {
    val id = friend?.refKey ?: address.asStringUriOnly().hashCode()

    val starred = friend?.starred == true

    val name = conversationSubject
        ?: if (friend != null) {
            friend.name ?: LinphoneUtils.getDisplayName(address)
        } else {
            address.username ?: address.domain.orEmpty()
        }

    // Hide SIP address and only show username for suggestions
    // on the same domain as the currently selected account
    val sipUri = if (!defaultAccountDomain.isNullOrEmpty() && defaultAccountDomain == address.domain) {
        address.username
    } else {
        address.asStringUriOnly()
    }

    val initials = AppUtils.getInitials(conversationSubject ?: name)

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val selected = MutableLiveData<Boolean>()

    @UiThread
    fun onClicked() {
        onClicked?.invoke(address)
    }
}

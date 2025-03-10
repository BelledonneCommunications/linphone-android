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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Address

class MessageBottomSheetParticipantModel
    @WorkerThread
    constructor(
    address: Address,
    val value: String,
    val timestamp: Long,
    val isOurOwnReaction: Boolean = false,
    val onClick: (() -> Unit)? = null
) {
    val sipUri = address.asStringUriOnly()

    val showSipUri = MutableLiveData<Boolean>()

    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)

    init {
        showSipUri.postValue(false)
    }

    @UiThread
    fun toggleShowSipUri() {
        if (!isOurOwnReaction && !corePreferences.onlyDisplaySipUriUsername) {
            showSipUri.postValue(showSipUri.value == false)
        } else {
            clicked()
        }
    }

    @UiThread
    fun clicked() {
        onClick?.invoke()
    }
}

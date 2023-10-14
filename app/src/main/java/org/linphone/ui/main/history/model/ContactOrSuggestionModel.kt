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
package org.linphone.ui.main.history.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class ContactOrSuggestionModel @WorkerThread constructor(
    val address: Address,
    val friend: Friend? = null,
    private val onClicked: ((Address) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Suggestion Model]"
    }

    val id = friend?.refKey ?: address.asStringUriOnly().hashCode()

    val name = LinphoneUtils.getDisplayName(address)

    val initials = AppUtils.getInitials(name)

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    @UiThread
    fun onClicked() {
        onClicked?.invoke(address)
    }
}

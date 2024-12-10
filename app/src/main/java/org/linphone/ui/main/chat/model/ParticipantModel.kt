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

import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.ui.main.contacts.model.ContactAvatarModel

class ParticipantModel
    @WorkerThread
    constructor(
    val address: Address,
    val isMyselfAdmin: Boolean = false,
    val isParticipantAdmin: Boolean = false,
    val showMenu: Boolean = false,
    val isParticipantMyself: Boolean = false,
    private val onClicked: ((model: ParticipantModel) -> Unit)? = null,
    private val onMenuClicked: ((view: View, model: ParticipantModel) -> Unit)? = null
) {
    val sipUri = address.asStringUriOnly()

    val avatarModel: ContactAvatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
        address
    )

    val refKey: String = avatarModel.friend.refKey.orEmpty()

    val friendAvailable: Boolean = coreContext.contactsManager.isContactAvailable(
        avatarModel.friend
    )

    @UiThread
    fun onClicked() {
        onClicked?.invoke(this)
    }

    @UiThread
    fun openMenu(view: View) {
        onMenuClicked?.invoke(view, this)
    }
}

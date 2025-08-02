/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package com.naminfo.ui.main.contacts.model

import androidx.annotation.UiThread
import org.linphone.contacts.AbstractAvatarModel
import org.linphone.core.SecurityLevel

@UiThread
class ContactTrustDialogModel(initials: String, picturePath: String) {
    val avatarModel = FakeAvatarModel(initials, picturePath, false)

    val trustedAvatarModel = FakeAvatarModel(initials, picturePath, true)

    @UiThread
    class FakeAvatarModel(contactInitials: String, contactPicturePath: String, trusted: Boolean) : AbstractAvatarModel() {
        init {
            initials.value = contactInitials
            picturePath.value = contactPicturePath
            showTrust.value = trusted
            trust.value = SecurityLevel.EndToEndEncryptedAndVerified
        }
    }
}

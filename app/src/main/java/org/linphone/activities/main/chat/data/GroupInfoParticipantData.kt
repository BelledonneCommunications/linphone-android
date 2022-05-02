/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.data

import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.activities.main.chat.GroupChatRoomMember
import org.linphone.contact.GenericContactData
import org.linphone.core.ChatRoomSecurityLevel
import org.linphone.utils.LinphoneUtils

class GroupInfoParticipantData(val participant: GroupChatRoomMember) : GenericContactData(participant.address) {
    val sipUri: String get() = LinphoneUtils.getDisplayableAddress(participant.address)

    val isAdmin = MutableLiveData<Boolean>()

    val showAdminControls = MutableLiveData<Boolean>()

    // A participant not yet added to a group can't be set admin at the same time it's added
    val canBeSetAdmin = MutableLiveData<Boolean>()

    val securityLevelIcon: Int by lazy {
        when (participant.securityLevel) {
            ChatRoomSecurityLevel.Safe -> R.drawable.security_2_indicator
            ChatRoomSecurityLevel.Encrypted -> R.drawable.security_1_indicator
            else -> R.drawable.security_alert_indicator
        }
    }

    val securityLevelContentDescription: Int by lazy {
        when (participant.securityLevel) {
            ChatRoomSecurityLevel.Safe -> R.string.content_description_security_level_safe
            ChatRoomSecurityLevel.Encrypted -> R.string.content_description_security_level_encrypted
            else -> R.string.content_description_security_level_unsafe
        }
    }

    init {
        securityLevel.value = participant.securityLevel
        isAdmin.value = participant.isAdmin
        showAdminControls.value = false
        canBeSetAdmin.value = participant.canBeSetAdmin
    }

    fun setAdmin() {
        isAdmin.value = true
        participant.isAdmin = true
    }

    fun unSetAdmin() {
        isAdmin.value = false
        participant.isAdmin = false
    }
}

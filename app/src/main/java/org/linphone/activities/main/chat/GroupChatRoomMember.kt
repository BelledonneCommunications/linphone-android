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
package org.linphone.activities.main.chat

import org.linphone.core.Address
import org.linphone.core.ChatRoomSecurityLevel

data class GroupChatRoomMember(
    val address: Address,
    var isAdmin: Boolean = false,
    val securityLevel: ChatRoomSecurityLevel = ChatRoomSecurityLevel.ClearText,
    val hasLimeX3DHCapability: Boolean = false,
    // A participant not yet added to a group can't be set admin at the same time it's added
    val canBeSetAdmin: Boolean = false
)

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
package com.naminfo.contacts

import androidx.lifecycle.MutableLiveData
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.SecurityLevel

abstract class AbstractAvatarModel {
    val trust = MutableLiveData<SecurityLevel>()

    val showTrust = MutableLiveData<Boolean>()

    val initials = MutableLiveData<String>()

    val picturePath = MutableLiveData<String>()

    val forceConversationIcon = MutableLiveData<Boolean>()

    val forceConferenceIcon = MutableLiveData<Boolean>()

    val defaultToConversationIcon = MutableLiveData<Boolean>()

    val defaultToConferenceIcon = MutableLiveData<Boolean>()

    val skipInitials = MutableLiveData<Boolean>()

    val presenceStatus = MutableLiveData<ConsolidatedPresence>()
}

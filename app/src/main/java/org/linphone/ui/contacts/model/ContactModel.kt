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
package org.linphone.ui.contacts.model

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Friend
import org.linphone.core.FriendListenerStub
import org.linphone.utils.LinphoneUtils

class ContactModel(val friend: Friend) {
    val id = friend.refKey

    val initials = LinphoneUtils.getInitials(friend.name.orEmpty())

    val presenceStatus = MutableLiveData<ConsolidatedPresence>()

    val name = MutableLiveData<String>()

    private val friendListener = object : FriendListenerStub() {
        override fun onPresenceReceived(fr: Friend) {
            presenceStatus.postValue(fr.consolidatedPresence)
        }
    }

    init {
        // Core thread
        name.postValue(friend.name)
        presenceStatus.postValue(friend.consolidatedPresence)

        friend.addListener(friendListener)

        presenceStatus.postValue(ConsolidatedPresence.Offline)
    }

    fun destroy() {
        // Core thread
        friend.removeListener(friendListener)
    }

    fun getAvatarUri(): Uri? {
        // Core thread
        val refKey = friend.refKey
        if (refKey != null) {
            val lookupUri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                refKey.toLong()
            )
            return Uri.withAppendedPath(
                lookupUri,
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        }

        return null
    }
}

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
package org.linphone.ui.main.contacts.model

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Friend
import org.linphone.core.FriendListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class ContactAvatarModel(val friend: Friend) {
    companion object {
        const val TAG = "[Contact Avatar Model]"
    }

    val id = friend.refKey

    val avatar = MutableLiveData<Uri>()

    val initials = LinphoneUtils.getInitials(friend.name.orEmpty())

    val presenceStatus = MutableLiveData<ConsolidatedPresence>()

    val name = MutableLiveData<String>()

    val firstLetter: String = LinphoneUtils.getFirstLetter(friend.name.orEmpty())

    val firstContactStartingByThatLetter = MutableLiveData<Boolean>()

    val noAlphabet = MutableLiveData<Boolean>()

    private val friendListener = object : FriendListenerStub() {
        override fun onPresenceReceived(fr: Friend) {
            Log.d(
                "$TAG Presence received for friend [${fr.name}]: [${friend.consolidatedPresence}]"
            )
            presenceStatus.postValue(fr.consolidatedPresence)
        }
    }

    init {
        // Core thread
        friend.addListener(friendListener)

        name.postValue(friend.name)
        presenceStatus.postValue(friend.consolidatedPresence)
        Log.d("$TAG Friend [${friend.name}] presence status is [${friend.consolidatedPresence}]")
        avatar.postValue(getAvatarUri())
    }

    fun destroy() {
        // Core thread
        friend.removeListener(friendListener)
    }

    private fun getAvatarUri(): Uri? {
        // Core thread
        val picturePath = friend.photo
        if (!picturePath.isNullOrEmpty()) {
            return Uri.parse(picturePath)
        }

        val refKey = friend.refKey
        if (refKey != null) {
            try {
                val lookupUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI,
                    refKey.toLong()
                )
                return Uri.withAppendedPath(
                    lookupUri,
                    ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
                )
            } catch (numberFormatException: NumberFormatException) {
                // Expected for contacts created by Linphone
            }
        }

        return null
    }
}

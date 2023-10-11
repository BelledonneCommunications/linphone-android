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
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.ChatRoom.SecurityLevel
import org.linphone.core.Friend
import org.linphone.core.tools.Log

class GroupAvatarModel @WorkerThread constructor(friends: ArrayList<Friend>) {
    companion object {
        private const val TAG = "[Group Avatar Model]"
    }

    val trust = MutableLiveData<SecurityLevel>()

    val uris = MutableLiveData<List<Uri>>()

    init {
        trust.postValue(SecurityLevel.Safe) // TODO FIXME: use API

        val list = arrayListOf<Uri>()
        Log.d("$TAG [${friends.size}] friends to use")
        for (friend in friends) {
            val uri = getAvatarUri(friend)
            if (uri != null) {
                if (!list.contains(uri)) {
                    list.add(uri)
                }
            }
        }
        uris.postValue(list.toList())
    }

    @WorkerThread
    private fun getAvatarUri(friend: Friend): Uri? {
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

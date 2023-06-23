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
package org.linphone.utils

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import java.io.IOException
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.Friend

class LinphoneUtils {
    companion object {
        private fun getChatRoomId(localAddress: Address, remoteAddress: Address): String {
            val localSipUri = localAddress.clone()
            localSipUri.clean()
            val remoteSipUri = remoteAddress.clone()
            remoteSipUri.clean()
            return "${localSipUri.asStringUriOnly()}~${remoteSipUri.asStringUriOnly()}"
        }

        fun getChatRoomId(chatRoom: ChatRoom): String {
            return getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
        }

        fun getDisplayName(address: Address?): String {
            if (address == null) return "[null]"
            if (address.displayName == null) {
                val account = coreContext.core.accountList.find { account ->
                    account.params.identityAddress?.asStringUriOnly() == address.asStringUriOnly()
                }
                val localDisplayName = account?.params?.identityAddress?.displayName
                // Do not return an empty local display name
                if (localDisplayName != null && localDisplayName.isNotEmpty()) {
                    return localDisplayName
                }
            }
            // Do not return an empty display name
            return address.displayName ?: address.username ?: address.asString()
        }

        fun Friend.getPictureUri(thumbnailPreferred: Boolean = false): Uri? {
            val refKey = refKey
            if (refKey != null) {
                try {
                    val lookupUri = ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        refKey.toLong()
                    )

                    if (!thumbnailPreferred) {
                        val pictureUri = Uri.withAppendedPath(
                            lookupUri,
                            ContactsContract.Contacts.Photo.DISPLAY_PHOTO
                        )
                        // Check that the URI points to a real file
                        val contentResolver = coreContext.context.contentResolver
                        try {
                            if (contentResolver.openAssetFileDescriptor(pictureUri, "r") != null) {
                                return pictureUri
                            }
                        } catch (ioe: IOException) { }
                    }

                    // Fallback to thumbnail if high res picture isn't available
                    return Uri.withAppendedPath(
                        lookupUri,
                        ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
                    )
                } catch (e: Exception) { }
            } else if (photo != null) {
                try {
                    return Uri.parse(photo)
                } catch (e: Exception) { }
            }
            return null
        }
    }
}

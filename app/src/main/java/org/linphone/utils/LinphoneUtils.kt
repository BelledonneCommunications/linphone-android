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
import androidx.emoji2.text.EmojiCompat
import java.io.IOException
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.Friend
import org.linphone.core.tools.Log

class LinphoneUtils {
    companion object {
        private val emojiCompat: EmojiCompat?
            get() = initEmojiCompat()

        private fun initEmojiCompat(): EmojiCompat? {
            return try {
                EmojiCompat.get()
            } catch (ise: IllegalStateException) {
                Log.w(
                    "[App Utils] EmojiCompat.get() triggered IllegalStateException [$ise], trying manual init"
                )
                EmojiCompat.init(coreContext.context)
            }
        }

        fun getFirstLetter(displayName: String): String {
            return getInitials(displayName, 1)
        }

        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.uppercase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0
            val emoji = emojiCompat

            for (i in split.indices) {
                if (split[i].isNotEmpty()) {
                    try {
                        if (emoji?.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED && emoji.hasEmojiGlyph(
                                split[i]
                            )
                        ) {
                            val glyph = emoji.process(split[i])
                            if (characters > 0) { // Limit initial to 1 emoji only
                                Log.d("[App Utils] We limit initials to one emoji only")
                                initials = ""
                            }
                            initials += glyph
                            break // Limit initial to 1 emoji only
                        } else {
                            initials += split[i][0]
                        }
                    } catch (ise: IllegalStateException) {
                        Log.e("[App Utils] Can't call hasEmojiGlyph: $ise")
                        initials += split[i][0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

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

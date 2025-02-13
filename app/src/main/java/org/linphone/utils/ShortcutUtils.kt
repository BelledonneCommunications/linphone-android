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

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Bundle
import androidx.annotation.WorkerThread
import androidx.collection.ArraySet
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.AvatarGenerator
import org.linphone.contacts.getPerson
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.MainActivity.Companion.ARGUMENTS_CHAT
import org.linphone.ui.main.MainActivity.Companion.ARGUMENTS_CONVERSATION_ID

class ShortcutUtils {
    companion object {
        private const val TAG = "[Shortcut Utils]"

        @WorkerThread
        fun removeShortcutToChatRoom(chatRoom: ChatRoom) {
            val id = LinphoneUtils.getConversationId(chatRoom)
            Log.i("$TAG Removing shortcut to conversation [$id]")
            ShortcutManagerCompat.removeLongLivedShortcuts(coreContext.context, arrayListOf(id))
        }

        @WorkerThread
        fun createShortcutsToChatRooms(context: Context) {
            if (ShortcutManagerCompat.isRateLimitingActive(context)) {
                Log.e("$TAG Rate limiting is active, aborting")
                return
            }

            Log.i("$TAG Creating dynamic shortcuts for conversations")
            val defaultAccount = coreContext.core.defaultAccount
            if (defaultAccount == null) {
                Log.w("$TAG No default account found, skipping...")
                return
            }

            var count = 0
            for (chatRoom in defaultAccount.chatRooms) {
                if (defaultAccount.params.instantMessagingEncryptionMandatory && !chatRoom.currentParams.isEncryptionEnabled) {
                    Log.w(
                        "$TAG Account is in secure mode, skipping not encrypted conversation [${LinphoneUtils.getConversationId(
                            chatRoom
                        )}]"
                    )
                    continue
                }

                if (count >= 4) {
                    Log.i("$TAG We already created [$count] shortcuts, stopping here")
                    break
                }

                val shortcut: ShortcutInfoCompat? = createChatRoomShortcut(context, chatRoom)
                if (shortcut != null) {
                    Log.i("$TAG Created dynamic shortcut for ${shortcut.shortLabel}")
                    try {
                        val keepGoing = ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                        if (keepGoing) {
                            count += 1
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("$TAG Failed to push dynamic shortcut for ${shortcut.shortLabel}: $e")
                    }
                }
            }
            Log.i("$TAG Created $count dynamic shortcuts")
        }

        @WorkerThread
        private fun createChatRoomShortcut(context: Context, chatRoom: ChatRoom): ShortcutInfoCompat? {
            val peerAddress = chatRoom.peerAddress
            val id = LinphoneUtils.getConversationId(chatRoom)

            try {
                val categories: ArraySet<String> = ArraySet()
                categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION)

                val personsList = arrayListOf<Person>()
                val subject: String
                val icon: IconCompat = if (chatRoom.hasCapability(
                        ChatRoom.Capabilities.Basic.toInt()
                    )
                ) {
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                        peerAddress
                    )
                    val contact = avatarModel.friend
                    val person = contact.getPerson()
                    personsList.add(person)

                    subject = contact.name ?: LinphoneUtils.getDisplayName(peerAddress)
                    person.icon ?: AvatarGenerator(context).setInitials(
                        AppUtils.getInitials(subject)
                    ).buildIcon()
                } else if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt()) && chatRoom.participants.isNotEmpty()) {
                    val address = chatRoom.participants.first().address
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                        address
                    )
                    val contact = avatarModel.friend
                    val person = contact.getPerson()
                    personsList.add(person)

                    subject = contact.name ?: LinphoneUtils.getDisplayName(address)
                    person.icon ?: AvatarGenerator(context).setInitials(
                        AppUtils.getInitials(subject)
                    ).buildIcon()
                } else {
                    subject = chatRoom.subject.orEmpty()
                    AvatarGenerator(context).setInitials(AppUtils.getInitials(subject)).buildIcon()
                }

                val persons = arrayOfNulls<Person>(personsList.size)
                personsList.toArray(persons)

                val args = Bundle()
                args.putString(ARGUMENTS_CONVERSATION_ID, id)

                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClass(context, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.putExtra(ARGUMENTS_CHAT, true)
                intent.putExtra(ARGUMENTS_CONVERSATION_ID, id)

                return ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(subject)
                    .setIcon(icon)
                    .setPersons(persons)
                    .setCategories(categories)
                    .setIntent(intent)
                    .setLongLived(Version.sdkAboveOrEqual(Version.API30_ANDROID_11))
                    .setLocusId(LocusIdCompat(id))
                    .build()
            } catch (e: NumberFormatException) {
                Log.e("$TAG createChatRoomShortcut for id [$id] exception: $e")
            }

            return null
        }

        @WorkerThread
        fun isShortcutToChatRoomAlreadyCreated(context: Context, chatRoom: ChatRoom): Boolean {
            val id = LinphoneUtils.getConversationId(chatRoom)
            val found = ShortcutManagerCompat.getDynamicShortcuts(context).find {
                it.id == id
            }
            return found != null
        }
    }
}

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
        fun removeLegacyShortcuts(context: Context) {
            val dynamicShortcutsToRemove = arrayListOf<String>()
            for (shortcut in ShortcutManagerCompat.getDynamicShortcuts(context)) {
                val id = shortcut.id
                Log.d("$TAG Found dynamic shortcut with ID [$id]")
                if (id.contains("#~#")) {
                    Log.w("$TAG Found legacy dynamic shortcut with ID [$id] detected, removing it")
                    dynamicShortcutsToRemove.add(id)
                }
            }
            if (dynamicShortcutsToRemove.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, dynamicShortcutsToRemove)
            }

            // Check for non-dynamic cached legacy shortcuts
            // Warning: on Android >= 10 dynamic shortcuts will still be returned!
            val flags = ShortcutManagerCompat.FLAG_MATCH_MANIFEST or ShortcutManagerCompat.FLAG_MATCH_PINNED or ShortcutManagerCompat.FLAG_MATCH_CACHED
            val cachedShortcutsToRemove = arrayListOf<String>()
            for (shortcut in ShortcutManagerCompat.getShortcuts(context, flags)) {
                val id = shortcut.id
                val dynamic = shortcut.isDynamic
                val cached = shortcut.isCached
                if (!dynamic && cached && id.contains("#~#")) {
                    Log.i("$TAG Found cached legacy shortcut with ID [$id], removing it")
                    cachedShortcutsToRemove.add(id)
                }
            }
            if (cachedShortcutsToRemove.isNotEmpty()) {
                ShortcutManagerCompat.removeLongLivedShortcuts(context, cachedShortcutsToRemove)
            }
        }

        @WorkerThread
        fun createDynamicShortcutToChatRoom(context: Context, chatRoom: ChatRoom) {
            val shortcut: ShortcutInfoCompat? = createChatRoomShortcut(context, chatRoom)
            if (shortcut != null) {
                Log.i("$TAG Created dynamic shortcut for ${shortcut.shortLabel}, pushing it")
                try {
                    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                } catch (e: Exception) {
                    Log.e("$TAG Failed to push dynamic shortcut for ${shortcut.shortLabel}: $e")
                }
            }
        }

        @WorkerThread
        private fun createChatRoomShortcut(context: Context, chatRoom: ChatRoom): ShortcutInfoCompat? {
            val peerAddress = chatRoom.peerAddress
            val id = LinphoneUtils.getConversationId(chatRoom)
            Log.i("$TAG Creating dynamic shortcut for chat room [$id]")

            try {
                val categories: ArraySet<String> = ArraySet()
                categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION)

                val personsList = arrayListOf<Person>()
                val subject: String
                var isGroup = false
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
                    isGroup = true
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

                val builder = ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(subject)
                    .setIcon(icon)
                    .setPersons(persons)
                    .setCategories(categories)
                    .setIntent(intent)
                    .setIsConversation()
                    .setLongLived(Version.sdkAboveOrEqual(Version.API30_ANDROID_11))
                    .setLocusId(LocusIdCompat(id))

                // See https://developer.android.com/training/sharing/direct-share-targets#track-shortcut-usage-comms-apps
                if (isGroup) {
                    builder.addCapabilityBinding("actions.intent.SEND_MESSAGE", "message.recipient.@type", listOf("Audience"))
                    builder.addCapabilityBinding("actions.intent.RECEIVE_MESSAGE", "message.sender.@type", listOf("Audience"))
                } else {
                    builder.addCapabilityBinding("actions.intent.SEND_MESSAGE")
                    builder.addCapabilityBinding("actions.intent.RECEIVE_MESSAGE")
                }

                return builder.build()
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
            Log.d("$TAG Dynamic shortcut for chat room with ID [$id] ${if (found != null) "exists" else "doesn't exists"}")
            return found != null
        }
    }
}

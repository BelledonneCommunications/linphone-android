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
package org.linphone.utils

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import androidx.collection.ArraySet
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.contact.Contact
import org.linphone.contact.NativeContact
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomCapabilities
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version

@TargetApi(25)
class ShortcutsHelper(val context: Context) {
    companion object {
        fun createShortcutsToContacts(context: Context) {
            val shortcuts = ArrayList<ShortcutInfo>()
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager.isRateLimitingActive) {
                Log.e("[Shortcut Helper] Rate limiting is active, aborting")
                return
            }

            val maxShortcuts = shortcutManager.maxShortcutCountPerActivity
            var count = 0
            val processedAddresses = arrayListOf<String>()
            for (room in coreContext.core.chatRooms) {
                // Android can usually only have around 4-5 shortcuts at a time
                if (count >= maxShortcuts) {
                    Log.w("[Shortcut Helper] Max amount of shortcuts reached ($count)")
                    break
                }

                val addresses: ArrayList<Address> = arrayListOf(room.peerAddress)
                if (!room.hasCapability(ChatRoomCapabilities.Basic.toInt())) {
                    addresses.clear()
                    for (participant in room.participants) {
                        addresses.add(participant.address)
                    }
                }
                for (address in addresses) {
                    if (count >= maxShortcuts) {
                        Log.w("[Shortcut Helper] Max amount of shortcuts reached ($count)")
                        break
                    }

                    val stringAddress = address.asStringUriOnly()
                    if (!processedAddresses.contains(stringAddress)) {
                        processedAddresses.add(stringAddress)
                        val contact: Contact? =
                            coreContext.contactsManager.findContactByAddress(address)

                        if (contact != null && contact is NativeContact) {
                            val shortcut: ShortcutInfo? = createContactShortcut(context, contact)
                            if (shortcut != null) {
                                Log.i("[Shortcut Helper] Creating launcher shortcut for ${shortcut.shortLabel}")
                                shortcuts.add(shortcut)
                                count += 1
                            }
                        } else {
                            Log.w("[Shortcut Helper] Contact not found for address: $stringAddress")
                        }
                    }
                }
            }
            shortcutManager.dynamicShortcuts = shortcuts
        }

        private fun createContactShortcut(context: Context, contact: NativeContact): ShortcutInfo? {
            try {
                val categories: ArraySet<String> = ArraySet()
                categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION)

                val person = contact.getPerson()
                val icon = person.icon

                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClass(context, MainActivity::class.java)
                intent.putExtra("ContactId", contact.nativeId)

                return ShortcutInfoCompat.Builder(context, contact.nativeId)
                    .setShortLabel(contact.fullName ?: "${contact.firstName} ${contact.lastName}")
                    .setIcon(icon)
                    .setPerson(person)
                    .setCategories(categories)
                    .setIntent(intent)
                    .build().toShortcutInfo()
            } catch (e: Exception) {
                Log.e("[Shortcuts Helper] createContactShortcut for contact [${contact.fullName}] exception: $e")
            }

            return null
        }

        fun createShortcutsToChatRooms(context: Context) {
            val shortcuts = ArrayList<ShortcutInfo>()
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager.isRateLimitingActive) {
                Log.e("[Shortcut Helper] Rate limiting is active, aborting")
                return
            }

            val maxShortcuts = shortcutManager.maxShortcutCountPerActivity
            var count = 0
            for (room in coreContext.core.chatRooms) {
                // Android can usually only have around 4-5 shortcuts at a time
                if (count >= maxShortcuts) {
                    Log.w("[Shortcut Helper] Max amount of shortcuts reached ($count)")
                    break
                }

                val shortcut: ShortcutInfo? = createChatRoomShortcut(context, room)
                if (shortcut != null) {
                    Log.i("[Shortcut Helper] Creating launcher shortcut for ${shortcut.shortLabel}")
                    shortcuts.add(shortcut)
                    count += 1
                }
            }
            shortcutManager.dynamicShortcuts = shortcuts
        }

        private fun createChatRoomShortcut(context: Context, chatRoom: ChatRoom): ShortcutInfo? {
            val localAddress = chatRoom.localAddress.asStringUriOnly()
            val peerAddress = chatRoom.peerAddress.asStringUriOnly()
            val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)

            try {
                val categories: ArraySet<String> = ArraySet()
                categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION)

                val personsList = arrayListOf<Person>()
                val subject: String
                val icon: IconCompat
                if (chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())) {
                    val contact =
                        coreContext.contactsManager.findContactByAddress(chatRoom.peerAddress)
                    if (contact != null) {
                        personsList.add(contact.getPerson())
                    }
                    subject = contact?.fullName ?: LinphoneUtils.getDisplayName(chatRoom.peerAddress)
                    icon = contact?.getPerson()?.icon ?: IconCompat.createWithResource(context, R.drawable.avatar)
                } else if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) && chatRoom.participants.isNotEmpty()) {
                    val address = chatRoom.participants.first().address
                    val contact =
                        coreContext.contactsManager.findContactByAddress(address)
                    if (contact != null) {
                        personsList.add(contact.getPerson())
                    }
                    subject = contact?.fullName ?: LinphoneUtils.getDisplayName(address)
                    icon = contact?.getPerson()?.icon ?: IconCompat.createWithResource(context, R.drawable.avatar)
                } else {
                    for (participant in chatRoom.participants) {
                        val contact =
                            coreContext.contactsManager.findContactByAddress(participant.address)
                        if (contact != null) {
                            personsList.add(contact.getPerson())
                        }
                    }
                    subject = chatRoom.subject.orEmpty()
                    icon = IconCompat.createWithResource(context, R.drawable.chat_group_avatar)
                }

                val persons = arrayOfNulls<Person>(personsList.size)
                personsList.toArray(persons)

                val args = Bundle()
                args.putString("RemoteSipUri", peerAddress)
                args.putString("LocalSipUri", localAddress)

                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClass(context, MainActivity::class.java)
                intent.putExtra("Chat", true)
                intent.putExtra("RemoteSipUri", peerAddress)
                intent.putExtra("LocalSipUri", localAddress)

                return ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(subject)
                    .setIcon(icon)
                    .setPersons(persons)
                    .setCategories(categories)
                    .setIntent(intent)
                    .setLongLived(Version.sdkAboveOrEqual(Version.API30_ANDROID_11))
                    .setLocusId(LocusIdCompat(id))
                    .build().toShortcutInfo()
            } catch (e: Exception) {
                Log.e("[Shortcuts Helper] createChatRoomShortcut for id [$id] exception: $e")
            }

            return null
        }

        fun removeShortcuts(context: Context) {
            Log.w("[Shortcut Helper] Removing all contacts shortcuts")
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            shortcutManager.removeAllDynamicShortcuts()
        }
    }
}

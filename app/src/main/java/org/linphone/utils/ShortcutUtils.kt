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
import kotlin.math.min
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AvatarGenerator
import org.linphone.contacts.getPerson
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.ui.main.MainActivity

class ShortcutUtils {
    companion object {
        private const val TAG = "[Shortcut Utils]"

        @WorkerThread
        suspend fun createShortcutsToChatRooms(context: Context) {
            val shortcuts = ArrayList<ShortcutInfoCompat>()
            if (ShortcutManagerCompat.isRateLimitingActive(context)) {
                Log.e("$TAG Rate limiting is active, aborting")
                return
            }
            Log.i("$TAG Creating launcher shortcuts for chat rooms")
            val maxShortcuts = min(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context), 5)
            var count = 0
            for (room in coreContext.core.chatRooms) {
                // Android can usually only have around 4-5 shortcuts at a time
                if (count >= maxShortcuts) {
                    Log.w("$TAG Max amount of shortcuts reached ($count)")
                    break
                }

                val shortcut: ShortcutInfoCompat? = createChatRoomShortcut(context, room)
                if (shortcut != null) {
                    Log.i("$TAG Created launcher shortcut for ${shortcut.shortLabel}")
                    shortcuts.add(shortcut)
                    count += 1
                }
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            Log.i("$TAG Created $count launcher shortcuts")
        }

        @WorkerThread
        private suspend fun createChatRoomShortcut(context: Context, chatRoom: ChatRoom): ShortcutInfoCompat? {
            val localAddress = chatRoom.localAddress
            val peerAddress = chatRoom.peerAddress
            val id = LinphoneUtils.getChatRoomId(localAddress, peerAddress)

            try {
                val categories: ArraySet<String> = ArraySet()
                categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION)

                val personsList = arrayListOf<Person>()
                val subject: String
                val icon: IconCompat
                if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
                    val contact =
                        coreContext.contactsManager.findContactByAddress(peerAddress)
                    val person = contact?.getPerson()
                    if (person != null) {
                        personsList.add(person)
                    }

                    subject = contact?.name ?: LinphoneUtils.getDisplayName(peerAddress)
                    icon = person?.icon ?: AvatarGenerator(context).setInitials(
                        AppUtils.getInitials(subject)
                    ).buildIcon()
                } else if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt()) && chatRoom.participants.isNotEmpty()) {
                    val address = chatRoom.participants.first().address
                    val contact =
                        coreContext.contactsManager.findContactByAddress(address)
                    val person = contact?.getPerson()
                    if (person != null) {
                        personsList.add(person)
                    }

                    subject = contact?.name ?: LinphoneUtils.getDisplayName(address)
                    icon = person?.icon ?: AvatarGenerator(context).setInitials(
                        AppUtils.getInitials(subject)
                    ).buildIcon()
                } else {
                    val list = arrayListOf<String>()
                    for (participant in chatRoom.participants) {
                        val contact =
                            coreContext.contactsManager.findContactByAddress(participant.address)
                        if (contact != null) {
                            personsList.add(contact.getPerson())

                            val picture = contact.photo
                            if (picture != null) {
                                list.add(picture)
                            }
                        }
                    }
                    subject = chatRoom.subject.orEmpty()
                    icon = if (list.isNotEmpty()) {
                        val iconSize = AppUtils.getDimension(R.dimen.avatar_list_cell_size).toInt()
                        IconCompat.createWithAdaptiveBitmap(
                            ImageUtils.getBitmapFromMultipleAvatars(context, iconSize, list)
                        )
                    } else {
                        AvatarGenerator(context).setInitials(subject).buildIcon()
                    }
                }

                val persons = arrayOfNulls<Person>(personsList.size)
                personsList.toArray(persons)

                val localSipUri = localAddress.asStringUriOnly()
                val peerSipUri = peerAddress.asStringUriOnly()

                val args = Bundle()
                args.putString("RemoteSipUri", peerSipUri)
                args.putString("LocalSipUri", localSipUri)

                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClass(context, MainActivity::class.java)
                intent.putExtra("Chat", true)
                intent.putExtra("RemoteSipUri", peerSipUri)
                intent.putExtra("LocalSipUri", localSipUri)

                return ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(subject)
                    .setIcon(icon)
                    .setPersons(persons)
                    .setCategories(categories)
                    .setIntent(intent)
                    .setLongLived(Version.sdkAboveOrEqual(Version.API30_ANDROID_11))
                    .setLocusId(LocusIdCompat(id))
                    .build()
            } catch (e: Exception) {
                Log.e("$TAG createChatRoomShortcut for id [$id] exception: $e")
            }

            return null
        }

        @WorkerThread
        fun isShortcutToChatRoomAlreadyCreated(context: Context, chatRoom: ChatRoom): Boolean {
            val id = LinphoneUtils.getChatRoomId(chatRoom)
            val found = ShortcutManagerCompat.getDynamicShortcuts(context).find {
                it.id == id
            }
            return found != null
        }
    }
}

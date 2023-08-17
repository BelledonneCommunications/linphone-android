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
package org.linphone.contacts

import androidx.loader.app.LoaderManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.utils.LinphoneUtils

class ContactsManager {
    val localFriends = arrayListOf<Friend>()

    private val listeners = arrayListOf<ContactsListener>()

    fun loadContacts(activity: MainActivity) {
        // UI thread
        val manager = LoaderManager.getInstance(activity)
        manager.restartLoader(0, null, ContactLoader())
    }

    fun addListener(listener: ContactsListener) {
        // UI thread
        if (coreContext.isReady()) {
            coreContext.postOnCoreThread {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: ContactsListener) {
        // UI thread
        if (coreContext.isReady()) {
            coreContext.postOnCoreThread {
                listeners.remove(listener)
            }
        }
    }

    fun onContactsLoaded() {
        // UI thread
        coreContext.postOnCoreThread {
            updateLocalContacts()

            for (listener in listeners) {
                listener.onContactsLoaded()
            }
        }
    }

    fun findContactById(id: String): Friend? {
        // Core thread
        return coreContext.core.defaultFriendList?.findFriendByRefKey(id)
    }

    fun updateLocalContacts() {
        // Core thread
        Log.i("[Contacts Manager] Updating local contact(s)")
        localFriends.clear()

        for (account in coreContext.core.accountList) {
            val friend = coreContext.core.createFriend()
            friend.name = LinphoneUtils.getDisplayName(account.params.identityAddress)

            val address = account.params.identityAddress ?: continue
            friend.address = address

            Log.i(
                "[Contacts Manager] Local contact created for account [${address.asString()}] and picture [${friend.photo}]"
            )
            localFriends.add(friend)
        }
    }

    fun onCoreStarted() {
        // Core thread
        updateLocalContacts()
    }

    fun onCoreStopped() {
        // Core thread
    }
}

interface ContactsListener {
    fun onContactsLoaded()
}

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
import org.linphone.ui.MainActivity

class ContactsManager {
    private val listeners = arrayListOf<ContactsListener>()

    fun loadContacts(activity: MainActivity) {
        val manager = LoaderManager.getInstance(activity)
        manager.restartLoader(0, null, ContactLoader())
    }

    fun addListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            coreContext.postOnCoreThread {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            coreContext.postOnCoreThread {
                listeners.remove(listener)
            }
        }
    }

    fun onContactsLoaded() {
        coreContext.postOnCoreThread {
            for (listener in listeners) {
                listener.onContactsLoaded()
            }
        }
    }

    fun findContactById(id: String): Friend? {
        return coreContext.core.defaultFriendList?.findFriendByRefKey(id)
    }

    fun onCoreStarted() {
    }

    fun onCoreStopped() {
    }
}

interface ContactsListener {
    fun onContactsLoaded()
}

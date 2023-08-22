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

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.loader.app.LoaderManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity

class ContactsManager {
    companion object {
        const val TAG = "[Contacts Manager]"
    }

    private val listeners = arrayListOf<ContactsListener>()

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(list: FriendList, friends: Array<Friend>) {
            Log.i("$TAG Presence received")
            for (listener in listeners) {
                listener.onContactsLoaded()
            }
        }
    }

    private val coreListener: CoreListenerStub = object : CoreListenerStub() {
        @WorkerThread
        override fun onFriendListCreated(core: Core, friendList: FriendList) {
            friendList.addListener(friendListListener)
        }

        @WorkerThread
        override fun onFriendListRemoved(core: Core, friendList: FriendList) {
            friendList.removeListener(friendListListener)
        }
    }

    @UiThread
    fun loadContacts(activity: MainActivity) {
        val manager = LoaderManager.getInstance(activity)
        manager.restartLoader(0, null, ContactLoader())
    }

    @WorkerThread
    fun addListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            listeners.add(listener)
        }
    }

    @WorkerThread
    fun removeListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            listeners.remove(listener)
        }
    }

    @UiThread
    fun onContactsLoaded() {
        coreContext.postOnCoreThread {
            notifyContactsListChanged()
        }
    }

    @WorkerThread
    fun notifyContactsListChanged() {
        for (listener in listeners) {
            listener.onContactsLoaded()
        }
    }

    @WorkerThread
    fun findContactById(id: String): Friend? {
        return coreContext.core.defaultFriendList?.findFriendByRefKey(id)
    }

    @WorkerThread
    fun onCoreStarted() {
        val core = coreContext.core
        core.addListener(coreListener)
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
    }

    @WorkerThread
    fun onCoreStopped() {
        val core = coreContext.core
        core.removeListener(coreListener)
        for (list in core.friendsLists) {
            list.removeListener(friendListListener)
        }
    }
}

interface ContactsListener {
    fun onContactsLoaded()
}

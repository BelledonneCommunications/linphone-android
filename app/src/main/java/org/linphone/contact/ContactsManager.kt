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
package org.linphone.contact

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.AsyncTask.THREAD_POOL_EXECUTOR
import android.provider.ContactsContract
import android.util.Patterns
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.PermissionHelper

interface ContactsUpdatedListener {
    fun onContactsUpdated()

    fun onContactUpdated(contact: Contact)
}

open class ContactsUpdatedListenerStub : ContactsUpdatedListener {
    override fun onContactsUpdated() {}

    override fun onContactUpdated(contact: Contact) {}
}

class ContactsManager(private val context: Context) {
    private val contactsObserver: ContentObserver by lazy {
        object : ContentObserver(coreContext.handler) {
            @Synchronized
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            @Synchronized
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.i("[Contacts Observer] At least one contact has changed")
                fetchContactsAsync()
            }
        }
    }

    var contacts = ArrayList<Contact>()
        @Synchronized
        get
        @Synchronized
        private set
    var sipContacts = ArrayList<Contact>()
        @Synchronized
        get
        @Synchronized
        private set

    val magicSearch: MagicSearch by lazy {
        val magicSearch = coreContext.core.createMagicSearch()
        magicSearch.limitedSearch = false
        magicSearch
    }

    private val contactsUpdatedListeners = ArrayList<ContactsUpdatedListener>()

    private var loadContactsTask: AsyncContactsLoader? = null

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @Synchronized
        override fun onPresenceReceived(list: FriendList, friends: Array<Friend>) {
            Log.i("[Contacts Manager] Presence received")
            var sipContactsListUpdated = false
            for (friend in friends) {
                if (refreshContactOnPresenceReceived(friend)) {
                    sipContactsListUpdated = true
                }
            }

            if (sipContactsListUpdated) {
                sipContacts.sort()
                Log.i("[Contacts Manager] Notifying observers that list has changed")
                for (listener in contactsUpdatedListeners) {
                    listener.onContactsUpdated()
                }
            }
        }
    }

    init {
        if (PermissionHelper.required(context).hasReadContactsPermission())
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)

        initSyncAccount()

        val core = coreContext.core
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
        Log.i("[Contacts Manager] Created")
    }

    @Synchronized
    fun fetchContactsAsync() {
        if (loadContactsTask != null) {
            Log.w("[Contacts Manager] Cancelling existing async task")
            loadContactsTask?.cancel(true)
        }
        loadContactsTask = AsyncContactsLoader(context)
        loadContactsTask?.executeOnExecutor(THREAD_POOL_EXECUTOR)
    }

    @Synchronized
    fun addContact(contact: Contact) {
        contacts.add(contact)
    }

    @Synchronized
    fun updateContacts(all: ArrayList<Contact>, sip: ArrayList<Contact>) {
        contacts.clear()
        sipContacts.clear()

        contacts.addAll(all)
        sipContacts.addAll(sip)

        Log.i("[Contacts Manager] Async fetching finished, notifying observers")
        for (listener in contactsUpdatedListeners) {
            listener.onContactsUpdated()
        }
    }

    @Synchronized
    fun getAndroidContactIdFromUri(uri: Uri): String? {
        val projection = arrayOf(ContactsContract.Data.CONTACT_ID)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        if (cursor?.moveToFirst() == true) {
            val nameColumnIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val id = cursor.getString(nameColumnIndex)
            cursor.close()
            return id
        }
        return null
    }

    @Synchronized
    fun findContactById(id: String): Contact? {
        var found: Contact? = null
        if (contacts.isNotEmpty()) {
            found = contacts.find { contact ->
                contact is NativeContact && contact.nativeId == id
            }
        }

        if (found == null && PermissionHelper.required(context).hasReadContactsPermission()) {
            // First of all abort background contacts fetching
            loadContactsTask?.cancel(true)

            Log.i("[Contacts Manager] Creating native contact with id $id and fetch information from Android database directly")
            found = NativeContact(id)
            found.syncValuesFromAndroidContact(context)
            // Create a LinphoneFriend to be able to receive presence information
            found.createOrUpdateFriendFromNativeContact()

            // Restart contacts async fetching
            fetchContactsAsync()
        }
        return found
    }

    @Synchronized
    fun findContactByPhoneNumber(number: String): Contact? {
        return contacts.find { contact ->
            contact.phoneNumbers.contains(number)
        }
    }

    @Synchronized
    fun findContactByAddress(address: Address): Contact? {
        val friend: Friend? = coreContext.core.findFriend(address)
        val contact: Contact? = friend?.userData as? Contact
        if (contact != null) return contact

        val username = address.username
        if (username != null && Patterns.PHONE.matcher(username).matches()) {
            return findContactByPhoneNumber(username)
        }

        return null
    }

    fun addListener(listener: ContactsUpdatedListener) {
        contactsUpdatedListeners.add(listener)
    }

    fun removeListener(listener: ContactsUpdatedListener) {
        contactsUpdatedListeners.remove(listener)
    }

    @Synchronized
    fun destroy() {
        context.contentResolver.unregisterContentObserver(contactsObserver)
        loadContactsTask?.cancel(true)

        // Contact has a Friend field and Friend can have a Contact has userData
        // Friend also keeps a ref on the Core, so we have to clean them
        for (contact in contacts) {
            contact.friend = null
        }
        contacts.clear()
        for (contact in sipContacts) {
            contact.friend = null
        }
        sipContacts.clear()

        val core = coreContext.core
        for (list in core.friendsLists) list.removeListener(friendListListener)
    }

    private fun initSyncAccount() {
        if (!corePreferences.useLinphoneSyncAccount) {
            Log.w("[Contacts Manager] Linphone sync account disabled, skipping initialization")
            return
        }

        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val accounts = accountManager.getAccountsByType(context.getString(R.string.sync_account_type))
        if (accounts.isEmpty()) {
            val newAccount = Account(context.getString(R.string.sync_account_name), context.getString(R.string.sync_account_type))
            try {
                accountManager.addAccountExplicitly(newAccount, null, null)
                Log.i("[Contacts Manager] Contact account added")
            } catch (e: Exception) {
                Log.e("[Contacts Manager] Couldn't initialize sync account: $e")
            }
        } else {
            for (account in accounts) {
                Log.i("[Contacts Manager] Found account with name [${account.name}] and type [${account.type}]")
            }
        }
    }

    @Synchronized
    private fun refreshContactOnPresenceReceived(friend: Friend): Boolean {
        if (friend.userData == null) return false

        val contact: Contact = friend.userData as Contact
        Log.i("[Contacts Manager] Received presence information for contact $contact")
        for (listener in contactsUpdatedListeners) {
            listener.onContactUpdated(contact)
        }

        if (corePreferences.storePresenceInNativeContact) {
            if (contact is NativeContact) {
                for (phoneNumber in contact.phoneNumbers) {
                    val sipAddress = contact.getContactForPhoneNumberOrAddress(phoneNumber)
                    if (sipAddress != null) {
                        Log.i("[Contacts Manager] Found presence information to store in native contact $contact")
                        NativeContactEditor(contact).setPresenceInformation(phoneNumber, sipAddress).commit()
                    }
                }
            }
        }

        if (!sipContacts.contains(contact)) {
            sipContacts.add(contact)
            return true
        }

        return false
    }
}

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
import android.accounts.AuthenticatorDescription
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.AsyncTask.THREAD_POOL_EXECUTOR
import android.provider.ContactsContract
import android.util.Patterns
import java.io.File
import kotlinx.coroutines.*
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

    var latestContactFetch: String = ""

    private var localAccountsContacts = ArrayList<Contact>()
        @Synchronized
        get
        @Synchronized
        private set

    private val friendsMap: HashMap<String, Friend> = HashMap()

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
                notifyListeners()
            }
        }
    }

    init {
        if (PermissionHelper.required(context).hasReadContactsPermission()) {
            onReadContactsPermissionGranted()
        }

        initSyncAccount()

        val core = coreContext.core
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
        Log.i("[Contacts Manager] Created")
    }

    fun onReadContactsPermissionGranted() {
        Log.i("[Contacts Manager] Register contacts observer")
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            contactsObserver
        )
    }

    fun shouldDisplaySipContactsList(): Boolean {
        return coreContext.core.defaultAccount?.params?.identityAddress?.domain == corePreferences.defaultDomain
    }

    @Synchronized
    fun fetchContactsAsync() {
        latestContactFetch = System.currentTimeMillis().toString()

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
    fun updateLocalContacts() {
        localAccountsContacts.clear()

        for (account in coreContext.core.accountList) {
            val localContact = Contact()
            localContact.fullName = account.params.identityAddress?.displayName ?: account.params.identityAddress?.username
            val pictureUri = corePreferences.defaultAccountAvatarPath
            if (pictureUri != null) {
                localContact.setContactThumbnailPictureUri(Uri.fromFile(File(pictureUri)))
            }
            val address = account.params.identityAddress
            if (address != null) {
                localContact.sipAddresses.add(address)
                localContact.rawSipAddresses.add(address.asStringUriOnly())
            }
            localAccountsContacts.add(localContact)
        }
    }

    @Synchronized
    fun updateContacts(all: ArrayList<Contact>, sip: ArrayList<Contact>) {
        contacts.clear()
        sipContacts.clear()

        contacts.addAll(all)
        sipContacts.addAll(sip)

        updateLocalContacts()

        Log.i("[Contacts Manager] Async fetching finished, notifying observers")
        notifyListeners()
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

        if (found == null) {
            Log.i("[Contacts Manager] Contact with id $id not found yet")
        } else {
            Log.d("[Contacts Manager] Found contact with id [$id]: ${found.fullName}")
        }

        return found
    }

    @Synchronized
    fun findContactByPhoneNumber(number: String): Contact? {
        val cacheFriend = friendsMap[number]
        val friend: Friend? = cacheFriend ?: coreContext.core.findFriendByPhoneNumber(number)
        if (cacheFriend == null && friend != null) friendsMap[number] = friend
        return friend?.userData as? Contact
    }

    @Synchronized
    fun findContactByAddress(address: Address): Contact? {
        val localContact = localAccountsContacts.find { localContact ->
            localContact.sipAddresses.find { localAddress ->
                address.weakEqual(localAddress)
            } != null
        }
        if (localContact != null) return localContact

        val cleanAddress = address.clone()
        cleanAddress.clean() // To remove gruu if any
        val cleanStringAddress = cleanAddress.asStringUriOnly()

        val cacheFriend = friendsMap[cleanStringAddress]
        val friend: Friend? = coreContext.core.findFriend(address)
        val contact: Contact? = friend?.userData as? Contact
        if (cacheFriend == null && friend != null) friendsMap[cleanStringAddress] = friend
        if (contact != null) return contact

        val username = address.username
        if (username != null && Patterns.PHONE.matcher(username).matches()) {
            return findContactByPhoneNumber(username)
        }

        return null
    }

    @Synchronized
    fun addListener(listener: ContactsUpdatedListener) {
        contactsUpdatedListeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: ContactsUpdatedListener) {
        contactsUpdatedListeners.remove(listener)
    }

    @Synchronized
    fun notifyListeners() {
        val list = contactsUpdatedListeners.toMutableList()
        for (listener in list) {
            listener.onContactsUpdated()
        }
    }

    @Synchronized
    fun notifyListeners(contact: Contact) {
        val list = contactsUpdatedListeners.toMutableList()
        for (listener in list) {
            listener.onContactUpdated(contact)
        }
    }

    @Synchronized
    fun destroy() {
        context.contentResolver.unregisterContentObserver(contactsObserver)
        loadContactsTask?.cancel(true)

        friendsMap.clear()
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
        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val accounts = accountManager.getAccountsByType(context.getString(R.string.sync_account_type))
        if (accounts.isEmpty()) {
            val newAccount = Account(
                context.getString(R.string.sync_account_name),
                context.getString(
                    R.string.sync_account_type
                )
            )
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

    fun getAvailableSyncAccounts(): List<Triple<String, String, Drawable?>> {
        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val packageManager = context.packageManager
        val syncAdapters = ContentResolver.getSyncAdapterTypes()
        val authenticators: Array<AuthenticatorDescription> = accountManager.authenticatorTypes
        val available = arrayListOf<Triple<String, String, Drawable?>>()

        for (syncAdapter in syncAdapters) {
            if (syncAdapter.authority == "com.android.contacts" && syncAdapter.isUserVisible) {
                if (syncAdapter.supportsUploading() || syncAdapter.accountType == context.getString(R.string.sync_account_type)) {
                    Log.i("[Contacts Manager] Found sync adapter for com.android.contacts authority: ${syncAdapter.accountType}")
                    val accounts = accountManager.getAccountsByType(syncAdapter.accountType)
                    for (account in accounts) {
                        Log.i("[Contacts Manager] Found account for account type ${syncAdapter.accountType}: ${account.name}")
                        for (authenticator in authenticators) {
                            if (authenticator.type == account.type) {
                                val drawable = packageManager.getDrawable(authenticator.packageName, authenticator.smallIconId, null)
                                val triple = Triple(account.name, account.type, drawable)
                                available.add(triple)
                            }
                        }
                    }
                }
            }
        }

        return available
    }

    @Synchronized
    private fun refreshContactOnPresenceReceived(friend: Friend): Boolean {
        if (friend.userData == null) return false

        val contact: Contact = friend.userData as Contact
        Log.d("[Contacts Manager] Received presence information for contact $contact")
        if (corePreferences.storePresenceInNativeContact && PermissionHelper.get().hasWriteContactsPermission()) {
            if (contact is NativeContact) {
                storePresenceInNativeContact(contact)
            }
        }
        if (loadContactsTask?.status == AsyncTask.Status.RUNNING) {
            Log.w("[Contacts Manager] Async contacts loader running, skip onContactUpdated listener notify")
        } else {
            notifyListeners(contact)
        }

        if (!sipContacts.contains(contact)) {
            sipContacts.add(contact)
            return true
        }

        return false
    }

    @Synchronized
    fun storePresenceInformationForAllContacts() {
        if (corePreferences.storePresenceInNativeContact && PermissionHelper.get().hasWriteContactsPermission()) {
            for (list in coreContext.core.friendsLists) {
                for (friend in list.friends) {
                    if (friend.userData == null) continue
                    val contact: Contact = friend.userData as Contact
                    if (contact is NativeContact) {
                        storePresenceInNativeContact(contact)
                        if (loadContactsTask?.status == AsyncTask.Status.RUNNING) {
                            Log.w("[Contacts Manager] Async contacts loader running, skip onContactUpdated listener notify")
                        } else {
                            notifyListeners(contact)
                        }
                    }
                }
            }
        }
    }

    private fun storePresenceInNativeContact(contact: NativeContact) {
        for (phoneNumber in contact.rawPhoneNumbers) {
            val sipAddress = contact.getContactForPhoneNumberOrAddress(phoneNumber)
            if (sipAddress != null) {
                Log.d("[Contacts Manager] Found presence information to store in native contact $contact under Linphone sync account")
                val contactEditor = NativeContactEditor(contact)
                val coroutineScope = CoroutineScope(Dispatchers.Main)
                coroutineScope.launch {
                    val deferred = async {
                        withContext(Dispatchers.IO) {
                            contactEditor.setPresenceInformation(
                                phoneNumber,
                                sipAddress
                            ).commit()
                        }
                    }
                    deferred.await()
                }
            }
        }
    }
}

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
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.util.Patterns
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.MutableLiveData
import java.io.IOException
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.ImageUtils
import org.linphone.utils.PermissionHelper

interface ContactsUpdatedListener {
    fun onContactsUpdated()

    fun onContactUpdated(friend: Friend)
}

open class ContactsUpdatedListenerStub : ContactsUpdatedListener {
    override fun onContactsUpdated() {}

    override fun onContactUpdated(friend: Friend) {}
}

class ContactsManager(private val context: Context) {
    val magicSearch: MagicSearch by lazy {
        val magicSearch = coreContext.core.createMagicSearch()
        magicSearch.limitedSearch = false
        magicSearch
    }

    var latestContactFetch: String = ""

    val fetchInProgress = MutableLiveData<Boolean>()

    var contactIdToWatchFor: String = ""

    val contactAvatar: IconCompat
    val groupAvatar: IconCompat

    private val localFriends = arrayListOf<Friend>()

    private val contactsUpdatedListeners = ArrayList<ContactsUpdatedListener>()

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @Synchronized
        override fun onPresenceReceived(list: FriendList, friends: Array<Friend>) {
            Log.i("[Contacts Manager] Presence received")
            for (friend in friends) {
                refreshContactOnPresenceReceived(friend)
            }
            Log.i("[Contacts Manager] Contacts refreshed due to presence received")
            notifyListeners()
            Log.i("[Contacts Manager] Presence notified to all listeners")
        }
    }

    init {
        initSyncAccount()

        contactAvatar = IconCompat.createWithResource(context, R.drawable.voip_single_contact_avatar_alt)
        groupAvatar = IconCompat.createWithResource(context, R.drawable.voip_multiple_contacts_avatar_alt)

        val core = coreContext.core
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
        Log.i("[Contacts Manager] Created")
    }

    fun shouldDisplaySipContactsList(): Boolean {
        return coreContext.core.defaultAccount?.params?.identityAddress?.domain == corePreferences.defaultDomain
    }

    fun fetchFinished() {
        Log.i("[Contacts Manager] Contacts loader have finished")
        latestContactFetch = System.currentTimeMillis().toString()
        updateLocalContacts()
        fetchInProgress.value = false
        notifyListeners()
    }

    @Synchronized
    fun updateLocalContacts() {
        Log.i("[Contacts Manager] Updating local contact(s)")
        localFriends.clear()

        for (account in coreContext.core.accountList) {
            val friend = coreContext.core.createFriend()
            friend.name = account.params.identityAddress?.displayName ?: account.params.identityAddress?.username

            val address = account.params.identityAddress ?: continue
            friend.address = address

            val pictureUri = corePreferences.defaultAccountAvatarPath
            if (pictureUri != null) {
                val parsedUri = if (pictureUri.startsWith("/")) "file:$pictureUri" else pictureUri
                Log.i("[Contacts Manager] Found local picture URI: $parsedUri")
                friend.photo = parsedUri
            }

            Log.i("[Contacts Manager] Local contact created for account [${address.asString()}] and picture [${friend.photo}]")
            localFriends.add(friend)
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
    fun findContactById(id: String): Friend? {
        return coreContext.core.defaultFriendList?.findFriendByRefKey(id)
    }

    @Synchronized
    fun findContactByPhoneNumber(number: String): Friend? {
        return coreContext.core.findFriendByPhoneNumber(number)
    }

    @Synchronized
    fun findContactByAddress(address: Address): Friend? {
        for (friend in localFriends) {
            if (friend.address?.weakEqual(address) == true) {
                return friend
            }
        }

        val friend = coreContext.core.findFriend(address)
        if (friend != null) return friend

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
    fun notifyListeners(friend: Friend) {
        val list = contactsUpdatedListeners.toMutableList()
        for (listener in list) {
            listener.onContactUpdated(friend)
        }
    }

    @Synchronized
    fun destroy() {
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
    private fun refreshContactOnPresenceReceived(friend: Friend) {
        Log.d("[Contacts Manager] Received presence information for contact $friend")
        if (corePreferences.storePresenceInNativeContact && PermissionHelper.get().hasWriteContactsPermission()) {
            if (friend.refKey != null) {
                Log.i("[Contacts Manager] Storing presence in native contact ${friend.refKey}")
                storePresenceInNativeContact(friend)
            }
        }
        notifyListeners(friend)
    }

    private fun storePresenceInNativeContact(friend: Friend) {
        val contactEditor = NativeContactEditor(friend)
        for (phoneNumber in friend.phoneNumbers) {
            val sipAddress = friend.getContactForPhoneNumberOrAddress(phoneNumber)
            if (sipAddress != null) {
                Log.d("[Contacts Manager] Found presence information to store in native contact $friend under Linphone sync account")
                contactEditor.setPresenceInformation(
                    phoneNumber,
                    sipAddress
                )
            }
        }
        contactEditor.commit()
    }

    fun createFriendFromSearchResult(searchResult: SearchResult): Friend {
        val searchResultFriend = searchResult.friend
        if (searchResultFriend != null) return searchResultFriend

        val friend = coreContext.core.createFriend()

        val address = searchResult.address
        if (address != null) {
            friend.address = address
        }

        val number = searchResult.phoneNumber
        if (number != null) {
            friend.addPhoneNumber(number)

            if (address != null && address.username == number) {
                friend.removeAddress(address)
            }
        }

        return friend
    }
}

fun Friend.getContactForPhoneNumberOrAddress(value: String): String? {
    val presenceModel = getPresenceModelForUriOrTel(value)
    if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return presenceModel.contact
    return null
}

fun Friend.hasPresence(): Boolean {
    for (address in addresses) {
        val presenceModel = getPresenceModelForUriOrTel(address.asStringUriOnly())
        if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return true
    }
    for (number in phoneNumbers) {
        val presenceModel = getPresenceModelForUriOrTel(number)
        if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return true
    }
    return false
}

fun Friend.getThumbnailUri(): Uri? {
    return getPictureUri(true)
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

fun Friend.getPerson(): Person {
    val personBuilder = Person.Builder().setName(name)

    val bm: Bitmap? =
        ImageUtils.getRoundBitmapFromUri(
            coreContext.context,
            getThumbnailUri()
        )
    val icon =
        if (bm == null) {
            coreContext.contactsManager.contactAvatar
        } else IconCompat.createWithAdaptiveBitmap(bm)
    if (icon != null) {
        personBuilder.setIcon(icon)
    }

    personBuilder.setUri(nativeUri)
    personBuilder.setImportant(starred)
    return personBuilder.build()
}

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

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.isDigitsOnly
import androidx.loader.app.LoaderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SecurityLevel
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.model.isEndToEndEncryptionMandatory
import org.linphone.utils.AppUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PhoneNumberUtils
import org.linphone.utils.ShortcutUtils

class ContactsManager
    @UiThread
    constructor() {
    companion object {
        private const val TAG = "[Contacts Manager]"

        private const val DELAY_BEFORE_RELOADING_CONTACTS_AFTER_PRESENCE_RECEIVED = 1000L // 1 second
        private const val DELAY_BEFORE_RELOADING_CONTACTS_AFTER_MAGIC_SEARCH_RESULT = 1000L // 1 second
        private const val FRIEND_LIST_TEMPORARY_STORED_REMOTE_DIRECTORY = "TempRemoteDirectoryContacts"
    }

    private var nativeContactsLoaded = false

    private val listeners = arrayListOf<ContactsListener>()

    private val knownContactsAvatarsMap = hashMapOf<String, ContactAvatarModel>()
    private val unknownContactsAvatarsMap = hashMapOf<String, ContactAvatarModel>()
    private val conferenceAvatarMap = hashMapOf<String, ContactAvatarModel>()
    private val magicSearchMap = hashMapOf<String, MagicSearch>()

    private val unknownRemoteContactDirectoriesContactsMap = arrayListOf<String>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reloadPresenceContactsJob: Job? = null
    private var reloadRemoteContactsJob: Job? = null

    private var loadContactsOnlyFromDefaultDirectory = true

    private val magicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            reloadRemoteContactsJob?.cancel()

            val results = magicSearch.lastSearch
            Log.i("$TAG [${results.size}] magic search results available")

            var found = false
            if (results.isNotEmpty()) {
                val result = results.first {
                    it.friend != null
                }
                if (result != null) {
                    val friend = result.friend!!
                    Log.i("$TAG Found matching friend in source [${result.sourceFlags}]")
                    found = true

                    // Store friend in app's cache to be re-used in call history, conversations, etc...
                    val temporaryFriendList = getRemoteContactDirectoriesCacheFriendList()
                    temporaryFriendList.addFriend(friend)
                    newContactAdded(friend)
                    Log.i(
                        "$TAG Stored discovered friend [${friend.name}] in temporary friend list, for later use"
                    )

                    for (listener in listeners) {
                        listener.onContactFoundInRemoteDirectory(friend)
                    }

                    reloadRemoteContactsJob = coroutineScope.launch {
                        delay(DELAY_BEFORE_RELOADING_CONTACTS_AFTER_MAGIC_SEARCH_RESULT)
                        coreContext.postOnCoreThread {
                            Log.i("$TAG At least a new SIP address was discovered, reloading contacts")
                            conferenceAvatarMap.values.forEach(ContactAvatarModel::destroy)
                            conferenceAvatarMap.clear()

                            notifyContactsListChanged()
                        }
                    }
                }
            }

            var foundKey = ""
            for ((key, value) in magicSearchMap.entries) {
                if (value == magicSearch) {
                    foundKey = key
                }
            }
            if (foundKey.isNotEmpty()) {
                magicSearchMap.remove(foundKey)
                if (!found) {
                    Log.i(
                        "$TAG SIP URI [$foundKey] wasn't found in remote directories, adding it to unknown list to prevent further queries"
                    )
                    unknownRemoteContactDirectoriesContactsMap.add(foundKey)
                }
            }
            magicSearch.removeListener(this)
        }
    }

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(friendList: FriendList, friends: Array<out Friend?>) {
            if (friendList.isSubscriptionBodyless) {
                Log.i("$TAG Bodyless friendlist [${friendList.displayName}] presence received")
                notifyContactsListChanged()
            }
        }

        @WorkerThread
        override fun onNewSipAddressDiscovered(
            friendList: FriendList,
            friend: Friend,
            sipUri: String
        ) {
            reloadPresenceContactsJob?.cancel()
            Log.d(
                "$TAG Newly discovered SIP Address [$sipUri] for friend [${friend.name}] in list [${friendList.displayName}]"
            )
            val address = Factory.instance().createAddress(sipUri)
            if (address != null) {
                Log.i("$TAG Storing discovered SIP URI inside Friend")
                friend.edit()
                friend.addAddress(address)
                friend.done()

                newContactAddedWithSipUri(friend, sipUri)
            } else {
                Log.e("$TAG Failed to parse SIP URI [$sipUri] as Address!")
            }

            reloadPresenceContactsJob = coroutineScope.launch {
                delay(DELAY_BEFORE_RELOADING_CONTACTS_AFTER_PRESENCE_RECEIVED)
                coreContext.postOnCoreThread {
                    Log.i("$TAG At least a new SIP address was discovered, reloading contacts")
                    conferenceAvatarMap.values.forEach(ContactAvatarModel::destroy)
                    conferenceAvatarMap.clear()

                    notifyContactsListChanged()
                }
            }
        }

        @WorkerThread
        override fun onContactCreated(friendList: FriendList, linphoneFriend: Friend) {
            for (address in linphoneFriend.addresses) {
                removeUnknownAddressFromMap(address)
            }
        }

        @WorkerThread
        override fun onContactDeleted(friendList: FriendList, linphoneFriend: Friend) {
            for (address in linphoneFriend.addresses) {
                removeKnownAddressFromMap(address)
            }
        }

        @WorkerThread
        override fun onContactUpdated(
            friendList: FriendList,
            newFriend: Friend,
            oldFriend: Friend
        ) {
            for (address in oldFriend.addresses) {
                removeKnownAddressFromMap(address)
            }
            for (address in newFriend.addresses) {
                removeUnknownAddressFromMap(address)
            }
        }

        @WorkerThread
        override fun onSyncStatusChanged(
            friendList: FriendList,
            status: FriendList.SyncStatus?,
            message: String?
        ) {
            Log.i("$TAG Friend list [${friendList.displayName}] sync status changed to [$status]")
            when (status) {
                FriendList.SyncStatus.Successful -> {
                    notifyContactsListChanged()
                }
                FriendList.SyncStatus.Failure -> {
                    Log.e("$TAG Friend list [${friendList.displayName}] sync failed: $message")
                }
                else -> {}
            }
        }
    }

    private val coreListener: CoreListenerStub = object : CoreListenerStub() {
        @WorkerThread
        override fun onFriendListCreated(core: Core, friendList: FriendList) {
            Log.i("$TAG Friend list [${friendList.displayName}] created")
            friendList.addListener(friendListListener)
        }

        @WorkerThread
        override fun onFriendListRemoved(core: Core, friendList: FriendList) {
            Log.i("$TAG Friend list [${friendList.displayName}] removed")
            friendList.removeListener(friendListListener)
        }

        @WorkerThread
        override fun onDefaultAccountChanged(core: Core, account: Account?) {
            Log.i("$TAG Default account changed, update all contacts' model showTrust value")
            updateContactsModelDependingOnDefaultAccountMode()
        }
    }

    @MainThread
    fun loadContacts(activity: MainActivity) {
        Log.i("$TAG Starting contacts loader")
        val manager = LoaderManager.getInstance(activity)
        val args = Bundle()
        args.putBoolean("defaultDirectory", loadContactsOnlyFromDefaultDirectory)
        manager.restartLoader(0, args, ContactLoader())
    }

    @WorkerThread
    fun addListener(listener: ContactsListener) {
        // Post again to prevent ConcurrentModificationException
        coreContext.postOnCoreThread {
            try {
                listeners.add(listener)
            } catch (cme: ConcurrentModificationException) {
                Log.e("$TAG Can't add listener: $cme")
            }
        }
    }

    @WorkerThread
    fun removeListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            // Post again to prevent ConcurrentModificationException
            coreContext.postOnCoreThread {
                try {
                    listeners.remove(listener)
                } catch (cme: ConcurrentModificationException) {
                    Log.e("$TAG Can't remove listener: $cme")
                }
            }
        }
    }

    @WorkerThread
    fun removeKnownAddressFromMap(address: Address) {
        val key = address.asStringUriOnly()
        val wasKnown = knownContactsAvatarsMap.remove(key)
        if (wasKnown != null) {
            Log.d("$TAG Removed address [$key] from knownContactsAvatarsMap")
        }
    }

    @WorkerThread
    fun removeUnknownAddressFromMap(address: Address) {
        val key = address.asStringUriOnly()
        val wasUnknown = unknownContactsAvatarsMap.remove(key)
        if (wasUnknown != null) {
            Log.d("$TAG Removed address [$key] from unknownContactsAvatarsMap")
        }
    }

    @WorkerThread
    private fun newContactAddedWithSipUri(friend: Friend, sipUri: String) {
        if (unknownContactsAvatarsMap.keys.contains(sipUri)) {
            Log.d("$TAG Found SIP URI [$sipUri] in unknownContactsAvatarsMap, removing it")
            val oldModel = unknownContactsAvatarsMap[sipUri]
            oldModel?.destroy()
            unknownContactsAvatarsMap.remove(sipUri)
        } else if (knownContactsAvatarsMap.keys.contains(sipUri)) {
            Log.d(
                "$TAG Found SIP URI [$sipUri] in knownContactsAvatarsMap, forcing presence update"
            )
            val oldModel = knownContactsAvatarsMap[sipUri]
            val address = Factory.instance().createAddress(sipUri)
            oldModel?.update(address)
        } else {
            Log.i(
                "$TAG New contact added with SIP URI [$sipUri] but no avatar yet, let's create it"
            )
            val model = ContactAvatarModel(friend)
            knownContactsAvatarsMap[sipUri] = model
        }
    }

    @WorkerThread
    fun newContactAdded(friend: Friend) {
        for (sipAddress in friend.addresses) {
            newContactAddedWithSipUri(friend, sipAddress.asStringUriOnly())
        }
    }

    @WorkerThread
    fun contactRemoved(friend: Friend) {
        val refKey = friend.refKey.orEmpty()
        if (refKey.isNotEmpty() && knownContactsAvatarsMap.keys.contains(refKey)) {
            Log.d("$TAG Found RefKey [$refKey] in knownContactsAvatarsMap, removing it")
            val oldModel = knownContactsAvatarsMap[refKey]
            oldModel?.destroy()
            knownContactsAvatarsMap.remove(refKey)
        }

        for (sipAddress in friend.addresses) {
            val sipUri = sipAddress.asStringUriOnly()
            if (knownContactsAvatarsMap.keys.contains(sipUri)) {
                Log.d("$TAG Found SIP URI [$sipUri] in knownContactsAvatarsMap, removing it")
                val oldModel = knownContactsAvatarsMap[sipUri]
                oldModel?.destroy()
                knownContactsAvatarsMap.remove(sipUri)
            }
        }

        conferenceAvatarMap.values.forEach(ContactAvatarModel::destroy)
        conferenceAvatarMap.clear()
        notifyContactsListChanged()
    }

    @WorkerThread
    fun onNativeContactsLoaded() {
        nativeContactsLoaded = true
        Log.i("$TAG Native contacts have been loaded, cleaning avatars maps")

        knownContactsAvatarsMap.values.forEach(ContactAvatarModel::destroy)
        knownContactsAvatarsMap.clear()
        unknownContactsAvatarsMap.values.forEach(ContactAvatarModel::destroy)
        unknownContactsAvatarsMap.clear()
        conferenceAvatarMap.values.forEach(ContactAvatarModel::destroy)
        conferenceAvatarMap.clear()
        unknownRemoteContactDirectoriesContactsMap.clear()

        notifyContactsListChanged()

        Log.i("$TAG Native contacts have been loaded, creating chat rooms shortcuts")
        ShortcutUtils.createShortcutsToChatRooms(coreContext.context)
    }

    @WorkerThread
    fun notifyContactsListChanged() {
        for (listener in listeners) {
            listener.onContactsLoaded()
        }
    }

    @WorkerThread
    fun findContactById(id: String): Friend? {
        Log.d("$TAG Looking for a friend with ref key [$id]")
        for (friendList in coreContext.core.friendsLists) {
            val found = friendList.findFriendByRefKey(id)
            if (found != null) {
                Log.d("$TAG Found friend [${found.name}] matching ref key [$id]")
                return found
            }
        }
        Log.w("$TAG No friend matching ref key [$id] has been found")
        return null
    }

    @WorkerThread
    fun findContactByAddress(address: Address): Friend? {
        val sipUri = LinphoneUtils.getAddressAsCleanStringUriOnly(address)
        Log.d("$TAG Looking for friend with SIP URI [$sipUri]")

        val username = address.username
        val found = coreContext.core.findFriend(address)
        if (found != null) {
            Log.d("$TAG Friend [${found.name}] was found using SIP URI [$sipUri]")
            return found
        }

        // Start an async query in Magic Search in case LDAP or remote CardDAV is configured
        val remoteContactDirectories = coreContext.core.remoteContactDirectories
        if (remoteContactDirectories.isNotEmpty() && !magicSearchMap.keys.contains(sipUri) && !unknownRemoteContactDirectoriesContactsMap.contains(
                sipUri
            )
        ) {
            Log.i(
                "$TAG SIP URI [$sipUri] not found in locally stored Friends, trying LDAP/CardDAV remote directory"
            )

            val magicSearch = coreContext.core.createMagicSearch()
            magicSearch.addListener(magicSearchListener)
            magicSearchMap[sipUri] = magicSearch

            magicSearch.getContactsListAsync(
                username,
                address.domain,
                MagicSearch.Source.LdapServers.toInt() or MagicSearch.Source.RemoteCardDAV.toInt(),
                MagicSearch.Aggregation.Friend
            )
        }

        val sipAddress = if (sipUri.startsWith("sip:")) {
            sipUri.substring("sip:".length)
        } else if (sipUri.startsWith("sips:")) {
            sipUri.substring("sips:".length)
        } else {
            sipUri
        }

        return if (!username.isNullOrEmpty() && (username.startsWith("+") || username.isDigitsOnly())) {
            Log.d("$TAG Looking for friend with phone number [$username]")
            val foundUsingPhoneNumber = coreContext.core.findFriendByPhoneNumber(username)
            if (foundUsingPhoneNumber != null) {
                Log.d(
                    "$TAG Friend [${foundUsingPhoneNumber.name}] was found using phone number [$username]"
                )
                foundUsingPhoneNumber
            } else {
                Log.d(
                    "$TAG Friend wasn't found using phone number [$username], looking in native address book directly"
                )
                null
            }
        } else {
            Log.d(
                "$TAG Friend wasn't found using SIP address [$sipAddress] and username [$username] isn't a phone number, looking in native address book directly"
            )
            null
        }
    }

    @WorkerThread
    fun findDisplayName(address: Address): String {
        return getContactAvatarModelForAddress(address).friend.name ?: LinphoneUtils.getDisplayName(
            address
        )
    }

    @WorkerThread
    fun getContactAvatarModelForAddress(address: Address?): ContactAvatarModel {
        if (address == null) {
            Log.w("$TAG Address is null, generic model will be used")
            val fakeFriend = coreContext.core.createFriend()
            return ContactAvatarModel(fakeFriend)
        }

        val clone = address.clone()
        clone.clean()
        val key = clone.asStringUriOnly()

        val foundInMap = getAvatarModelFromCache(key)
        if (foundInMap != null) {
            Log.d("$TAG Avatar model found in map for SIP URI [$key]")
            return foundInMap
        }

        val localAccount = coreContext.core.accountList.find {
            it.params.identityAddress?.weakEqual(clone) == true
        }
        val avatar = if (localAccount != null) {
            Log.d("$TAG [$key] SIP URI matches one of the local account")
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = clone
            fakeFriend.name = LinphoneUtils.getDisplayName(localAccount.params.identityAddress)
            fakeFriend.photo = localAccount.params.pictureUri
            val model = ContactAvatarModel(fakeFriend)
            model.trust.postValue(SecurityLevel.EndToEndEncryptedAndVerified)
            unknownContactsAvatarsMap[key] = model
            model
        } else {
            Log.d("$TAG Looking for friend matching SIP URI [$key]")
            val friend = findContactByAddress(clone)
            if (friend != null) {
                Log.d("$TAG Matching friend [${friend.name}] found for SIP URI [$key]")
                val model = ContactAvatarModel(friend, address)
                knownContactsAvatarsMap[key] = model
                model
            } else {
                Log.d("$TAG No matching friend found for SIP URI [$key]...")
                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.name = LinphoneUtils.getDisplayName(address)
                fakeFriend.address = clone
                val model = ContactAvatarModel(fakeFriend)
                unknownContactsAvatarsMap[key] = model
                model
            }
        }

        return avatar
    }

    @WorkerThread
    fun getContactAvatarModelForFriend(friend: Friend?): ContactAvatarModel {
        if (friend == null) {
            Log.w("$TAG Friend is null, using generic avatar model")
            val fakeFriend = coreContext.core.createFriend()
            return ContactAvatarModel(fakeFriend)
        }

        val avatar = ContactAvatarModel(friend)
        return avatar
    }

    @WorkerThread
    fun getContactAvatarModelForConferenceInfo(conferenceInfo: ConferenceInfo): ContactAvatarModel {
        // Do not clean parameters!
        val key = conferenceInfo.uri?.asStringUriOnly()
        if (key == null) {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = conferenceInfo.subject
            val model = ContactAvatarModel(fakeFriend)
            model.showTrust.postValue(false)
            return model
        }

        val foundInMap = conferenceAvatarMap[key] ?: conferenceAvatarMap[key]
        if (foundInMap != null) return foundInMap

        val avatar = LinphoneUtils.getAvatarModelForConferenceInfo(conferenceInfo)
        conferenceAvatarMap[key] = avatar

        return avatar
    }

    @WorkerThread
    fun isContactAvailable(friend: Friend): Boolean {
        return !friend.refKey.isNullOrEmpty() && !isContactTemporary(friend)
    }

    @WorkerThread
    fun isContactTemporary(friend: Friend, allowNullFriendList: Boolean = false): Boolean {
        val friendList = friend.friendList
        if (friendList == null && !allowNullFriendList) return true
        return friendList?.type == FriendList.Type.ApplicationCache
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        Log.i("$TAG Core has been started")
        loadContactsOnlyFromDefaultDirectory = corePreferences.fetchContactsFromDefaultDirectory

        core.addListener(coreListener)
        for (list in core.friendsLists) {
            Log.i("$TAG Found existing friend list [${list.displayName}]")
            list.addListener(friendListListener)
        }

        val context = coreContext.context
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("$TAG READ_CONTACTS permission was denied, creating chat rooms shortcuts")
            ShortcutUtils.createShortcutsToChatRooms(context)
        }

        for (list in core.friendsLists) {
            if (list.type == FriendList.Type.CardDAV && !list.uri.isNullOrEmpty()) {
                Log.i(
                    "$TAG Found a CardDAV friend list with name [${list.displayName}] and URI [${list.uri}], synchronizing it"
                )
                list.synchronizeFriendsFromServer()
            }
        }
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        Log.w("$TAG Core has been stopped")
        coroutineScope.cancel()

        core.removeListener(coreListener)

        for (list in core.friendsLists) {
            list.removeListener(friendListListener)
        }
    }

    @WorkerThread
    fun getRemoteContactDirectoriesCacheFriendList(): FriendList {
        val core = coreContext.core
        val name = FRIEND_LIST_TEMPORARY_STORED_REMOTE_DIRECTORY
        val temporaryFriendList = core.getFriendListByName(name) ?: core.createFriendList()
        if (temporaryFriendList.displayName.isNullOrEmpty()) {
            temporaryFriendList.isDatabaseStorageEnabled = false
            temporaryFriendList.displayName = name
            temporaryFriendList.type = FriendList.Type.ApplicationCache
            core.addFriendList(temporaryFriendList)
            Log.i(
                "$TAG Created temporary friend list with name [$name]"
            )
        }
        return temporaryFriendList
    }

    @WorkerThread
    fun getMePerson(localAddress: Address): Person {
        val account = coreContext.core.accountList.find {
            it.params.identityAddress?.weakEqual(localAddress) == true
        }
        val name = account?.params?.identityAddress?.displayName ?: LinphoneUtils.getDisplayName(
            localAddress
        )
        val personBuilder = Person.Builder().setName(name)

        val photo = account?.params?.pictureUri.orEmpty()
        val bm = ImageUtils.getBitmap(coreContext.context, photo)
        personBuilder.setIcon(
            if (bm == null) {
                AvatarGenerator(coreContext.context).setInitials(AppUtils.getInitials(name)).buildIcon()
            } else {
                IconCompat.createWithAdaptiveBitmap(bm)
            }
        )

        val identity = account?.params?.identityAddress?.asStringUriOnly() ?: localAddress.asStringUriOnly()
        personBuilder.setKey(identity)
        personBuilder.setImportant(true)
        return personBuilder.build()
    }

    @WorkerThread
    fun updateContactsModelDependingOnDefaultAccountMode() {
        val showTrust = true
        Log.i(
            "$TAG Default account mode is [${if (showTrust) "end-to-end encryption mandatory" else "interoperable"}], update all contact models showTrust value"
        )
        knownContactsAvatarsMap.forEach { (_, contactAvatarModel) ->
            contactAvatarModel.showTrust.postValue(showTrust)
        }
        unknownContactsAvatarsMap.forEach { (_, contactAvatarModel) ->
            contactAvatarModel.showTrust.postValue(showTrust)
        }
        conferenceAvatarMap.forEach { (_, contactAvatarModel) ->
            contactAvatarModel.showTrust.postValue(showTrust)
        }
    }

    @WorkerThread
    private fun getAvatarModelFromCache(key: String): ContactAvatarModel? {
        return knownContactsAvatarsMap[key] ?: unknownContactsAvatarsMap[key]
    }

    interface ContactsListener {
        fun onContactsLoaded()

        fun onContactFoundInRemoteDirectory(friend: Friend)
    }
}

@WorkerThread
fun Friend.getAvatarBitmap(round: Boolean = false): Bitmap? {
    try {
        return ImageUtils.getBitmap(
            coreContext.context,
            photo ?: getNativeContactPictureUri()?.toString(),
            round
        )
    } catch (numberFormatException: NumberFormatException) {
        // Expected for contacts created by Linphone
    }
    return null
}

@WorkerThread
fun Friend.getNativeContactPictureUri(): Uri? {
    val contactId = refKey
    if (contactId != null) {
        try {
            val lookupUri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contactId.toLong()
            )

            val pictureUri = Uri.withAppendedPath(
                lookupUri,
                ContactsContract.Contacts.Photo.DISPLAY_PHOTO
            )
            // Check that the URI points to a real file
            val contentResolver = coreContext.context.contentResolver
            try {
                val fd = contentResolver.openAssetFileDescriptor(pictureUri, "r")
                if (fd != null) {
                    fd.close()
                    return pictureUri
                }
            } catch (e: Exception) {
                Log.e("[Contacts Manager] Can't open [$pictureUri] for contact [$name]: $e")
            }

            // Fallback to thumbnail
            return Uri.withAppendedPath(
                lookupUri,
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        } catch (numberFormatException: NumberFormatException) {
            // Expected for contacts created by Linphone
        }
    }
    return null
}

@WorkerThread
fun Friend.getPerson(): Person {
    val personBuilder = Person.Builder().setName(name)

    val bm: Bitmap? = getAvatarBitmap()
    personBuilder.setIcon(
        if (bm == null) {
            Log.i(
                "[Friend] Can't use friend [$name] picture path, generating avatar based on initials"
            )
            AvatarGenerator(coreContext.context).setInitials(AppUtils.getInitials(name.orEmpty())).buildIcon()
        } else {
            IconCompat.createWithAdaptiveBitmap(bm)
        }
    )

    personBuilder.setKey(refKey)
    personBuilder.setUri(nativeUri)
    personBuilder.setImportant(true)
    return personBuilder.build()
}

@WorkerThread
fun Friend.getListOfSipAddresses(): ArrayList<Address> {
    val addressesList = arrayListOf<Address>()

    for (address in addresses) {
        if (addressesList.find { it.weakEqual(address) } == null) {
            addressesList.add(address)
        }
    }

    return addressesList
}

@WorkerThread
fun Friend.getListOfSipAddressesAndPhoneNumbers(listener: ContactNumberOrAddressClickListener): ArrayList<ContactNumberOrAddressModel> {
    val addressesAndNumbers = arrayListOf<ContactNumberOrAddressModel>()

    for (address in getListOfSipAddresses()) {
        val data = ContactNumberOrAddressModel(
            this,
            address,
            address.asStringUriOnly(),
            true, // SIP addresses are always enabled
            listener,
            true
        )
        addressesAndNumbers.add(data)
    }
    if (corePreferences.hidePhoneNumbers) {
        return addressesAndNumbers
    }

    val indexOfLastSipAddress = addressesAndNumbers.count()
    for (number in phoneNumbersWithLabel) {
        val presenceModel = getPresenceModelForUriOrTel(number.phoneNumber)
        val hasPresenceInfo = !presenceModel?.contact.isNullOrEmpty()
        var presenceAddress: Address? = null

        if (presenceModel != null && hasPresenceInfo) {
            Log.d("[Friend] Phone number [${number.phoneNumber}] has presence information")
            // Show linked SIP address if not already stored as-is
            val contact = presenceModel.contact
            if (!contact.isNullOrEmpty()) {
                val address = core.interpretUrl(contact, false)
                if (address != null) {
                    address.clean() // To remove ;user=phone
                    presenceAddress = address
                    if (addressesAndNumbers.find { it.address?.weakEqual(address) == true } == null) {
                        val data = ContactNumberOrAddressModel(
                            this,
                            address,
                            address.asStringUriOnly(),
                            true, // SIP addresses are always enabled
                            listener,
                            true
                        )
                        addressesAndNumbers.add(indexOfLastSipAddress, data)
                    }
                    Log.d(
                        "[Friend] Phone number [${number.phoneNumber}] is linked to SIP address [${presenceAddress.asStringUriOnly()}]"
                    )
                }
            }
        }

        // phone numbers are disabled is secure mode unless linked to a SIP address
        val defaultAccount = LinphoneUtils.getDefaultAccount()
        val enablePhoneNumbers = hasPresenceInfo || !isEndToEndEncryptionMandatory()
        val address = presenceAddress ?: core.interpretUrl(
            number.phoneNumber,
            LinphoneUtils.applyInternationalPrefix(defaultAccount)
        )
        val label = PhoneNumberUtils.vcardParamStringToAddressBookLabel(
            coreContext.context.resources,
            number.label ?: ""
        )
        val data = ContactNumberOrAddressModel(
            this,
            address,
            number.phoneNumber,
            enablePhoneNumbers,
            listener,
            false,
            label,
            presenceAddress != null
        )
        addressesAndNumbers.add(data)
    }

    return addressesAndNumbers
}

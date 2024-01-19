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
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.loader.app.LoaderManager
import java.io.IOException
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.AppUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PhoneNumberUtils

class ContactsManager @UiThread constructor() {
    companion object {
        private const val TAG = "[Contacts Manager]"
    }

    private var nativeContactsLoaded = false

    private val listeners = arrayListOf<ContactsListener>()

    private val knownContactsAvatarsMap = hashMapOf<String, ContactAvatarModel>()
    private val unknownContactsAvatarsMap = hashMapOf<String, ContactAvatarModel>()
    private val conferenceAvatarMap = hashMapOf<String, ContactAvatarModel>()

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(list: FriendList, friends: Array<Friend>) {
            Log.i(
                "$TAG Presence received for list [${list.displayName}] and [${friends.size}] friends"
            )

            // TODO FIXME: doesn't work if a SIP address wasn't added to unknownContactsAvatarsMap yet
            // For example if it wasn't displayed so far in any list
            var atLeastSomeoneNew = false
            if (unknownContactsAvatarsMap.isNotEmpty()) {
                for (friend in friends) {
                    for (phoneNumber in friend.phoneNumbers) {
                        val presence = friend.getPresenceModelForUriOrTel(phoneNumber)
                        if (presence != null) {
                            val contactAddress = presence.contact
                            if (unknownContactsAvatarsMap.keys.contains(contactAddress)) {
                                Log.i(
                                    "$TAG Found a new SIP Address: [$contactAddress] matching phone number [$phoneNumber]"
                                )

                                val oldModel = unknownContactsAvatarsMap[contactAddress]
                                oldModel?.destroy()

                                unknownContactsAvatarsMap.remove(contactAddress)
                                atLeastSomeoneNew = true
                            }
                        }
                    }
                }
            }

            if (atLeastSomeoneNew) {
                Log.i("$TAG At least a new SIP address was discovered, reloading contacts")
                conferenceAvatarMap.values.forEach(ContactAvatarModel::destroy)
                conferenceAvatarMap.clear()

                for (listener in listeners) {
                    listener.onContactsLoaded()
                }
            } else {
                Log.i("$TAG Presence has been received but no new SIP URI was found, doing nothing")
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
    }

    @UiThread
    fun loadContacts(activity: MainActivity) {
        val manager = LoaderManager.getInstance(activity)
        manager.restartLoader(0, null, ContactLoader())
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
    fun onNativeContactsLoaded() {
        nativeContactsLoaded = true
        Log.i("$TAG Native contacts have been loaded, cleaning avatars maps")

        knownContactsAvatarsMap.values.forEach(ContactAvatarModel::destroy)
        knownContactsAvatarsMap.clear()
        unknownContactsAvatarsMap.values.forEach(ContactAvatarModel::destroy)
        unknownContactsAvatarsMap.clear()
        conferenceAvatarMap.values.forEach(ContactAvatarModel::destroy)
        conferenceAvatarMap.clear()

        notifyContactsListChanged()
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
        Log.d("$TAG No friend matching ref key [$id] has been found")
        return null
    }

    @WorkerThread
    fun findContactByAddress(address: Address): Friend? {
        val clonedAddress = address.clone()
        clonedAddress.clean()
        val sipUri = clonedAddress.asStringUriOnly()

        Log.d("$TAG Looking for friend with SIP URI [$sipUri]")
        val username = clonedAddress.username
        val found = coreContext.core.findFriend(clonedAddress)
        return if (found != null) {
            Log.d("$TAG Friend [${found.name}] was found using SIP URI [$sipUri]")
            found
        } else if (!username.isNullOrEmpty() && username.startsWith("+")) {
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
                findNativeContact(sipUri, true, username)
            }
        } else {
            Log.d(
                "$TAG Friend wasn't found using SIP URI [$sipUri] and username [$username] isn't a phone number, looking in native address book directly"
            )
            findNativeContact(sipUri, false)
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
            unknownContactsAvatarsMap[key] = model
            model
        } else {
            Log.d("$TAG Looking for friend matching SIP URI [$key]")
            val friend = coreContext.contactsManager.findContactByAddress(clone)
            if (friend != null) {
                Log.d("$TAG Matching friend [${friend.name}] found for SIP URI [$key]")
                val model = ContactAvatarModel(friend)
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

        val address = friend.address ?: friend.addresses.firstOrNull()
            ?: return ContactAvatarModel(friend)
        Log.d(
            "$TAG Looking for avatar model for friend [${friend.name}] using SIP URI  [${address.asStringUriOnly()}]"
        )

        val clone = address.clone()
        clone.clean()
        val key = clone.asStringUriOnly()

        val foundInMap = getAvatarModelFromCache(key)
        if (foundInMap != null) {
            Log.d("$TAG Found avatar model in map using SIP URI [$key]")
            return foundInMap
        }

        Log.w("$TAG Avatar model not found in map with SIP URI [$key]")
        val avatar = ContactAvatarModel(friend)
        knownContactsAvatarsMap[key] = avatar

        return avatar
    }

    @WorkerThread
    fun getContactAvatarModelForConferenceInfo(conferenceInfo: ConferenceInfo): ContactAvatarModel {
        // Do not clean parameters!
        val key = conferenceInfo.uri?.asStringUriOnly()
        if (key == null) {
            val fakeFriend = coreContext.core.createFriend()
            return ContactAvatarModel(fakeFriend)
        }

        val foundInMap = conferenceAvatarMap[key] ?: conferenceAvatarMap[key]
        if (foundInMap != null) return foundInMap

        val avatar = LinphoneUtils.getAvatarModelForConferenceInfo(conferenceInfo)
        conferenceAvatarMap[key] = avatar

        return avatar
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        core.addListener(coreListener)
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        core.removeListener(coreListener)
        for (list in core.friendsLists) {
            list.removeListener(friendListListener)
        }
    }

    @WorkerThread
    fun findNativeContact(address: String, searchAsPhoneNumber: Boolean, number: String = ""): Friend? {
        if (nativeContactsLoaded) {
            Log.i(
                "$TAG Native contacts already loaded, no need to search further, no native contact matches address [$address]"
            )
            return null
        }

        val context = coreContext.context
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(
                "$TAG Looking for native contact with address [$address] ${if (searchAsPhoneNumber) "or phone number [$number]" else ""}"
            )

            try {
                val selection = if (searchAsPhoneNumber) {
                    "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS} LIKE ?"
                } else {
                    "${ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS} LIKE ?"
                }
                val selectionParams = if (searchAsPhoneNumber) {
                    arrayOf(number, address)
                } else {
                    arrayOf(address)
                }
                val cursor: Cursor? = context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Data.DISPLAY_NAME_PRIMARY
                    ),
                    selection,
                    selectionParams,
                    null
                )

                if (cursor != null && cursor.moveToNext()) {
                    val friend = coreContext.core.createFriend()
                    friend.edit()

                    do {
                        val id: String =
                            cursor.getString(
                                cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                            )
                        friend.refKey = id

                        if (friend.name.isNullOrEmpty()) {
                            val displayName: String? =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        ContactsContract.Data.DISPLAY_NAME_PRIMARY
                                    )
                                )
                            friend.name = displayName
                        }

                        if (friend.photo.isNullOrEmpty()) {
                            val uri = friend.getNativeContactPictureUri()
                            if (uri != null) {
                                friend.photo = uri.toString()
                            }
                        }

                        if (friend.nativeUri.isNullOrEmpty()) {
                            val lookupKey =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        ContactsContract.Contacts.LOOKUP_KEY
                                    )
                                )
                            friend.nativeUri =
                                "${ContactsContract.Contacts.CONTENT_LOOKUP_URI}/$lookupKey"
                        }
                    } while (cursor.moveToNext())

                    friend.done()

                    Log.d("$TAG Found native contact [${friend.name}] with address [$address]")
                    cursor.close()
                    return friend
                }

                Log.w("$TAG Failed to find native contact with address [$address]")
                return null
            } catch (e: IllegalArgumentException) {
                Log.e("$TAG Failed to search for native contact with address [$address]: $e")
            }
        } else {
            Log.w("$TAG READ_CONTACTS permission not granted, can't check native address book")
        }
        return null
    }

    @WorkerThread
    fun getMePerson(localAddress: Address): Person {
        val account = coreContext.core.accountList.find {
            it.params.identityAddress?.weakEqual(localAddress) ?: false
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
    private fun getAvatarModelFromCache(key: String): ContactAvatarModel? {
        return knownContactsAvatarsMap[key] ?: unknownContactsAvatarsMap[key]
    }

    interface ContactsListener {
        fun onContactsLoaded()
    }
}

@WorkerThread
fun Friend.getAvatarBitmap(): Bitmap? {
    return ImageUtils.getBitmap(coreContext.context, photo)
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
            } catch (_: IOException) { }

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
fun Friend.getListOfSipAddressesAndPhoneNumbers(listener: ContactNumberOrAddressClickListener): ArrayList<ContactNumberOrAddressModel> {
    val addressesAndNumbers = arrayListOf<ContactNumberOrAddressModel>()

    for (address in addresses) {
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
    val indexOfLastSipAddress = addressesAndNumbers.count()
    Log.i(
        "[Friend] Contact [$name] has [$indexOfLastSipAddress] SIP ${if (indexOfLastSipAddress > 1) "addresses" else "address"}"
    )

    for (number in phoneNumbersWithLabel) {
        val presenceModel = getPresenceModelForUriOrTel(number.phoneNumber)
        val hasPresenceInfo = !presenceModel?.contact.isNullOrEmpty()
        var presenceAddress: Address? = null

        if (presenceModel != null && hasPresenceInfo) {
            Log.i("[Friend] Phone number [${number.phoneNumber}] has presence information")
            // Show linked SIP address if not already stored as-is
            val contact = presenceModel.contact
            val found = addressesAndNumbers.find {
                it.displayedValue == contact
            }
            if (!contact.isNullOrEmpty() && found == null) {
                val address = core.interpretUrl(contact, false)
                if (address != null) {
                    address.clean() // To remove ;user=phone
                    presenceAddress = address
                    val data = ContactNumberOrAddressModel(
                        this,
                        address,
                        address.asStringUriOnly(),
                        true, // SIP addresses are always enabled
                        listener,
                        true
                    )
                    addressesAndNumbers.add(indexOfLastSipAddress, data)
                    Log.i(
                        "[Friend] Phone number [${number.phoneNumber}] is linked to SIP address [${presenceAddress.asStringUriOnly()}]"
                    )
                }
            } else if (found != null) {
                presenceAddress = found.address
                Log.i(
                    "[Friend] Phone number [${number.phoneNumber}] is linked to existing SIP address [${presenceAddress?.asStringUriOnly()}]"
                )
            }
        }

        // phone numbers are disabled is secure mode unless linked to a SIP address
        val enablePhoneNumbers = hasPresenceInfo || core.defaultAccount?.isInSecureMode() != true
        val address = presenceAddress ?: core.interpretUrl(number.phoneNumber, true)
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

    val phoneNumbersCount = addressesAndNumbers.count() - indexOfLastSipAddress
    Log.i(
        "[Friend] Contact [$name] has [$phoneNumbersCount] phone ${if (phoneNumbersCount > 1) "numbers" else "number"}"
    )
    return addressesAndNumbers
}

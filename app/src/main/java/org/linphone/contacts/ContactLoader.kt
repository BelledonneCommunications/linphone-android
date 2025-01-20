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

import android.database.Cursor
import android.database.StaleDataException
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Patterns
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import java.lang.Exception
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.GlobalState
import org.linphone.core.SubscribePolicy
import org.linphone.core.tools.Log
import org.linphone.utils.PhoneNumberUtils

class ContactLoader : LoaderManager.LoaderCallbacks<Cursor> {
    companion object {
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )

        private const val TAG = "[Contacts Loader]"

        const val NATIVE_ADDRESS_BOOK_FRIEND_LIST = "Native address-book"
        const val LINPHONE_ADDRESS_BOOK_FRIEND_LIST = "Linphone address-book"

        private const val MIN_INTERVAL_TO_WAIT_BEFORE_REFRESH = 300000L // 5 minutes
    }

    private val friends = HashMap<String, Friend>()

    @MainThread
    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        Log.i("$TAG Creating and starting cursor loader")
        val mimeType = ContactsContract.Data.MIMETYPE
        val mimeSelection = "$mimeType = ? OR $mimeType = ? OR $mimeType = ? OR $mimeType = ?"

        val selection = if (args?.getBoolean("defaultDirectory", true) == true) {
            Log.i("$TAG Only fetching contacts from default directory")
            ContactsContract.Data.IN_DEFAULT_DIRECTORY + " == 1 AND ($mimeSelection)"
        } else {
            Log.i("$TAG Fetching all available contacts")
            mimeSelection
        }

        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        )

        val loader = CursorLoader(
            coreContext.context,
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            ContactsContract.Data.CONTACT_ID + " ASC"
        )

        // Update at most once every X (see variable value for actual duration)
        loader.setUpdateThrottle(MIN_INTERVAL_TO_WAIT_BEFORE_REFRESH)

        return loader
    }

    @MainThread
    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor == null) {
            Log.e("$TAG Cursor is null!")
            return
        }
        Log.i("$TAG Load finished, found ${cursor.count} entries in cursor")

        coreContext.postOnCoreThread {
            parseFriends(cursor)
        }
    }

    @MainThread
    override fun onLoaderReset(loader: Loader<Cursor>) {
        Log.i("$TAG Loader reset")
    }

    @WorkerThread
    private fun parseFriends(cursor: Cursor) {
        val core = coreContext.core

        val state = core.globalState
        if (state == GlobalState.Shutdown || state == GlobalState.Off) {
            Log.w("$TAG Core is being stopped or already destroyed, abort")
            return
        }

        try {
            val contactIdColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mimetypeColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val displayNameColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.Data.DISPLAY_NAME_PRIMARY
            )
            val starredColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            val lookupColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.Contacts.LOOKUP_KEY
            )
            val phoneNumberColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val phoneTypeColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.TYPE
            )
            val phoneLabelColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.LABEL
            )
            val normalizedPhoneColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            )
            val sipAddressColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS
            )
            val companyColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Organization.COMPANY
            )
            val jobTitleColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Organization.TITLE
            )
            val givenNameColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME
            )
            val familyNameColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
            )
            while (!cursor.isClosed && cursor.moveToNext()) {
                try {
                    val id: String = cursor.getString(contactIdColumn)
                    val mime: String? = cursor.getString(mimetypeColumn)

                    val friend = friends[id] ?: core.createFriend()
                    friend.refKey = id
                    if (friend.name.isNullOrEmpty()) {
                        val displayName: String? = cursor.getString(displayNameColumn)
                        if (!displayName.isNullOrEmpty()) {
                            friend.name = displayName

                            val uri = friend.getNativeContactPictureUri()
                            if (uri != null) {
                                friend.photo = uri.toString()
                            }

                            val starred = cursor.getInt(starredColumn) == 1
                            friend.starred = starred

                            val lookupKey =
                                cursor.getString(lookupColumn)
                            friend.nativeUri =
                                "${ContactsContract.Contacts.CONTENT_LOOKUP_URI}/$lookupKey"

                            friend.isSubscribesEnabled = false
                            // Disable peer to peer short term presence
                            friend.incSubscribePolicy = SubscribePolicy.SPDeny
                        }
                    }

                    when (mime) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val data1: String? = cursor.getString(phoneNumberColumn)
                            val data2: String? = cursor.getString(phoneTypeColumn)
                            val data3: String? = cursor.getString(phoneLabelColumn)
                            val data4: String? = cursor.getString(normalizedPhoneColumn)

                            val label =
                                PhoneNumberUtils.addressBookLabelTypeToVcardParamString(
                                    data2?.toInt()
                                        ?: ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM,
                                    data3
                                )

                            val number =
                                if (data1.isNullOrEmpty() ||
                                    !Patterns.PHONE.matcher(data1).matches()
                                ) {
                                    data4 ?: data1
                                } else {
                                    data1
                                }

                            if (number != null) {
                                if (friend.phoneNumbersWithLabel.find {
                                    PhoneNumberUtils.arePhoneNumberWeakEqual(it.phoneNumber, number)
                                } == null
                                ) {
                                    val phoneNumber = Factory.instance()
                                        .createFriendPhoneNumber(number, label)
                                    friend.addPhoneNumberWithLabel(phoneNumber)
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE -> {
                            val sipAddress: String? = cursor.getString(sipAddressColumn)
                            if (sipAddress != null) {
                                val address = core.interpretUrl(sipAddress, false)
                                if (address != null) {
                                    friend.addAddress(address)
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val organization: String? = cursor.getString(companyColumn)
                            if (organization != null) {
                                friend.organization = organization
                            }

                            val job: String? = cursor.getString(jobTitleColumn)
                            if (job != null) {
                                friend.jobTitle = job
                            }
                        }
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val vCard = friend.vcard
                            if (vCard != null) {
                                val givenName: String? = cursor.getString(givenNameColumn)
                                if (!givenName.isNullOrEmpty()) {
                                    vCard.givenName = givenName
                                }

                                val familyName: String? = cursor.getString(familyNameColumn)
                                if (!familyName.isNullOrEmpty()) {
                                    vCard.familyName = familyName
                                }
                            }
                        }
                    }

                    friends[id] = friend
                } catch (e: Exception) {
                    Log.e("$TAG Exception: $e")
                }
            }

            Log.i("$TAG Contacts parsed, posting another task to handle adding them (or not)")
            // Re-post another task to allow other tasks on Core thread
            coreContext.postOnCoreThread {
                addFriendsIfNeeded()
            }
        } catch (sde: StaleDataException) {
            Log.e("$TAG State Data Exception: $sde")
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Illegal State Exception: $ise")
        } catch (e: Exception) {
            Log.e("$TAG Exception: $e")
        }
    }

    @WorkerThread
    private fun addFriendsIfNeeded() {
        val core = coreContext.core

        if (core.globalState == GlobalState.Shutdown || core.globalState == GlobalState.Off) {
            Log.w("$TAG Core is being stopped or already destroyed, abort")
        } else if (friends.isEmpty) {
            Log.w("$TAG No friend created!")
        } else {
            Log.i("$TAG ${friends.size} friends fetched")

            val friendsList = core.getFriendListByName(NATIVE_ADDRESS_BOOK_FRIEND_LIST)
                ?: core.createFriendList()
            if (friendsList.displayName.isNullOrEmpty()) {
                Log.i(
                    "$TAG Friend list [$NATIVE_ADDRESS_BOOK_FRIEND_LIST] didn't exist yet, let's create it"
                )
                friendsList.isDatabaseStorageEnabled =
                    true // Store them to keep presence info available for push notifications & favorites
                friendsList.type = FriendList.Type.Default
                friendsList.displayName = NATIVE_ADDRESS_BOOK_FRIEND_LIST
                core.addFriendList(friendsList)

                for (friend in friends.values) {
                    friendsList.addLocalFriend(friend)
                }
                Log.i("$TAG Friends added")
            } else {
                Log.i(
                    "$TAG Friend list [$NATIVE_ADDRESS_BOOK_FRIEND_LIST] found, synchronizing existing friends with new ones"
                )
                for (localFriend in friendsList.friends) {
                    val newlyFetchedFriend = friends[localFriend.refKey]
                    if (newlyFetchedFriend != null) {
                        friends.remove(localFriend.refKey)
                        localFriend.nativeUri =
                            newlyFetchedFriend.nativeUri // Native URI isn't stored in linphone database, needs to be updated
                        if (newlyFetchedFriend.vcard?.asVcard4String() == localFriend.vcard?.asVcard4String()) continue

                        localFriend.edit()
                        // Update basic fields that may have changed
                        localFriend.name = newlyFetchedFriend.name
                        localFriend.organization = newlyFetchedFriend.organization
                        localFriend.jobTitle = newlyFetchedFriend.jobTitle
                        localFriend.photo = newlyFetchedFriend.photo

                        // Clear local friend phone numbers & add all newly fetched one ones
                        var atLeastAPhoneNumberWasRemoved = false
                        for (phoneNumber in localFriend.phoneNumbersWithLabel) {
                            val found = newlyFetchedFriend.phoneNumbers.find {
                                it == phoneNumber.phoneNumber
                            }
                            if (found == null) {
                                atLeastAPhoneNumberWasRemoved = true
                            }
                            localFriend.removePhoneNumberWithLabel(phoneNumber)
                        }
                        for (phoneNumber in newlyFetchedFriend.phoneNumbersWithLabel) {
                            localFriend.addPhoneNumberWithLabel(phoneNumber)
                        }

                        // If at least a phone number was removed, remove all SIP address from local friend before adding all from newly fetched one.
                        // If none was removed, simply add SIP addresses from fetched contact that aren't already in the local friend.
                        if (atLeastAPhoneNumberWasRemoved) {
                            Log.w(
                                "$TAG At least a phone number was removed from native contact [${localFriend.name}], clearing all SIP addresses from local friend before adding back the ones that still exists"
                            )
                            for (sipAddress in localFriend.addresses) {
                                localFriend.removeAddress(sipAddress)
                            }
                        }

                        // Adding only newly added SIP address(es) in native contact if any
                        for (sipAddress in newlyFetchedFriend.addresses) {
                            localFriend.addAddress(sipAddress)
                        }
                        localFriend.done()
                    } else {
                        Log.i(
                            "$TAG Friend [${localFriend.name}] with ref key [${localFriend.refKey}] not found in newly fetched batch, removing it"
                        )
                        friendsList.removeFriend(localFriend)
                    }
                }

                // Check for newly created friends since last sync
                val localFriends = friendsList.friends
                for ((key, newFriend) in friends.entries) {
                    val found = localFriends.find {
                        it.refKey == key
                    }
                    if (found == null) {
                        if (newFriend.refKey == null) {
                            Log.w(
                                "$TAG Found friend [${newFriend.name}] with no refKey, using ID [$key]"
                            )
                            newFriend.refKey = key
                        }
                        Log.i(
                            "$TAG Friend [${newFriend.name}] with ref key [${newFriend.refKey}] not found in currently stored list, adding it"
                        )
                        friendsList.addLocalFriend(newFriend)
                    }
                }
                Log.i("$TAG Friends synchronized")
            }
            friends.clear()

            friendsList.updateSubscriptions()
            Log.i("$TAG Subscription(s) updated")
            coreContext.contactsManager.onNativeContactsLoaded()
        }
    }
}

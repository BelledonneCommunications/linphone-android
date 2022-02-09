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

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.SubscribePolicy
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.ImageUtils

class NativeContact(val nativeId: String, private val lookupKey: String? = null) : Contact() {
    override fun compareTo(other: Contact): Int {
        val superResult = super.compareTo(other)
        if (superResult == 0 && other is NativeContact) {
            return nativeId.compareTo(other.nativeId)
        }
        return superResult
    }

    override fun getContactThumbnailPictureUri(): Uri {
        return Uri.withAppendedPath(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, nativeId.toLong()),
            ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
        )
    }

    override fun getContactPictureUri(): Uri {
        return Uri.withAppendedPath(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, nativeId.toLong()),
            ContactsContract.Contacts.Photo.DISPLAY_PHOTO
        )
    }

    override fun getPerson(): Person {
        val personBuilder = Person.Builder().setName(fullName)

        val bm: Bitmap? =
            ImageUtils.getRoundBitmapFromUri(
                coreContext.context,
                getContactThumbnailPictureUri()
            )
        val icon =
            if (bm == null) IconCompat.createWithResource(
                coreContext.context,
                R.drawable.avatar
            ) else IconCompat.createWithAdaptiveBitmap(bm)
        if (icon != null) {
            personBuilder.setIcon(icon)
        }

        personBuilder.setImportant(isStarred)
        if (lookupKey != null) {
            personBuilder.setUri("${ContactsContract.Contacts.CONTENT_LOOKUP_URI}/$lookupKey")
        }

        return personBuilder.build()
    }

    @Synchronized
    override fun syncValuesFromAndroidCursor(cursor: Cursor) {
        try {
            val displayName: String? =
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME_PRIMARY))

            val mime: String? =
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
            val data1: String? = cursor.getString(cursor.getColumnIndexOrThrow("data1"))
            val data2: String? = cursor.getString(cursor.getColumnIndexOrThrow("data2"))
            val data3: String? = cursor.getString(cursor.getColumnIndexOrThrow("data3"))
            val data4: String? = cursor.getString(cursor.getColumnIndexOrThrow("data4"))

            if (fullName == null || fullName != displayName) {
                Log.d("[Native Contact] Setting display name $displayName")
                fullName = displayName
            }

            val linphoneMime = AppUtils.getString(R.string.linphone_address_mime_type)
            when (mime) {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                    if (data1 == null && data4 == null) {
                        Log.d("[Native Contact] Phone number data is empty")
                        return
                    }

                    val labelColumnIndex =
                        cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                    val label: String? = cursor.getString(labelColumnIndex)
                    val typeColumnIndex =
                        cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val type: Int = cursor.getInt(typeColumnIndex)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        coreContext.context.resources,
                        type,
                        label
                    ).toString()

                    // data4 = ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                    // data1 = ContactsContract.CommonDataKinds.Phone.NUMBER
                    val number = if (corePreferences.preferNormalizedPhoneNumbersFromAddressBook) {
                        data4 ?: data1
                    } else {
                        data1 ?: data4
                    }
                    if (number != null && number.isNotEmpty()) {
                        Log.d("[Native Contact] Found phone number $data1 ($data4), type label is $typeLabel")
                        if (!rawPhoneNumbers.contains(number)) {
                            phoneNumbers.add(PhoneNumber(number, typeLabel))
                            rawPhoneNumbers.add(number)
                        }
                    }
                }
                linphoneMime, ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE -> {
                    if (data1 == null) {
                        Log.d("[Native Contact] SIP address is null")
                        return
                    }

                    Log.d("[Native Contact] Found SIP address $data1")
                    if (rawPhoneNumbers.contains(data1)) {
                        Log.d("[Native Contact] SIP address value already exists in phone numbers list, skipping")
                        return
                    }

                    val address: Address? = coreContext.core.interpretUrl(data1)
                    if (address == null) {
                        Log.e("[Native Contact] Couldn't parse address $data1 !")
                        return
                    }

                    val stringAddress = address.asStringUriOnly()
                    Log.d("[Native Contact] Found SIP address $stringAddress")
                    if (!rawSipAddresses.contains(data1)) {
                        sipAddresses.add(address)
                        rawSipAddresses.add(data1)
                    }
                }
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                    if (data1 == null) {
                        Log.d("[Native Contact] Organization is null")
                        return
                    }

                    Log.d("[Native Contact] Found organization $data1")
                    organization = data1
                }
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                    if (data2 == null && data3 == null) {
                        Log.d("[Native Contact] First name and last name are both null")
                        return
                    }

                    Log.d("[Native Contact] Found first name $data2 and last name $data3")
                    firstName = data2
                    lastName = data3
                }
            }
        } catch (iae: IllegalArgumentException) {
            Log.e("[Native Contact] Exception: $iae")
        }
    }

    @Synchronized
    fun createOrUpdateFriendFromNativeContact() {
        var created = false
        if (friend == null) {
            val friend = coreContext.core.createFriend()
            friend.isSubscribesEnabled = false
            friend.incSubscribePolicy = SubscribePolicy.SPDeny
            friend.refKey = nativeId
            friend.userData = this

            created = true
            this.friend = friend
        }

        val friend = this.friend
        if (friend != null) {
            friend.edit()
            val fn = fullName
            if (fn != null) friend.name = fn

            val vCard = friend.vcard
            if (vCard != null) {
                vCard.familyName = lastName
                vCard.givenName = firstName
                vCard.organization = organization
            }

            if (!created) {
                for (address in friend.addresses) friend.removeAddress(address)
                for (number in friend.phoneNumbers) friend.removePhoneNumber(number)
            }

            for (address in sipAddresses) friend.addAddress(address)
            for (number in rawPhoneNumbers) friend.addPhoneNumber(number)

            friend.done()
            if (created) coreContext.core.defaultFriendList?.addFriend(friend)
        }
    }

    @Synchronized
    fun syncValuesFromAndroidContact(context: Context) {
        Log.d("[Native Contact] Looking for contact cursor with id: $nativeId")

        var selection: String = ContactsContract.Data.CONTACT_ID + " == " + nativeId
        if (corePreferences.fetchContactsFromDefaultDirectory) {
            Log.d("[Native Contact] Only fetching contacts in default directory")
            selection = ContactsContract.Data.IN_DEFAULT_DIRECTORY + " == 1 AND " + selection
        }

        val cursor: Cursor? = context.contentResolver
            .query(
                ContactsContract.Data.CONTENT_URI,
                AsyncContactsLoader.projection,
                selection,
                null,
                null
            )
        if (cursor != null) {
            sipAddresses.clear()
            rawSipAddresses.clear()
            phoneNumbers.clear()
            rawPhoneNumbers.clear()

            while (cursor.moveToNext()) {
                syncValuesFromAndroidCursor(cursor)
            }
            cursor.close()
        }
    }

    override fun toString(): String {
        return "${super.toString()}: id [$nativeId], name [$fullName]"
    }
}

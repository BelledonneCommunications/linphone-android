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
package org.linphone.activities.main.contact.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.*
import org.linphone.core.ChatRoomSecurityLevel
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.PermissionHelper

class ContactEditorData(val friend: Friend?) : ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val coroutineScope: CoroutineScope = coreContext.coroutineScope

    val firstName = MutableLiveData<String>()

    val lastName = MutableLiveData<String>()

    val organization = MutableLiveData<String>()

    val displayOrganization = corePreferences.contactOrganizationVisible

    val tempPicturePath = MutableLiveData<String>()
    private var picture: ByteArray? = null

    val numbers = MutableLiveData<ArrayList<NumberOrAddressEditorData>>()

    val addresses = MutableLiveData<ArrayList<NumberOrAddressEditorData>>()

    var syncAccountName: String? = null
    var syncAccountType: String? = null

    init {
        if (friend != null) {
            contact.value = friend!!
            displayName.value = friend.name ?: ""
        } else {
            displayName.value = ""
        }

        organization.value = friend?.organization ?: ""

        firstName.value = ""
        lastName.value = ""
        val refKey = friend?.refKey
        val vCard = friend?.vcard
        if (vCard?.familyName.isNullOrEmpty() && vCard?.givenName.isNullOrEmpty()) {
            if (refKey != null) {
                Log.w("[Contact Editor] vCard first & last name not filled-in yet, doing it now")
                fetchFirstAndLastNames(refKey)
            } else {
                Log.e("[Contact Editor] vCard first & last name not available as contact doesn't have a native ID")
            }
        } else {
            firstName.value = vCard?.givenName
            lastName.value = vCard?.familyName
        }

        updateNumbersAndAddresses(refKey)
    }

    fun save(): Friend {
        var contact = friend
        var created = false

        if (contact == null) {
            created = true
            // From Crashlytics it seems both permissions are required...
            val nativeId = if (PermissionHelper.get().hasReadContactsPermission() &&
                PermissionHelper.get().hasWriteContactsPermission()
            ) {
                Log.i("[Contact Editor] Creating native contact")
                NativeContactEditor.createAndroidContact(syncAccountName, syncAccountType)
                    .toString()
            } else {
                Log.e("[Contact Editor] Can't create native contact, permission denied")
                null
            }
            contact = coreContext.core.createFriend()
            contact.refKey = nativeId
        }

        if (contact.refKey != null) {
            Log.i("[Contact Editor] Committing changes in native contact id ${contact.refKey}")
            NativeContactEditor(contact)
                .setFirstAndLastNames(firstName.value.orEmpty(), lastName.value.orEmpty())
                .setOrganization(organization.value.orEmpty())
                .setPhoneNumbers(numbers.value.orEmpty())
                .setSipAddresses(addresses.value.orEmpty())
                .setPicture(picture)
                .commit()
        }

        if (!created) contact.edit()

        contact.name = "${firstName.value.orEmpty()} ${lastName.value.orEmpty()}"
        contact.organization = organization.value

        for (address in contact.addresses) {
            contact.removeAddress(address)
        }
        for (address in addresses.value.orEmpty()) {
            val sipAddress = address.newValue.value.orEmpty()
            if (sipAddress.isEmpty() || address.toRemove.value == true) continue

            val parsed = coreContext.core.interpretUrl(sipAddress, false)
            if (parsed != null) contact.addAddress(parsed)
        }

        for (phone in contact.phoneNumbers) {
            contact.removePhoneNumber(phone)
        }
        for (phone in numbers.value.orEmpty()) {
            val phoneNumber = phone.newValue.value.orEmpty()
            if (phoneNumber.isEmpty() || phone.toRemove.value == true) continue

            contact.addPhoneNumber(phoneNumber)
        }

        val vCard = contact.vcard
        if (vCard != null) {
            vCard.familyName = lastName.value
            vCard.givenName = firstName.value
        }

        if (created) {
            coreContext.core.defaultFriendList?.addLocalFriend(contact)
        } else {
            contact.done()
        }
        return contact
    }

    fun setPictureFromPath(picturePath: String) {
        var orientation = ExifInterface.ORIENTATION_NORMAL
        var image = BitmapFactory.decodeFile(picturePath)

        try {
            val ei = ExifInterface(picturePath)
            orientation = ei.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            Log.i("[Contact Editor] Exif rotation is $orientation")
        } catch (e: IOException) {
            Log.e("[Contact Editor] Failed to get Exif rotation, exception raised: $e")
        }

        if (image == null) {
            Log.e("[Contact Editor] Couldn't get bitmap from filePath: $picturePath")
            return
        }

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 ->
                image =
                    ImageUtils.rotateImage(image, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 ->
                image =
                    ImageUtils.rotateImage(image, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 ->
                image =
                    ImageUtils.rotateImage(image, 270f)
        }

        val stream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        picture = stream.toByteArray()
        tempPicturePath.value = picturePath
        image.recycle()
        stream.close()
    }

    fun addEmptySipAddress() {
        val list = arrayListOf<NumberOrAddressEditorData>()
        list.addAll(addresses.value.orEmpty())
        list.add(NumberOrAddressEditorData("", true))
        addresses.value = list
    }

    fun addEmptyPhoneNumber() {
        val list = arrayListOf<NumberOrAddressEditorData>()
        list.addAll(numbers.value.orEmpty())
        list.add(NumberOrAddressEditorData("", false))
        numbers.value = list
    }

    private fun updateNumbersAndAddresses(contactId: String?) {
        val phoneNumbers = arrayListOf<NumberOrAddressEditorData>()
        val sipAddresses = arrayListOf<NumberOrAddressEditorData>()
        var fetched = false

        if (contactId != null) {
            try {
                // Try to get real values from contact to ensure edition/removal in native address book will go well
                val cursor = coreContext.context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )

                while (cursor != null && cursor.moveToNext()) {
                    val linphoneMime = AppUtils.getString(R.string.linphone_address_mime_type)
                    val mime: String? =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
                    if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                        val data1: String? =
                            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        if (data1 != null) {
                            phoneNumbers.add(NumberOrAddressEditorData(data1, false))
                        }
                    } else if (
                        mime == ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE ||
                        mime == linphoneMime
                    ) {
                        val data1: String? =
                            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS))
                        if (data1 != null) {
                            sipAddresses.add(NumberOrAddressEditorData(data1, true))
                        }
                    }
                }

                cursor?.close()
                fetched = true
            } catch (e: Exception) {
                Log.e("[Contact Editor] Failed to sip addresses & phone number: $e")
                fetched = false
            }
        }

        if (!fetched) {
            Log.w("[Contact Editor] Fall-backing to friend info (might be inaccurate and thus edition/removal might fail)")
            for (number in friend?.phoneNumbers.orEmpty()) {
                phoneNumbers.add(NumberOrAddressEditorData(number, false))
            }

            for (address in friend?.addresses.orEmpty()) {
                sipAddresses.add(NumberOrAddressEditorData(address.asStringUriOnly(), true))
            }
        }

        if (phoneNumbers.isEmpty()) {
            phoneNumbers.add(NumberOrAddressEditorData("", false))
        }
        numbers.value = phoneNumbers

        if (sipAddresses.isEmpty()) {
            sipAddresses.add(NumberOrAddressEditorData("", true))
        }
        addresses.value = sipAddresses
    }

    private fun fetchFirstAndLastNames(contactId: String) {
        try {
            val cursor = coreContext.context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
                ),
                ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?",
                arrayOf(contactId),
                null
            )

            while (cursor != null && cursor.moveToNext()) {
                val mime: String? = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
                if (mime == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
                    val givenName: String? =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME))
                    if (!givenName.isNullOrEmpty()) {
                        friend?.vcard?.givenName = givenName
                        firstName.value = givenName!!
                    }

                    val familyName: String? =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME))
                    if (!familyName.isNullOrEmpty()) {
                        friend?.vcard?.familyName = familyName
                        lastName.value = familyName!!
                    }
                }
            }

            cursor?.close()
        } catch (e: Exception) {
            Log.e("[Contact Editor] Failed to fetch first & last name: $e")
        }
    }
}

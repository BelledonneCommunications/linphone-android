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
package org.linphone.activities.main.contact.viewmodels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.contact.data.NumberOrAddressEditorData
import org.linphone.contact.*
import org.linphone.core.ChatRoomSecurityLevel
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.utils.ImageUtils
import org.linphone.utils.PermissionHelper

class ContactEditorViewModelFactory(private val friend: Friend?) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ContactEditorViewModel(friend) as T
    }
}

class ContactEditorViewModel(val c: Friend?) : ViewModel(), ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val coroutineScope: CoroutineScope = viewModelScope

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
        if (c != null) {
            contact.value = c!!
            displayName.value = c.name ?: ""
        } else {
            displayName.value = ""
        }

        organization.value = c?.organization ?: ""

        firstName.value = ""
        lastName.value = ""
        val vCard = c?.vcard
        if (vCard?.familyName.isNullOrEmpty() && vCard?.givenName.isNullOrEmpty()) {
            val refKey = c?.refKey
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

        updateNumbersAndAddresses()
    }

    fun save(): Friend {
        var contact = c
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
            if (sipAddress.isEmpty()) continue

            val parsed = coreContext.core.interpretUrl(sipAddress, false)
            if (parsed != null) contact.addAddress(parsed)
        }

        for (phone in contact.phoneNumbers) {
            contact.removePhoneNumber(phone)
        }
        for (phone in numbers.value.orEmpty()) {
            val phoneNumber = phone.newValue.value.orEmpty()
            if (phoneNumber.isEmpty()) continue

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

    private fun updateNumbersAndAddresses() {
        val phoneNumbers = arrayListOf<NumberOrAddressEditorData>()
        for (number in c?.phoneNumbers.orEmpty()) {
            phoneNumbers.add(NumberOrAddressEditorData(number, false))
        }
        if (phoneNumbers.isEmpty()) {
            phoneNumbers.add(NumberOrAddressEditorData("", false))
        }
        numbers.value = phoneNumbers

        val sipAddresses = arrayListOf<NumberOrAddressEditorData>()
        for (address in c?.addresses.orEmpty()) {
            sipAddresses.add(NumberOrAddressEditorData(address.asStringUriOnly(), true))
        }
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
                        c?.vcard?.givenName = givenName
                        firstName.value = givenName!!
                    }

                    val familyName: String? =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME))
                    if (!familyName.isNullOrEmpty()) {
                        c?.vcard?.familyName = familyName
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

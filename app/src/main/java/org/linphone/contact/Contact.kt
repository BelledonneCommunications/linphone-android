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

import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.PresenceBasicStatus
import org.linphone.core.tools.Log
import org.linphone.utils.ImageUtils

data class PhoneNumber(val value: String, val typeLabel: String) : Comparable<PhoneNumber> {
    override fun compareTo(other: PhoneNumber): Int {
        return value.compareTo(other.value)
    }
}

open class Contact : Comparable<Contact> {
    var fullName: String? = null
    var firstName: String? = null
    var lastName: String? = null
    var organization: String? = null
    var isStarred: Boolean = false

    var phoneNumbers = arrayListOf<PhoneNumber>()
    var rawPhoneNumbers = arrayListOf<String>()
    var sipAddresses = arrayListOf<Address>()
    // Raw SIP addresses are only used for contact edition
    var rawSipAddresses = arrayListOf<String>()

    var friend: Friend? = null

    private var thumbnailUri: Uri? = null

    override fun compareTo(other: Contact): Int {
        val fn = fullName ?: ""
        val otherFn = other.fullName ?: ""

        if (fn == otherFn) {
            if (phoneNumbers.size == other.phoneNumbers.size && phoneNumbers.size > 0) {
                if (phoneNumbers != other.phoneNumbers) {
                    for (i in 0 until phoneNumbers.size) {
                        val compare = phoneNumbers[i].compareTo(other.phoneNumbers[i])
                        if (compare != 0) return compare
                    }
                }
            } else {
                return phoneNumbers.size.compareTo(other.phoneNumbers.size)
            }

            if (sipAddresses.size == other.sipAddresses.size && sipAddresses.size > 0) {
                if (sipAddresses != other.sipAddresses) {
                    for (i in 0 until sipAddresses.size) {
                        val compare = sipAddresses[i].asStringUriOnly().compareTo(other.sipAddresses[i].asStringUriOnly())
                        if (compare != 0) return compare
                    }
                }
            } else {
                return sipAddresses.size.compareTo(other.sipAddresses.size)
            }

            val org = organization ?: ""
            val otherOrg = other.organization ?: ""
            return org.compareTo(otherOrg)
        }

        return coreContext.collator.compare(fn, otherFn)
    }

    @Synchronized
    fun syncValuesFromFriend() {
        val friend = this.friend
        friend ?: return

        phoneNumbers.clear()
        for (number in friend.phoneNumbers) {
            if (!rawPhoneNumbers.contains(number)) {
                phoneNumbers.add(PhoneNumber(number, ""))
                rawPhoneNumbers.add(number)
            }
        }

        sipAddresses.clear()
        rawSipAddresses.clear()
        for (address in friend.addresses) {
            val stringAddress = address.asStringUriOnly()
            if (!rawSipAddresses.contains(stringAddress)) {
                sipAddresses.add(address)
                rawSipAddresses.add(stringAddress)
            }
        }

        fullName = friend.name
        val vCard = friend.vcard
        if (vCard != null) {
            lastName = vCard.familyName
            firstName = vCard.givenName
            organization = vCard.organization
        }
    }

    @Synchronized
    open fun syncValuesFromAndroidCursor(cursor: Cursor) {
        Log.e("[Contact] Not a native contact, skip")
    }

    open fun getContactThumbnailPictureUri(): Uri? {
        return thumbnailUri
    }

    fun setContactThumbnailPictureUri(uri: Uri) {
        thumbnailUri = uri
    }

    open fun getContactPictureUri(): Uri? {
        return null
    }

    open fun getPerson(): Person {
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
        return personBuilder.build()
    }

    fun hasPresence(): Boolean {
        if (friend == null) return false
        for (address in sipAddresses) {
            val presenceModel = friend?.getPresenceModelForUriOrTel(address.asStringUriOnly())
            if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return true
        }
        for (number in rawPhoneNumbers) {
            val presenceModel = friend?.getPresenceModelForUriOrTel(number)
            if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return true
        }
        return false
    }

    fun getContactForPhoneNumberOrAddress(value: String): String? {
        val presenceModel = friend?.getPresenceModelForUriOrTel(value)
        if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return presenceModel.contact
        return null
    }

    override fun toString(): String {
        return "${super.toString()}: name [$fullName]"
    }
}

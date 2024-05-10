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
package org.linphone.ui.call.model

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import java.util.Random
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class ZrtpSasConfirmationDialogModel @UiThread constructor(
    authTokenToRead: String,
    private val authTokenToListen: String
) : GenericViewModel() {
    companion object {
        private const val TAG = "[ZRTP SAS Confirmation Dialog]"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    val message = MutableLiveData<String>()
    val letters1 = MutableLiveData<String>()
    val letters2 = MutableLiveData<String>()
    val letters3 = MutableLiveData<String>()
    val letters4 = MutableLiveData<String>()

    val trustVerified = MutableLiveData<Event<Boolean>>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    init {
        message.value = AppUtils.getFormattedString(
            R.string.call_dialog_zrtp_validate_trust_subtitle,
            authTokenToRead
        )

        // TODO FIXME: use SDK API when it will be available
        val rnd = Random()
        val randomLetters1 = "${ALPHABET[rnd.nextInt(ALPHABET.length)]}${ALPHABET[
            rnd.nextInt(
                ALPHABET.length
            )
        ]}"
        val randomLetters2 = "${ALPHABET[rnd.nextInt(ALPHABET.length)]}${ALPHABET[
            rnd.nextInt(
                ALPHABET.length
            )
        ]}"
        val randomLetters3 = "${ALPHABET[rnd.nextInt(ALPHABET.length)]}${ALPHABET[
            rnd.nextInt(
                ALPHABET.length
            )
        ]}"
        val randomLetters4 = "${ALPHABET[rnd.nextInt(ALPHABET.length)]}${ALPHABET[
            rnd.nextInt(
                ALPHABET.length
            )
        ]}"

        val correctLetters = rnd.nextInt(4)
        letters1.value = if (correctLetters == 0) authTokenToListen else randomLetters1
        letters2.value = if (correctLetters == 1) authTokenToListen else randomLetters2
        letters3.value = if (correctLetters == 2) authTokenToListen else randomLetters3
        letters4.value = if (correctLetters == 3) authTokenToListen else randomLetters4
    }

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }

    @UiThread
    fun lettersClicked(letters: MutableLiveData<String>) {
        val verified = letters.value == authTokenToListen
        Log.i(
            "$TAG User clicked on ${if (verified) "right" else "wrong"} letters"
        )
        trustVerified.value = Event(verified)
    }
}

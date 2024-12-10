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
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event

class ZrtpSasConfirmationDialogModel
    @UiThread
    constructor(
    authTokenToRead: String,
    authTokensToListen: List<String>,
    val cacheMismatch: Boolean
) : GenericViewModel() {
    companion object {
        private const val TAG = "[ZRTP SAS Confirmation Dialog]"
    }

    val localToken = MutableLiveData<String>()
    val letters1 = MutableLiveData<String>()
    val letters2 = MutableLiveData<String>()
    val letters3 = MutableLiveData<String>()
    val letters4 = MutableLiveData<String>()

    val authTokenClickedEvent = MutableLiveData<Event<String>>()

    val skipEvent = MutableLiveData<Event<Boolean>>()

    init {
        localToken.value = authTokenToRead
        letters1.value = authTokensToListen[0]
        letters2.value = authTokensToListen[1]
        letters3.value = authTokensToListen[2]
        letters4.value = authTokensToListen[3]
    }

    @UiThread
    fun skip() {
        skipEvent.value = Event(true)
    }

    @UiThread
    fun notFound() {
        Log.e("$TAG User clicked on 'Not Found' button!")
        authTokenClickedEvent.value = Event("")
    }

    @UiThread
    fun lettersClicked(letters: MutableLiveData<String>) {
        val token = letters.value.orEmpty()
        Log.i("$TAG User clicked on [$token] letters")
        authTokenClickedEvent.value = Event(token)
    }
}

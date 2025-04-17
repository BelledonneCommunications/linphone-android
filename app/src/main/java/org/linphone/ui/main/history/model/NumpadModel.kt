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
package org.linphone.ui.main.history.model

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

open class NumpadModel
    @UiThread
    constructor(
    private val inCallNumpad: Boolean,
    private val onDigitClicked: (value: String) -> (Unit),
    private val onVoicemailClicked: () -> (Unit),
    private val onBackspaceClicked: () -> (Unit),
    private val onCallClicked: () -> (Unit),
    private val onTransferCallClicked: () -> (Unit),
    private val onClearClicked: () -> (Unit)
) {
    companion object {
        private const val TAG = "[Numpad Model]"
    }

    val digits = MutableLiveData<String>()

    val voicemailAvailable = MutableLiveData<Boolean>()

    val showLetters = MutableLiveData<Boolean>()

    init {
        coreContext.postOnCoreThread {
            showLetters.postValue(corePreferences.showLettersOnDialpad)

            val account = LinphoneUtils.getDefaultAccount()
            val voicemailAddress = account?.params?.voicemailAddress
            voicemailAvailable.postValue(voicemailAddress != null)
        }
    }

    @UiThread
    fun onDigitClicked(value: String) {
        Log.i("$TAG Clicked on digit [$value]")
        onDigitClicked.invoke(value)

        if (value.isNotEmpty()) {
            coreContext.postOnCoreThread {
                coreContext.playDtmf(value[0], ignoreSystemPolicy = inCallNumpad)
            }
        }
    }

    @UiThread
    fun onDigitLongClicked(value: String): Boolean {
        if (value == "0") { // Special case
            Log.i("$TAG Long clicked on digit [$value]")
            onDigitClicked.invoke("+")
            return true
        }
        return false
    }

    @UiThread
    fun onVoicemailLongClicked(): Boolean {
        if (voicemailAvailable.value == true) {
            Log.i("$TAG Long clicked on voicemail icon")
            onVoicemailClicked.invoke()
            return true
        }
        return false
    }

    @UiThread
    fun onBackspaceClicked() {
        Log.i("$TAG Clicked on backspace")
        onBackspaceClicked.invoke()
    }

    @UiThread
    fun onBackspaceLongClicked(): Boolean {
        Log.i("$TAG Long clicked on backspace, clearing input")
        onClearClicked.invoke()
        return true
    }

    @UiThread
    fun onCallClicked() {
        Log.i("$TAG Starting call")
        onCallClicked.invoke()
    }

    @UiThread
    fun onBlindTransferClicked() {
        Log.i("$TAG Transferring call")
        onTransferCallClicked.invoke()
    }
}

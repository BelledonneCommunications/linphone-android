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
package org.linphone.ui.main.model

import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.RegistrationState
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

class AccountModel(
    private val account: Account,
    private val onMenuClicked: ((view: View, account: Account) -> Unit)? = null
) {
    val displayName = MutableLiveData<String>()

    val avatar = MutableLiveData<String>()

    val registrationState = MutableLiveData<String>()

    val isConnected = MutableLiveData<Boolean>()

    val inError = MutableLiveData<Boolean>()

    val isDefault = MutableLiveData<Boolean>()

    private val accountListener = object : AccountListenerStub() {
        @WorkerThread
        override fun onRegistrationStateChanged(
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            update()
        }
    }

    init {
        // Core thread
        account.addListener(accountListener)

        avatar.postValue(account.getPicturePath())

        update()
    }

    @WorkerThread
    fun destroy() {
        account.removeListener(accountListener)
    }

    @UiThread
    fun setAsDefault() {
        coreContext.postOnCoreThread { core ->
            core.defaultAccount = account
            isDefault.postValue(true)
        }
    }

    @UiThread
    fun openMenu(view: View) {
        onMenuClicked?.invoke(view, account)
    }

    @UiThread
    fun refreshRegister() {
        coreContext.postOnCoreThread { core ->
            core.refreshRegisters()
        }
    }

    @WorkerThread
    private fun update() {
        displayName.postValue(LinphoneUtils.getDisplayName(account.params.identityAddress))

        isDefault.postValue(coreContext.core.defaultAccount == account)

        val state = when (account.state) {
            RegistrationState.None, RegistrationState.Cleared -> "Disabled"
            RegistrationState.Progress -> "Connection..."
            RegistrationState.Failed -> "Error"
            RegistrationState.Ok -> "Connected"
            RegistrationState.Refreshing -> "Refreshing"
            else -> "${account.state}"
        }

        isConnected.postValue(account.state == RegistrationState.Ok)
        inError.postValue(account.state == RegistrationState.Failed)
        registrationState.postValue(state)
    }
}

fun Account.getPicturePath(): String {
    // TODO FIXME: get image path from account
    return FileUtils.getFileStoragePath(
        "john.jpg",
        isImage = true,
        overrideExisting = true
    ).absolutePath
}

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
@file:Suppress("SameReturnValue")

package org.linphone.ui.main.model

import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AbstractAvatarModel
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.MessageWaitingIndication
import org.linphone.core.RegistrationState
import org.linphone.core.SecurityLevel
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class AccountModel
    @WorkerThread
    constructor(
    val account: Account,
    private val onMenuClicked: ((view: View, account: Account) -> Unit)? = null
) : AbstractAvatarModel() {
    companion object {
        private const val TAG = "[Account Model]"
    }

    val displayName = MutableLiveData<String>()

    val registrationState = MutableLiveData<RegistrationState>()

    val registrationStateLabel = MutableLiveData<String>()

    val registrationStateSummary = MutableLiveData<String>()

    val isDefault = MutableLiveData<Boolean>()

    val notificationsCount = MutableLiveData<Int>()

    val showMwi = MutableLiveData<Boolean>()

    val voicemailCount = MutableLiveData<String>()

    private val accountListener = object : AccountListenerStub() {
        @WorkerThread
        override fun onRegistrationStateChanged(
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            Log.i(
                "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] registration state changed: [$state]($message)"
            )
            update()
        }

        override fun onMessageWaitingIndicationChanged(
            account: Account,
            mwi: MessageWaitingIndication
        ) {
            Log.i(
                "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] has received a MWI NOTIFY. ${if (mwi.hasMessageWaiting()) "Message(s) are waiting." else "No message is waiting."}}"
            )
            showMwi.postValue(mwi.hasMessageWaiting())
            for (summary in mwi.summaries) {
                val context = summary.contextClass
                val nbNew = summary.nbNew
                val nbNewUrgent = summary.nbNewUrgent
                val nbOld = summary.nbOld
                val nbOldUrgent = summary.nbOldUrgent
                Log.i(
                    "$TAG [MWI] [$context]: new [$nbNew] urgent ($nbNewUrgent), old [$nbOld] urgent ($nbOldUrgent)"
                )

                voicemailCount.postValue(nbNew.toString())
            }
        }
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            computeNotificationsCount()
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            computeNotificationsCount()
        }
    }

    init {
        account.addListener(accountListener)
        coreContext.core.addListener(coreListener)

        isDefault.postValue(false)
        presenceStatus.postValue(ConsolidatedPresence.Offline)
        showMwi.postValue(false)
        voicemailCount.postValue("")

        update()
    }

    @WorkerThread
    fun destroy() {
        coreContext.core.removeListener(coreListener)
        account.removeListener(accountListener)
    }

    @UiThread
    fun setAsDefault() {
        coreContext.postOnCoreThread { core ->
            if (core.defaultAccount != account) {
                core.defaultAccount = account

                for (friendList in core.friendsLists) {
                    if (friendList.isSubscriptionsEnabled) {
                        Log.i(
                            "$TAG Default account has changed, refreshing friend list [${friendList.displayName}] subscriptions"
                        )
                        // friendList.updateSubscriptions() won't trigger a refresh unless a friend has changed
                        friendList.isSubscriptionsEnabled = false
                        friendList.isSubscriptionsEnabled = true
                    }
                }
            }
        }

        isDefault.value = true
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

    @UiThread
    fun callVoicemailUri() {
        coreContext.postOnCoreThread {
            val voicemail = account.params.voicemailAddress
            if (voicemail != null) {
                Log.i("$TAG Calling voicemail address [${voicemail.asStringUriOnly()}]")
                coreContext.startAudioCall(voicemail)
            }
        }
    }

    @WorkerThread
    fun computeNotificationsCount() {
        notificationsCount.postValue(account.unreadChatMessageCount + account.missedCallsCount)
    }

    @WorkerThread
    fun updateRegistrationState() {
        val state = if (account.state == RegistrationState.None) {
            // If the account has been disabled manually, use the Cleared status instead of None
            if (!account.params.isRegisterEnabled) {
                Log.w(
                    "$TAG Account real registration state is None but using Cleared instead as it was manually disabled by the user"
                )
                RegistrationState.Cleared
            } else {
                account.state
            }
        } else {
            account.state
        }
        registrationState.postValue(state)

        val label = when (state) {
            RegistrationState.Cleared -> {
                AppUtils.getString(
                    R.string.drawer_menu_account_connection_status_cleared
                )
            }
            RegistrationState.Progress -> AppUtils.getString(
                R.string.drawer_menu_account_connection_status_progress
            )
            RegistrationState.Failed -> {
                AppUtils.getString(
                    R.string.drawer_menu_account_connection_status_failed
                )
            }
            RegistrationState.Ok -> {
                AppUtils.getString(
                    R.string.drawer_menu_account_connection_status_connected
                )
            }
            RegistrationState.None -> {
                AppUtils.getString(
                    R.string.drawer_menu_account_connection_status_disconnected
                )
            }
            RegistrationState.Refreshing -> AppUtils.getString(
                R.string.drawer_menu_account_connection_status_refreshing
            )
            else -> "$state"
        }
        registrationStateLabel.postValue(label)
        Log.i("$TAG Account registration state is [$state]")

        val summary = when (state) {
            RegistrationState.Cleared -> AppUtils.getString(
                R.string.manage_account_status_cleared_summary
            )
            RegistrationState.Refreshing, RegistrationState.Progress -> AppUtils.getString(
                R.string.manage_account_status_progress_summary
            )
            RegistrationState.Failed -> AppUtils.getString(
                R.string.manage_account_status_failed_summary
            )
            RegistrationState.Ok -> AppUtils.getString(
                R.string.manage_account_status_connected_summary
            )
            RegistrationState.None -> AppUtils.getString(
                R.string.manage_account_status_disconnected_summary
            )
            else -> "$state"
        }
        registrationStateSummary.postValue(summary)
    }

    @WorkerThread
    private fun update() {
        Log.i(
            "$TAG Refreshing info for account [${account.params.identityAddress?.asStringUriOnly()}]"
        )

        trust.postValue(SecurityLevel.EndToEndEncryptedAndVerified)
        showTrust.postValue(isEndToEndEncryptionMandatory())

        val name = LinphoneUtils.getDisplayName(account.params.identityAddress)
        displayName.postValue(name)

        initials.postValue(AppUtils.getInitials(name))

        val pictureUri = account.params.pictureUri.orEmpty()
        if (pictureUri != picturePath.value.orEmpty()) {
            picturePath.postValue(pictureUri)
            Log.d("$TAG Account picture URI is [$pictureUri]")
        }

        isDefault.postValue(coreContext.core.defaultAccount == account)
        computeNotificationsCount()

        updateRegistrationState()
    }
}

@WorkerThread
fun isEndToEndEncryptionMandatory(): Boolean {
    return false // TODO: Will be done later in SDK
}

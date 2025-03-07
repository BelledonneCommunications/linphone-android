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
package org.linphone.activities.main.viewmodels

import PresenceEventData
import androidx.databinding.ObservableField
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.GsonBuilder
import io.reactivex.rxjava3.disposables.Disposable
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.*
import org.linphone.models.realtime.PresenceIconState
import org.linphone.services.PresenceService
import org.linphone.services.UserService
import org.linphone.utils.Log
import org.linphone.utils.Optional

open class StatusViewModel : ViewModel() {
    val registrationStatusText = MutableLiveData<Int>()

    val registrationStatusDrawable = MutableLiveData<Int>()

    val voiceMailCount = MutableLiveData<Int>()

    val defaultAccountFound = MutableLiveData<Boolean>()

    val presenceStatus = MutableLiveData<ConsolidatedPresence>()

    val dimensionsPresenceStatus = MutableLiveData<ConsolidatedPresence>()

    val userImageUrl = ObservableField<String>()

    private var userSubscription: Disposable? = null
    private var presenceSubscription: Disposable? = null

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            if (account == core.defaultAccount) {
                updateDefaultAccountRegistrationStatus(state)
            } else if (core.accountList.isEmpty()) {
                // Update registration status when default account is removed
                registrationStatusText.value = getStatusIconText(state)
                registrationStatusDrawable.value = getStatusIconResource(state)
            }
        }

        override fun onNotifyReceived(
            core: Core,
            event: Event,
            notifiedEvent: String,
            body: Content?
        ) {
            if (body?.type == "application" && body.subtype == "simple-message-summary" && body.size > 0) {
                val data = body.utf8Text?.lowercase(Locale.getDefault())
                val voiceMail = data?.split("voice-message: ")
                if ((voiceMail?.size ?: 0) >= 2) {
                    val toParse = voiceMail!![1].split("/", limit = 0)
                    try {
                        val unreadCount: Int = toParse[0].toInt()
                        voiceMailCount.value = unreadCount
                    } catch (nfe: NumberFormatException) {
                        Log.e("[Status Fragment] $nfe")
                    }
                }
            }
        }
    }

    init {
        val core = coreContext.core
        core.addListener(listener)

        var state: RegistrationState = RegistrationState.None

        val defaultAccount = core.defaultAccount
        if (defaultAccount != null) {
            state = defaultAccount.state
        }

        updateDefaultAccountRegistrationStatus(state)

        val userSvc = UserService.getInstance(coreContext.context)
        userSubscription = userSvc.user
            .subscribe(
                { u ->
                    Log.i("Userinfo: " + GsonBuilder().create().toJson(u))
                    // viewModel.user.set(u)
                    userImageUrl.set(u.profileImageUrl.replace("_36.png", "_128.png"))
                },
                { error -> Log.e("userSubscription", error) }
            )

        refreshConsolidatedPresence()

        presenceSubscription = PresenceService.getInstance(coreContext.context).currentUserPresence.subscribe(
            { p: Any ->
                Log.i("presenceInfo: " + GsonBuilder().create().toJson(p))

                if (p is Optional<*>) {
                    if (p.isPresent()) {
                        val eventData = p.get()
                        if (eventData is PresenceEventData) {
                            dimensionsPresenceStatus.postValue(
                                PresenceIconState.toConsolidatedPresence(
                                    PresenceIconState.fromString(eventData.iconState)
                                )
                            )
                        }
                    } else {
                        dimensionsPresenceStatus.postValue(
                            PresenceIconState.toConsolidatedPresence(
                                null
                            )
                        )
                    }
                }
            },
            { error ->
                Log.e("presenceSubscription", error)
            }
        )
    }

    fun refreshConsolidatedPresence() {
        presenceStatus.value = coreContext.core.consolidatedPresence
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun refreshRegister() {
        coreContext.core.refreshRegisters()
    }

    fun dialVoicemail() {
        coreContext.dialVoicemail()
    }

    fun updateDefaultAccountRegistrationStatus(state: RegistrationState) {
        registrationStatusText.value = getStatusIconText(state)
        registrationStatusDrawable.value = getStatusIconResource(state)
        defaultAccountFound.value = true
    }

    private fun getStatusIconText(state: RegistrationState): Int {
        return when (state) {
            RegistrationState.Ok -> R.string.status_connected
            RegistrationState.Progress, RegistrationState.Refreshing -> R.string.status_in_progress
            RegistrationState.Failed -> R.string.status_error
            else -> R.string.status_not_connected
        }
    }

    private fun getStatusIconResource(state: RegistrationState): Int {
        return when (state) {
            RegistrationState.Ok -> R.drawable.led_registered
            RegistrationState.Progress, RegistrationState.Refreshing -> R.drawable.led_registration_in_progress
            RegistrationState.Failed -> R.drawable.led_error
            else -> R.drawable.led_not_registered
        }
    }

    fun showDeviceMissing() {
        registrationStatusText.value = R.string.no_device_configured
        registrationStatusDrawable.value = R.drawable.led_error
    }
}

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
package org.linphone.ui.voip.viewmodel

import android.animation.ValueAnimator
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CallViewModel() : ViewModel() {
    companion object {
        const val TAG = "[Call ViewModel]"
    }

    val contact = MutableLiveData<ContactModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isActionsMenuExpanded = MutableLiveData<Boolean>()

    val extraActionsMenuTranslateY = MutableLiveData<Float>()

    private val extraActionsMenuHeight = coreContext.context.resources.getDimension(
        R.dimen.in_call_extra_actions_menu_height
    )
    private val extraButtonsMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(
            extraActionsMenuHeight,
            0f
        ).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                extraActionsMenuTranslateY.value = value
            }
            duration = 500
        }
    }

    val toggleExtraActionMenuVisibilityEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var call: Call

    init {
        isVideoEnabled.value = false
        isActionsMenuExpanded.value = false
        extraActionsMenuTranslateY.value = extraActionsMenuHeight

        coreContext.postOnCoreThread { core ->
            val currentCall = core.currentCall ?: core.calls.firstOrNull()

            if (currentCall != null) {
                call = currentCall
                Log.i("$TAG Found call [$call]")

                if (call.state == Call.State.StreamsRunning) {
                    isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
                } else {
                    isVideoEnabled.postValue(call.params.isVideoEnabled)
                }
                isOutgoing.postValue(call.dir == Call.Dir.Outgoing)

                val address = call.remoteAddress
                address.clean()
                displayedAddress.postValue(address.asStringUriOnly())

                val friend = core.findFriend(address)
                if (friend != null) {
                    displayedName.postValue(friend.name)
                    contact.postValue(ContactModel(friend))
                } else {
                    displayedName.postValue(LinphoneUtils.getDisplayName(address))
                }
            } else {
                Log.e("$TAG Failed to find outgoing call!")
            }
        }
    }

    fun hangUp() {
        // UI thread
        coreContext.postOnCoreThread {
            Log.i("$TAG Terminating call [$call]")
            call.terminate()
        }
    }

    fun toggleExpandActionsMenu() {
        // UI thread
        isActionsMenuExpanded.value = isActionsMenuExpanded.value == false

        if (isActionsMenuExpanded.value == true) {
            extraButtonsMenuAnimator.start()
        } else {
            extraButtonsMenuAnimator.reverse()
        }
        // toggleExtraActionMenuVisibilityEvent.value = Event(isActionsMenuExpanded.value == true)
    }
}

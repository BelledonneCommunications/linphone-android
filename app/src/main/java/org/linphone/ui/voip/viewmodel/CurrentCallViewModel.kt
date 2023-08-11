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
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.MediaEncryption
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CurrentCallViewModel() : ViewModel() {
    companion object {
        const val TAG = "[Current Call ViewModel]"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isRemoteDeviceTrusted = MutableLiveData<Boolean>()

    val showZrtpSasDialogEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    val callDuration = MutableLiveData<Int>()

    // Extras actions

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

    private val callListener = object : CallListenerStub() {
        override fun onEncryptionChanged(call: Call, on: Boolean, authenticationToken: String?) {
            updateEncryption()
        }
    }

    init {
        isVideoEnabled.value = false
        isMicrophoneMuted.value = false
        isActionsMenuExpanded.value = false
        extraActionsMenuTranslateY.value = extraActionsMenuHeight

        coreContext.postOnCoreThread { core ->
            val currentCall = core.currentCall ?: core.calls.firstOrNull()

            if (currentCall != null) {
                call = currentCall
                Log.i("$TAG Found call [$call]")
                configureCall(call)
            } else {
                Log.e("$TAG Failed to find outgoing call!")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.removeListener(callListener)
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

    fun updateZrtpSas(verified: Boolean) {
        // UI thread
        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.authenticationTokenVerified = verified
            }
        }
    }

    fun toggleMuteMicrophone() {
        // UI thread
        // TODO: check record audio permission
        coreContext.postOnCoreThread {
            call.microphoneMuted = !call.microphoneMuted
            isMicrophoneMuted.postValue(call.microphoneMuted)
        }
    }

    fun changeAudioOutputDevice() {
        // UI thread
        // TODO: display list of all output devices
    }

    fun toggleVideo() {
        // UI thread
        // TODO: check video permission

        // TODO
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

    fun forceShowZrtpSasDialog() {
        val authToken = call.authenticationToken
        if (authToken.orEmpty().isNotEmpty()) {
            showZrtpSasDialog(authToken!!.uppercase(Locale.getDefault()))
        }
    }

    private fun showZrtpSasDialog(authToken: String) {
        val toRead: String
        val toListen: String
        when (call.dir) {
            Call.Dir.Incoming -> {
                toRead = authToken.substring(0, 2)
                toListen = authToken.substring(2)
            }
            else -> {
                toRead = authToken.substring(2)
                toListen = authToken.substring(0, 2)
            }
        }
        showZrtpSasDialogEvent.postValue(Event(Pair(toRead, toListen)))
    }

    private fun updateEncryption() {
        // Core thread
        when (call.currentParams.mediaEncryption) {
            MediaEncryption.ZRTP -> {
                val authToken = call.authenticationToken
                val deviceIsTrusted = call.authenticationTokenVerified && authToken != null
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is ${if (deviceIsTrusted) "trusted" else "not trusted yet"}"
                )
                isRemoteDeviceTrusted.postValue(deviceIsTrusted)

                if (!deviceIsTrusted && authToken.orEmpty().isNotEmpty()) {
                    Log.i("$TAG Showing ZRTP SAS confirmation dialog")
                    showZrtpSasDialog(authToken!!.uppercase(Locale.getDefault()))
                }
            }
            MediaEncryption.SRTP, MediaEncryption.DTLS -> {
            }
            else -> {
            }
        }
    }

    private fun configureCall(call: Call) {
        // Core thread
        call.addListener(callListener)

        if (LinphoneUtils.isCallOutgoing(call.state)) {
            isVideoEnabled.postValue(call.params.isVideoEnabled)
        } else {
            isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
        }

        isMicrophoneMuted.postValue(call.microphoneMuted)
        isOutgoing.postValue(call.dir == Call.Dir.Outgoing)

        val address = call.remoteAddress
        address.clean()
        displayedAddress.postValue(address.asStringUriOnly())

        val friend = call.core.findFriend(address)
        if (friend != null) {
            displayedName.postValue(friend.name)
            contact.postValue(ContactAvatarModel(friend))
        } else {
            displayedName.postValue(LinphoneUtils.getDisplayName(address))
        }

        updateEncryption()
        callDuration.postValue(call.duration)
    }
}

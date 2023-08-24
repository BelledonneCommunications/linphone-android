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

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CurrentCallViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Current Call ViewModel]"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    // To synchronize chronometers in UI
    val callDuration = MutableLiveData<Int>()

    // ZRTP related

    val isRemoteDeviceTrusted = MutableLiveData<Boolean>()

    val showZrtpSasDialogEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

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

    private lateinit var call: Call

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onEncryptionChanged(call: Call, on: Boolean, authenticationToken: String?) {
            updateEncryption()
        }

        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State?, message: String) {
            if (LinphoneUtils.isCallOutgoing(call.state)) {
                isVideoEnabled.postValue(call.params.isVideoEnabled)
            } else {
                val videoEnabled = call.currentParams.isVideoEnabled
                isVideoEnabled.postValue(videoEnabled)

                // Toggle full screen OFF when remote disables video
                if (!videoEnabled && fullScreenMode.value == true) {
                    fullScreenMode.postValue(false)
                }
            }
        }
    }

    init {
        isVideoEnabled.value = false
        isMicrophoneMuted.value = false
        fullScreenMode.value = false
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

            showSwitchCamera.postValue(coreContext.showSwitchCameraButton())
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.removeListener(callListener)
            }
        }
    }

    @UiThread
    fun answer() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Answering call [$call]")
            call.accept()
        }
    }

    @UiThread
    fun hangUp() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Terminating call [$call]")
            call.terminate()
        }
    }

    @UiThread
    fun updateZrtpSas(verified: Boolean) {
        coreContext.postOnCoreThread {
            if (::call.isInitialized) {
                call.authenticationTokenVerified = verified
            }
        }
    }

    @UiThread
    fun toggleMuteMicrophone() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: request record audio permission
            return
        }
        coreContext.postOnCoreThread {
            call.microphoneMuted = !call.microphoneMuted
            isMicrophoneMuted.postValue(call.microphoneMuted)
        }
    }

    @UiThread
    fun changeAudioOutputDevice() {
        // TODO: display list of all output devices
    }

    @UiThread
    fun toggleVideo() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: request video permission
            return
        }

        coreContext.postOnCoreThread { core ->
            if (::call.isInitialized) {
                val params = core.createCallParams(call)
                if (call.conference != null) {
                    if (params?.isVideoEnabled == false) {
                        params.isVideoEnabled = true
                        params.videoDirection = MediaDirection.SendRecv
                    } else {
                        if (params?.videoDirection == MediaDirection.SendRecv || params?.videoDirection == MediaDirection.SendOnly) {
                            params.videoDirection = MediaDirection.RecvOnly
                        } else {
                            params?.videoDirection = MediaDirection.SendRecv
                        }
                    }
                } else {
                    params?.isVideoEnabled = params?.isVideoEnabled == false
                    Log.i(
                        "$TAG Updating call with video enabled set to ${params?.isVideoEnabled}"
                    )
                }
                call.update(params)
            }
        }
    }

    @UiThread
    fun switchCamera() {
        coreContext.postOnCoreThread {
            coreContext.switchCamera()
        }
    }

    @UiThread
    fun toggleFullScreen() {
        if (fullScreenMode.value == false && isVideoEnabled.value == false) return
        fullScreenMode.value = fullScreenMode.value != true
    }

    @UiThread
    fun toggleExpandActionsMenu() {
        isActionsMenuExpanded.value = isActionsMenuExpanded.value == false

        if (isActionsMenuExpanded.value == true) {
            extraButtonsMenuAnimator.start()
        } else {
            extraButtonsMenuAnimator.reverse()
        }
    }

    @WorkerThread
    fun forceShowZrtpSasDialog() {
        val authToken = call.authenticationToken
        if (authToken.orEmpty().isNotEmpty()) {
            showZrtpSasDialog(authToken!!.uppercase(Locale.getDefault()))
        }
    }

    @WorkerThread
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

    @WorkerThread
    private fun updateEncryption() {
        when (call.currentParams.mediaEncryption) {
            MediaEncryption.ZRTP -> {
                val authToken = call.authenticationToken
                val deviceIsTrusted = call.authenticationTokenVerified && authToken != null
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is ${if (deviceIsTrusted) "trusted" else "not trusted yet"}"
                )
                isRemoteDeviceTrusted.postValue(deviceIsTrusted)
                contact.value?.showTrust?.postValue(deviceIsTrusted)

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

    @WorkerThread
    private fun configureCall(call: Call) {
        call.addListener(callListener)

        if (LinphoneUtils.isCallOutgoing(call.state)) {
            isVideoEnabled.postValue(call.params.isVideoEnabled)
        } else {
            isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
        }

        isMicrophoneMuted.postValue(call.microphoneMuted)
        isOutgoing.postValue(call.dir == Call.Dir.Outgoing)

        val address = call.remoteAddress.clone()
        address.clean()
        displayedAddress.postValue(address.asStringUriOnly())

        val friend = call.core.findFriend(address)
        if (friend != null) {
            displayedName.postValue(friend.name)
            contact.postValue(ContactAvatarModel(friend))
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = LinphoneUtils.getDisplayName(address)
            fakeFriend.addAddress(address)
            contact.postValue(ContactAvatarModel(fakeFriend))
            displayedName.postValue(fakeFriend.name)
        }

        updateEncryption()
        callDuration.postValue(call.duration)
    }
}

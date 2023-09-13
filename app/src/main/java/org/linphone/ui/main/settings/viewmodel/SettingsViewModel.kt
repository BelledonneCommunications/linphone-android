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
package org.linphone.ui.main.settings.viewmodel

import android.os.Vibrator
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log

class SettingsViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Settings ViewModel]"
    }

    val expandCalls = MutableLiveData<Boolean>()
    val expandNetwork = MutableLiveData<Boolean>()
    val expandUserInterface = MutableLiveData<Boolean>()

    // Calls settings
    val echoCancellerEnabled = MutableLiveData<Boolean>()
    val routeAudioToBluetooth = MutableLiveData<Boolean>()
    val videoEnabled = MutableLiveData<Boolean>()
    val autoInitiateVideoCalls = MutableLiveData<Boolean>()
    val autoAcceptVideoRequests = MutableLiveData<Boolean>()
    val useDeviceRingtone = MutableLiveData<Boolean>()
    val vibrateDuringIncomingCall = MutableLiveData<Boolean>()
    val autoRecordCalls = MutableLiveData<Boolean>()

    // Network settings
    val useWifiOnly = MutableLiveData<Boolean>()

    // User Interface settings

    val isVibrationAvailable = MutableLiveData<Boolean>()

    init {
        expandCalls.value = true
        expandNetwork.value = true
        expandUserInterface.value = false

        val vibrator = coreContext.context.getSystemService(Vibrator::class.java)
        isVibrationAvailable.value = vibrator.hasVibrator()
        if (isVibrationAvailable.value == false) {
            Log.w("$TAG Device doesn't seem to have a vibrator, hiding related setting")
        }

        coreContext.postOnCoreThread { core ->
            echoCancellerEnabled.postValue(core.isEchoCancellationEnabled)
            routeAudioToBluetooth.postValue(corePreferences.routeAudioToBluetoothIfAvailable)
            videoEnabled.postValue(core.isVideoEnabled)
            autoInitiateVideoCalls.postValue(core.videoActivationPolicy.automaticallyInitiate)
            autoAcceptVideoRequests.postValue(core.videoActivationPolicy.automaticallyAccept)
            useDeviceRingtone.postValue(core.ring == null)
            vibrateDuringIncomingCall.postValue(core.isVibrationOnIncomingCallEnabled)
            autoRecordCalls.postValue(corePreferences.automaticallyStartCallRecording)

            useWifiOnly.postValue(core.isWifiOnlyEnabled)
        }
    }

    @UiThread
    fun toggleEchoCanceller() {
        val newValue = echoCancellerEnabled.value == false
        coreContext.postOnCoreThread { core ->
            core.isEchoCancellationEnabled = newValue
            echoCancellerEnabled.postValue(newValue)
        }
    }

    @UiThread
    fun toggleRouteAudioToBluetooth() {
        val newValue = routeAudioToBluetooth.value == false
        coreContext.postOnCoreThread {
            corePreferences.routeAudioToBluetoothIfAvailable = newValue
            routeAudioToBluetooth.postValue(newValue)
        }
    }

    @UiThread
    fun toggleEnableVideo() {
        val newValue = videoEnabled.value == false
        coreContext.postOnCoreThread { core ->
            core.isVideoCaptureEnabled = newValue
            core.isVideoDisplayEnabled = newValue
            videoEnabled.postValue(newValue)
        }
    }

    @UiThread
    fun toggleAutoInitiateVideoCalls() {
        val newValue = autoInitiateVideoCalls.value == false
        coreContext.postOnCoreThread { core ->
            val policy = core.videoActivationPolicy
            policy.automaticallyInitiate = newValue
            core.videoActivationPolicy = policy
            autoInitiateVideoCalls.postValue(newValue)
        }
    }

    @UiThread
    fun toggleAutoAcceptVideoRequests() {
        val newValue = autoAcceptVideoRequests.value == false
        coreContext.postOnCoreThread { core ->
            val policy = core.videoActivationPolicy
            policy.automaticallyAccept = newValue
            core.videoActivationPolicy = policy
            autoAcceptVideoRequests.postValue(newValue)
        }
    }

    @UiThread
    fun toggleDeviceRingtone() {
        val newValue = useDeviceRingtone.value == false
        coreContext.postOnCoreThread { core ->
            core.ring = if (newValue) null else corePreferences.defaultRingtonePath
            useDeviceRingtone.postValue(newValue)
        }
    }

    @UiThread
    fun toggleVibrateOnIncomingCalls() {
        val newValue = vibrateDuringIncomingCall.value == false
        coreContext.postOnCoreThread { core ->
            core.isVibrationOnIncomingCallEnabled = newValue
            vibrateDuringIncomingCall.postValue(newValue)
        }
    }

    @UiThread
    fun toggleAutoRecordCall() {
        val newValue = autoRecordCalls.value == false
        coreContext.postOnCoreThread {
            corePreferences.automaticallyStartCallRecording = newValue
            autoRecordCalls.postValue(newValue)
        }
    }

    @UiThread
    fun toggleUseWifiOnly() {
        val newValue = useWifiOnly.value == false
        coreContext.postOnCoreThread { core ->
            core.isWifiOnlyEnabled = newValue
            useWifiOnly.postValue(newValue)
        }
    }

    @UiThread
    fun toggleCallsExpand() {
        expandCalls.value = expandCalls.value == false
    }

    @UiThread
    fun toggleNetworkExpand() {
        expandNetwork.value = expandNetwork.value == false
    }

    @UiThread
    fun toggleUserInterfaceExpand() {
        expandUserInterface.value = expandUserInterface.value == false
    }
}

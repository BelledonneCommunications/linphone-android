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

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioRouteUtils

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

    val availableRingtonesPaths = arrayListOf<String>()
    val availableRingtonesNames = arrayListOf<String>()
    val selectedRingtone = MutableLiveData<String>()
    val isRingtonePlaying = MutableLiveData<Boolean>()

    val isVibrationAvailable = MutableLiveData<Boolean>()
    val vibrateDuringIncomingCall = MutableLiveData<Boolean>()

    val autoRecordCalls = MutableLiveData<Boolean>()

    // Network settings
    val useWifiOnly = MutableLiveData<Boolean>()

    // User Interface settings

    val theme = MutableLiveData<Int>()
    val availableThemesNames = arrayListOf<String>()
    val availableThemesValues = arrayListOf(-1, 0, 1)

    // Other

    private lateinit var ringtonePlayer: Player
    private lateinit var deviceRingtonePlayer: Ringtone

    private val playerListener = PlayerListener {
        Log.i("[$TAG] End of ringtone reached")
        stopRingtonePlayer()
    }

    init {
        expandCalls.value = false
        expandNetwork.value = false
        expandUserInterface.value = false

        val vibrator = coreContext.context.getSystemService(Vibrator::class.java)
        isVibrationAvailable.value = vibrator.hasVibrator()
        if (isVibrationAvailable.value == false) {
            Log.w("$TAG Device doesn't seem to have a vibrator, hiding related setting")
        }

        computeAvailableRingtones()

        availableThemesNames.add(
            AppUtils.getString(R.string.settings_user_interface_auto_theme_label)
        )
        availableThemesNames.add(
            AppUtils.getString(R.string.settings_user_interface_light_theme_label)
        )
        availableThemesNames.add(
            AppUtils.getString(R.string.settings_user_interface_dark_theme_label)
        )

        coreContext.postOnCoreThread { core ->
            echoCancellerEnabled.postValue(core.isEchoCancellationEnabled)
            routeAudioToBluetooth.postValue(corePreferences.routeAudioToBluetoothIfAvailable)
            videoEnabled.postValue(core.isVideoEnabled)
            autoInitiateVideoCalls.postValue(core.videoActivationPolicy.automaticallyInitiate)
            autoAcceptVideoRequests.postValue(core.videoActivationPolicy.automaticallyAccept)
            vibrateDuringIncomingCall.postValue(core.isVibrationOnIncomingCallEnabled)
            autoRecordCalls.postValue(corePreferences.automaticallyStartCallRecording)

            useWifiOnly.postValue(core.isWifiOnlyEnabled)

            val ringtone = core.ring.orEmpty()
            Log.i("Currently configured ringtone in Core is [$ringtone]")
            selectedRingtone.postValue(ringtone)

            theme.postValue(corePreferences.darkMode)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::ringtonePlayer.isInitialized) {
                stopRingtonePlayer()
                ringtonePlayer.removeListener(playerListener)
            }
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
    fun setRingtone(ringtone: String) {
        coreContext.postOnCoreThread { core ->
            core.ring = ringtone
            selectedRingtone.postValue(ringtone)

            if (::ringtonePlayer.isInitialized) {
                if (ringtonePlayer.state == Player.State.Playing) {
                    stopRingtonePlayer()
                }
            }
        }
    }

    @UiThread
    fun playPauseRingtone() {
        coreContext.postOnCoreThread { core ->
            if (!::ringtonePlayer.isInitialized) {
                // Also works for ringtone
                val playbackDevice = AudioRouteUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
                val player = core.createLocalPlayer(playbackDevice, null, null)
                ringtonePlayer = player ?: return@postOnCoreThread
                ringtonePlayer.addListener(playerListener)
            }

            val path = core.ring.orEmpty()
            if (path.isEmpty()) {
                if (::deviceRingtonePlayer.isInitialized) {
                    if (deviceRingtonePlayer.isPlaying) {
                        deviceRingtonePlayer.stop()
                        isRingtonePlaying.postValue(false)
                    } else {
                        playDeviceDefaultRingtone()
                    }
                } else {
                    playDeviceDefaultRingtone()
                }
            } else {
                if (ringtonePlayer.state == Player.State.Playing) {
                    stopRingtonePlayer()
                } else {
                    if (ringtonePlayer.open(path) == 0) {
                        if (ringtonePlayer.start() == 0) {
                            isRingtonePlaying.postValue(true)
                        } else {
                            Log.e("$TAG Failed to play ringtone [$path]")
                        }
                    } else {
                        Log.e("$TAG Failed to open ringtone [$path]")
                    }
                }
            }
        }
    }

    @WorkerThread
    private fun playDeviceDefaultRingtone() {
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val defaultRingtoneUri = getDefaultRingtoneUri(coreContext.context)
        try {
            val ringtone = RingtoneManager.getRingtone(coreContext.context, defaultRingtoneUri)
            if (ringtone != null) {
                ringtone.audioAttributes = audioAttrs
                ringtone.isLooping = true
                ringtone.play()
                deviceRingtonePlayer = ringtone
                isRingtonePlaying.postValue(true)
            } else {
                Log.e("$TAG Couldn't retrieve Ringtone object from manager!")
            }
        } catch (e: Exception) {
            Log.e("$TAG Failed to play ringtone [", defaultRingtoneUri, "] : ", e)
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

    @UiThread
    fun setTheme(theme: Int) {
        corePreferences.darkMode = theme
    }

    @WorkerThread
    fun stopRingtonePlayer() {
        if (::ringtonePlayer.isInitialized && ringtonePlayer.state != Player.State.Closed) {
            Log.i("$TAG Stopping ringtone player")
            ringtonePlayer.pause()
            ringtonePlayer.seek(0)
            ringtonePlayer.close()
            isRingtonePlaying.postValue(false)
        }
    }

    @UiThread
    private fun computeAvailableRingtones() {
        availableRingtonesNames.add(
            AppUtils.getString(R.string.settings_calls_use_device_ringtone_label)
        )
        availableRingtonesPaths.add("")

        val directory = File(corePreferences.ringtonesPath)
        val files = directory.listFiles()
        for (ringtone in files.orEmpty()) {
            if (ringtone.absolutePath.endsWith(".mkv")) {
                val name = ringtone.name
                    .substringBefore(".")
                    .replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                availableRingtonesNames.add(name)
                availableRingtonesPaths.add(ringtone.absolutePath)
            }
        }
    }

    private fun getDefaultRingtoneUri(context: Context): Uri? {
        var uri: Uri? = null
        try {
            uri =
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        } catch (exception: SecurityException) {
        }
        if (uri == null) {
            Log.w("$TAG Failed to get actual default ringtone URI, trying to get a valid one")
            uri = RingtoneManager.getValidRingtoneUri(context)
        }
        if (uri == null) {
            Log.w("$TAG Failed to get a valid ringtone URI, trying the first one available")
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(RingtoneManager.TYPE_RINGTONE)
            val cursor = ringtoneManager.cursor
            if (cursor.moveToFirst()) {
                val idString = cursor.getString(RingtoneManager.ID_COLUMN_INDEX)
                val uriString = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                uri = Uri.parse("$uriString/$idString")
            }
            cursor.close()
        }
        return uri
    }
}

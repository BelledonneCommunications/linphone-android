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
import org.linphone.core.Conference
import org.linphone.core.FriendList
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.VFS
import org.linphone.core.tools.Log
import org.linphone.ui.main.settings.model.CardDavLdapModel
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event

class SettingsViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Settings ViewModel]"
    }

    val expandSecurity = MutableLiveData<Boolean>()
    val expandCalls = MutableLiveData<Boolean>()
    val expandConversations = MutableLiveData<Boolean>()
    val expandContacts = MutableLiveData<Boolean>()
    val expandMeetings = MutableLiveData<Boolean>()
    val expandNetwork = MutableLiveData<Boolean>()
    val expandUserInterface = MutableLiveData<Boolean>()

    // Security settings
    val isVfsEnabled = MutableLiveData<Boolean>()

    // Calls settings
    val hideVideoCallSetting = MutableLiveData<Boolean>()

    val echoCancellerEnabled = MutableLiveData<Boolean>()
    val routeAudioToBluetooth = MutableLiveData<Boolean>()
    val videoEnabled = MutableLiveData<Boolean>()

    val availableRingtonesPaths = arrayListOf<String>()
    val availableRingtonesNames = arrayListOf<String>()
    val selectedRingtone = MutableLiveData<String>()
    val isRingtonePlaying = MutableLiveData<Boolean>()

    val isVibrationAvailable = MutableLiveData<Boolean>()
    val vibrateDuringIncomingCall = MutableLiveData<Boolean>()

    val autoRecordCalls = MutableLiveData<Boolean>()

    // Conversations settings
    val showConversationsSettings = MutableLiveData<Boolean>()

    val autoDownloadEnabled = MutableLiveData<Boolean>()
    val exportMediaEnabled = MutableLiveData<Boolean>()

    // Contacts settings
    val showContactsSettings = MutableLiveData<Boolean>()

    val ldapAvailable = MutableLiveData<Boolean>()
    val ldapServers = MutableLiveData<List<CardDavLdapModel>>()

    val cardDavFriendsLists = MutableLiveData<List<CardDavLdapModel>>()

    val addLdapServerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }
    val editLdapServerEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val addCardDavServerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val editCardDavServerEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    // Meetings settings
    val showMeetingsSettings = MutableLiveData<Boolean>()

    val defaultLayout = MutableLiveData<Int>()
    val availableLayoutsNames = arrayListOf(
        AppUtils.getString(R.string.settings_meetings_layout_active_speaker_label),
        AppUtils.getString(R.string.settings_meetings_layout_mosaic_label)
    )
    val availableLayoutsValues = arrayListOf(
        Conference.Layout.ActiveSpeaker.toInt(),
        Conference.Layout.Grid.toInt()
    )

    // Network settings
    val useWifiOnly = MutableLiveData<Boolean>()

    // User Interface settings
    val showThemeSelector = MutableLiveData<Boolean>()
    val theme = MutableLiveData<Int>()
    val availableThemesNames = arrayListOf(
        AppUtils.getString(R.string.settings_user_interface_auto_theme_label),
        AppUtils.getString(R.string.settings_user_interface_light_theme_label),
        AppUtils.getString(R.string.settings_user_interface_dark_theme_label)
    )
    val availableThemesValues = arrayListOf(-1, 0, 1)

    // Other

    private lateinit var ringtonePlayer: Player
    private lateinit var deviceRingtonePlayer: Ringtone

    private val playerListener = PlayerListener {
        Log.i("[$TAG] End of ringtone reached")
        stopRingtonePlayer()
    }

    init {
        coreContext.postOnCoreThread { core ->
            hideVideoCallSetting.postValue(!core.isVideoEnabled)
            showConversationsSettings.postValue(!corePreferences.disableChat)
            showMeetingsSettings.postValue(!corePreferences.disableMeetings)
            ldapAvailable.postValue(core.ldapAvailable())
            showThemeSelector.postValue(corePreferences.darkModeAllowed)
        }
        showContactsSettings.value = true

        expandSecurity.value = false
        expandCalls.value = false
        expandConversations.value = false
        expandContacts.value = false
        expandMeetings.value = false
        expandNetwork.value = false
        expandUserInterface.value = false

        isVfsEnabled.value = VFS.isEnabled(coreContext.context)

        val vibrator = coreContext.context.getSystemService(Vibrator::class.java)
        isVibrationAvailable.value = vibrator.hasVibrator()
        if (isVibrationAvailable.value == false) {
            Log.w("$TAG Device doesn't seem to have a vibrator, hiding related setting")
        }

        computeAvailableRingtones()

        coreContext.postOnCoreThread { core ->
            echoCancellerEnabled.postValue(core.isEchoCancellationEnabled)
            routeAudioToBluetooth.postValue(corePreferences.routeAudioToBluetoothIfAvailable)
            videoEnabled.postValue(core.isVideoEnabled)
            vibrateDuringIncomingCall.postValue(core.isVibrationOnIncomingCallEnabled)
            autoRecordCalls.postValue(corePreferences.automaticallyStartCallRecording)

            useWifiOnly.postValue(core.isWifiOnlyEnabled)

            val ringtone = core.ring.orEmpty()
            Log.i("Currently configured ringtone in Core is [$ringtone]")
            selectedRingtone.postValue(ringtone)

            autoDownloadEnabled.postValue(core.maxSizeForAutoDownloadIncomingFiles == 0)
            exportMediaEnabled.postValue(corePreferences.exportMediaToNativeGallery)

            defaultLayout.postValue(core.defaultConferenceLayout.toInt())

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
    fun toggleSecurityExpand() {
        expandSecurity.value = expandSecurity.value == false
    }

    @UiThread
    fun enableVfs() {
        Log.i("$TAG Enabling VFS")
        if (VFS.enable(coreContext.context)) {
            val enabled = VFS.isEnabled(coreContext.context)
            isVfsEnabled.postValue(enabled)
            if (enabled) {
                Log.i("$TAG VFS has been enabled")
            }
        } else {
            Log.e("$TAG Failed to enable VFS!")
        }
    }

    @UiThread
    fun toggleCallsExpand() {
        expandCalls.value = expandCalls.value == false
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
                val playbackDevice = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
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
    fun toggleConversationsExpand() {
        expandConversations.value = expandConversations.value == false
    }

    @UiThread
    fun toggleAutoDownload() {
        val newValue = autoDownloadEnabled.value == false
        coreContext.postOnCoreThread { core ->
            core.maxSizeForAutoDownloadIncomingFiles = if (newValue) 0 else -1
            autoDownloadEnabled.postValue(newValue)
        }
    }

    @UiThread
    fun toggleExportMedia() {
        val newValue = exportMediaEnabled.value == false
        coreContext.postOnCoreThread {
            corePreferences.exportMediaToNativeGallery = newValue
            exportMediaEnabled.postValue(newValue)
        }
    }

    @UiThread
    fun toggleContactsExpand() {
        expandContacts.value = expandContacts.value == false
    }

    @UiThread
    fun addLdapServer() {
        addLdapServerEvent.value = Event(true)
    }

    @UiThread
    fun reloadLdapServers() {
        coreContext.postOnCoreThread { core ->
            val list = arrayListOf<CardDavLdapModel>()

            for (ldap in core.ldapList) {
                val label = ldap.params.server
                if (label.isNotEmpty()) {
                    list.add(
                        CardDavLdapModel(label) {
                            editLdapServerEvent.postValue(Event(label))
                        }
                    )
                }
            }

            ldapServers.postValue(list)
        }
    }

    @UiThread
    fun addCardDavServer() {
        addCardDavServerEvent.value = Event(true)
    }

    @UiThread
    fun reloadConfiguredCardDavServers() {
        coreContext.postOnCoreThread { core ->
            val list = arrayListOf<CardDavLdapModel>()

            for (friendList in core.friendsLists) {
                if (friendList.type == FriendList.Type.CardDAV) {
                    val label = friendList.displayName ?: friendList.uri ?: ""
                    if (label.isNotEmpty()) {
                        list.add(
                            CardDavLdapModel(label) {
                                editCardDavServerEvent.postValue(Event(label))
                            }
                        )
                    }
                }
            }

            cardDavFriendsLists.postValue(list)
        }
    }

    @UiThread
    fun toggleMeetingsExpand() {
        expandMeetings.value = expandMeetings.value == false
    }

    @UiThread
    fun setDefaultLayout(layoutValue: Int) {
        coreContext.postOnCoreThread { core ->
            val newDefaultLayout = Conference.Layout.fromInt(layoutValue)
            core.defaultConferenceLayout = newDefaultLayout
            Log.i("$TAG Default meeting layout [$newDefaultLayout] saved")
            defaultLayout.postValue(layoutValue)
        }
    }

    @UiThread
    fun toggleNetworkExpand() {
        expandNetwork.value = expandNetwork.value == false
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
    fun toggleUserInterfaceExpand() {
        expandUserInterface.value = expandUserInterface.value == false
    }

    @UiThread
    fun setTheme(themeValue: Int) {
        coreContext.postOnCoreThread {
            corePreferences.darkMode = themeValue
            Log.i("$TAG Theme [$theme] saved")
            theme.postValue(themeValue)
        }
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
        } catch (e: SecurityException) {
            Log.e("$TAG Can't get default ringtone URI: $e")
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

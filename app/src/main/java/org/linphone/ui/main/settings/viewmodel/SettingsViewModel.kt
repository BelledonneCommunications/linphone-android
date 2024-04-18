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
import org.linphone.R
import org.linphone.core.Conference
import org.linphone.core.FriendList
import org.linphone.core.VFS
import org.linphone.core.tools.Log
import org.linphone.ui.main.settings.model.CardDavLdapModel
import org.linphone.utils.AppUtils
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
    val echoCancellerEnabled = MutableLiveData<Boolean>()
    val routeAudioToBluetooth = MutableLiveData<Boolean>()
    val videoEnabled = MutableLiveData<Boolean>()

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

    // Advanced setttings
    val remoteProvisioningUrl = MutableLiveData<String>()

    init {
        coreContext.postOnCoreThread { core ->
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

        coreContext.postOnCoreThread { core ->
            echoCancellerEnabled.postValue(core.isEchoCancellationEnabled)
            routeAudioToBluetooth.postValue(corePreferences.routeAudioToBluetoothIfAvailable)
            videoEnabled.postValue(core.isVideoEnabled)
            vibrateDuringIncomingCall.postValue(core.isVibrationOnIncomingCallEnabled)
            autoRecordCalls.postValue(corePreferences.automaticallyStartCallRecording)

            useWifiOnly.postValue(core.isWifiOnlyEnabled)

            autoDownloadEnabled.postValue(core.maxSizeForAutoDownloadIncomingFiles == 0)
            exportMediaEnabled.postValue(corePreferences.exportMediaToNativeGallery)

            defaultLayout.postValue(core.defaultConferenceLayout.toInt())

            theme.postValue(corePreferences.darkMode)

            remoteProvisioningUrl.postValue(core.provisioningUri)
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

    @UiThread
    fun updateRemoteProvisioningUrl() {
        coreContext.postOnCoreThread { core ->
            val newProvisioningUri = remoteProvisioningUrl.value.orEmpty()
            if (newProvisioningUri != core.provisioningUri) {
                Log.i("$TAG Updating remote provisioning URI to [$newProvisioningUri]")
                if (newProvisioningUri.isEmpty()) {
                    core.provisioningUri = null
                } else {
                    core.provisioningUri = newProvisioningUri
                }
            }
        }
    }
}

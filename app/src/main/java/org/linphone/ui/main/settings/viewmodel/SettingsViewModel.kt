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

import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.ContactLoader.Companion.NATIVE_ADDRESS_BOOK_FRIEND_LIST
import org.linphone.core.AudioDevice
import org.linphone.core.Conference
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.EcCalibratorStatus
import org.linphone.core.Factory
import org.linphone.core.FriendList
import org.linphone.core.MediaEncryption
import org.linphone.core.Tunnel
import org.linphone.core.VFS
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.settings.model.CardDavLdapModel
import org.linphone.ui.main.settings.model.CodecModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class SettingsViewModel
    @UiThread
    constructor() : GenericViewModel() {
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
    val expandTunnel = MutableLiveData<Boolean>()
    val isTunnelAvailable = MutableLiveData<Boolean>()

    val recreateActivityEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val keepAliveServiceSettingChangedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Security settings
    val isVfsEnabled = MutableLiveData<Boolean>()

    val isUiSecureModeEnabled = MutableLiveData<Boolean>()

    // Calls settings
    val echoCancellerEnabled = MutableLiveData<Boolean>()
    val calibratedEchoCancellerValue = MutableLiveData<String>()

    val adaptiveRateControlEnabled = MutableLiveData<Boolean>()

    val videoEnabled = MutableLiveData<Boolean>()
    val videoFecEnabled = MutableLiveData<Boolean>()

    val isVibrationAvailable = MutableLiveData<Boolean>()
    val vibrateDuringIncomingCall = MutableLiveData<Boolean>()

    val autoRecordCalls = MutableLiveData<Boolean>()

    val goToIncomingCallNotificationChannelSettingsEvent = MutableLiveData<Event<Uri?>>()

    // Conversations settings
    val showConversationsSettings = MutableLiveData<Boolean>()

    val autoDownloadEnabled = MutableLiveData<Boolean>()

    val autoExportMediaToNativeGallery = MutableLiveData<Boolean>()

    val markAsReadWhenDismissingNotification = MutableLiveData<Boolean>()

    // Contacts settings
    val showContactsSettings = MutableLiveData<Boolean>()

    val sortContactsBy = MutableLiveData<Int>()
    val sortContactsByNames = arrayListOf(
        AppUtils.getString(R.string.contact_editor_first_name),
        AppUtils.getString(R.string.contact_editor_last_name),
    )
    val sortContactsByValues = arrayListOf(0, 1)

    val editNativeContactsInLinphone = MutableLiveData<Boolean>()
    val hideEmptyContacts = MutableLiveData<Boolean>()

    val ldapAvailable = MutableLiveData<Boolean>()
    val ldapServers = MutableLiveData<List<CardDavLdapModel>>()

    val cardDavFriendsLists = MutableLiveData<List<CardDavLdapModel>>()

    val presenceSubscribe = MutableLiveData<Boolean>()

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
    val allowIpv6 = MutableLiveData<Boolean>()

    // User Interface settings
    val autoShowDialpad = MutableLiveData<Boolean>()

    val showThemeSelector = MutableLiveData<Boolean>()
    val theme = MutableLiveData<Int>()
    val availableThemesNames = arrayListOf(
        AppUtils.getString(R.string.settings_user_interface_auto_theme_label),
        AppUtils.getString(R.string.settings_user_interface_light_theme_label),
        AppUtils.getString(R.string.settings_user_interface_dark_theme_label)
    )
    val availableThemesValues = arrayListOf(-1, 0, 1)

    val showColorSelector = MutableLiveData<Boolean>()
    val color = MutableLiveData<String>()
    val availableColorsNames = arrayListOf(
        AppUtils.getString(R.string.orange),
        AppUtils.getString(R.string.yellow),
        AppUtils.getString(R.string.green),
        AppUtils.getString(R.string.blue),
        AppUtils.getString(R.string.red),
        AppUtils.getString(R.string.pink),
        AppUtils.getString(R.string.purple)
    )
    val availableColorsValues = arrayListOf(
        "orange",
        "yellow",
        "green",
        "blue",
        "red",
        "pink",
        "purple"
    )

    // Tunnel settings
    val tunnelMainHost = MutableLiveData<String>()
    val tunnelMainPort = MutableLiveData<String>()
    val tunnelDualMode = MutableLiveData<Boolean>()
    val tunnelDualHost = MutableLiveData<String>()
    val tunnelDualPort = MutableLiveData<String>()
    val tunnelModeIndex = MutableLiveData<Int>()
    val tunnelModeLabels = arrayListOf(
        AppUtils.getString(R.string.settings_tunnel_mode_disabled_label),
        AppUtils.getString(R.string.settings_tunnel_mode_always_label),
        AppUtils.getString(R.string.settings_tunnel_mode_auto_label)
    )

    // Advanced settings
    val showAdvancedSettings = MutableLiveData<Boolean>()

    val sendLogsToCrashlytics = MutableLiveData<Boolean>()
    val isCrashlyticsAvailable = MutableLiveData<Boolean>()
    val startAtBoot = MutableLiveData<Boolean>()
    val keepAliveThirdPartyAccountsService = MutableLiveData<Boolean>()
    val useSmffForCallRecording = MutableLiveData<Boolean>()

    val deviceName = MutableLiveData<String>()
    val remoteProvisioningUrl = MutableLiveData<String>()

    val mediaEncryptionIndex = MutableLiveData<Int>()
    val mediaEncryptionLabels = arrayListOf<String>()
    private val mediaEncryptionValues = arrayListOf<MediaEncryption>()
    val mediaEncryptionMandatory = MutableLiveData<Boolean>()
    val acceptEarlyMedia = MutableLiveData<Boolean>()
    val ringDuringEarlyMedia = MutableLiveData<Boolean>()
    val allowOutgoingEarlyMedia = MutableLiveData<Boolean>()
    val autoAnswerIncomingCalls = MutableLiveData<Boolean>()
    val autoAnswerIncomingCallsDelay = MutableLiveData<Int>()
    val autoAnswerIncomingCallsWithVideoDirectionSendReceive = MutableLiveData<Boolean>()

    val expandAudioDevices = MutableLiveData<Boolean>()
    val inputAudioDeviceIndex = MutableLiveData<Int>()
    val inputAudioDeviceLabels = arrayListOf<String>()
    private val inputAudioDeviceValues = arrayListOf<AudioDevice>()
    val outputAudioDeviceIndex = MutableLiveData<Int>()
    val outputAudioDeviceLabels = arrayListOf<String>()
    private val outputAudioDeviceValues = arrayListOf<AudioDevice>()

    val expandAudioCodecs = MutableLiveData<Boolean>()
    val audioCodecs = MutableLiveData<List<CodecModel>>()

    val expandVideoCodecs = MutableLiveData<Boolean>()
    val videoCodecs = MutableLiveData<List<CodecModel>>()

    val expandEarlyMedia = MutableLiveData<Boolean>()
    val expandAutoAnswer = MutableLiveData<Boolean>()

    // Developer settings
    val showDeveloperSettings = MutableLiveData<Boolean>()

    val logcat = MutableLiveData<Boolean>()
    val fileSharingServerUrl = MutableLiveData<String>()
    val logsSharingServerUrl = MutableLiveData<String>()
    val createEndToEndEncryptedConferences = MutableLiveData<Boolean>()
    val enableVuMeters = MutableLiveData<Boolean>()
    val enableAdvancedCallStats = MutableLiveData<Boolean>()
    val pushCompatibleDomainsList = MutableLiveData<String>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onAudioDevicesListUpdated(core: Core) {
            Log.i(
                "$TAG Audio devices list has changed, update available input/output audio devices list"
            )
            setupAudioDevices()
        }

        @WorkerThread
        override fun onEcCalibrationResult(core: Core, status: EcCalibratorStatus, delayMs: Int) {
            if (status == EcCalibratorStatus.InProgress) return
            echoCancellerCalibrationFinished(status, delayMs)
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            isTunnelAvailable.postValue(core.tunnelAvailable())
            isCrashlyticsAvailable.postValue(coreContext.isCrashlyticsAvailable())

            showConversationsSettings.postValue(!corePreferences.disableChat)
            showMeetingsSettings.postValue(!corePreferences.disableMeetings)
            ldapAvailable.postValue(core.ldapAvailable())
            showThemeSelector.postValue(corePreferences.darkModeAllowed)
            showColorSelector.postValue(corePreferences.changeMainColorAllowed)
            showAdvancedSettings.postValue(!corePreferences.hideAdvancedSettings)
            showDeveloperSettings.postValue(corePreferences.showDeveloperSettings)
        }
        showContactsSettings.value = true

        expandSecurity.value = false
        expandCalls.value = false
        expandConversations.value = false
        expandContacts.value = false
        expandMeetings.value = false
        expandNetwork.value = false
        expandUserInterface.value = false
        expandTunnel.value = false
        expandAudioDevices.value = false
        expandAudioCodecs.value = false
        expandVideoCodecs.value = false
        expandEarlyMedia.value = false
        expandAutoAnswer.value = false

        val vfsEnabled = VFS.isEnabled(coreContext.context)
        isVfsEnabled.value = vfsEnabled

        val vibrator = coreContext.context.getSystemService(Vibrator::class.java)
        isVibrationAvailable.value = vibrator.hasVibrator()
        if (isVibrationAvailable.value == false) {
            Log.w("$TAG Device doesn't seem to have a vibrator, hiding related setting")
        }

        coreContext.postOnCoreThread { core ->
            isUiSecureModeEnabled.postValue(corePreferences.enableSecureMode)

            echoCancellerEnabled.postValue(core.isEchoCancellationEnabled)
            val delay = core.echoCancellationCalibration
            if (delay > 0) {
                val label = AppUtils.getString(
                    R.string.settings_calls_calibrate_echo_canceller_done
                ).format(
                    delay
                )
                calibratedEchoCancellerValue.postValue(label)
            } else if (delay == 0) {
                calibratedEchoCancellerValue.postValue(
                    AppUtils.getString(
                        R.string.settings_calls_calibrate_echo_canceller_done_no_echo
                    )
                )
            }

            adaptiveRateControlEnabled.postValue(core.isAdaptiveRateControlEnabled)

            videoEnabled.postValue(core.isVideoEnabled)
            videoFecEnabled.postValue(core.isFecEnabled)
            vibrateDuringIncomingCall.postValue(core.isVibrationOnIncomingCallEnabled)
            autoRecordCalls.postValue(corePreferences.automaticallyStartCallRecording)
            useSmffForCallRecording.postValue(corePreferences.callRecordingUseSmffFormat)

            useWifiOnly.postValue(core.isWifiOnlyEnabled)
            allowIpv6.postValue(core.isIpv6Enabled)

            autoDownloadEnabled.postValue(core.maxSizeForAutoDownloadIncomingFiles == 0)
            autoExportMediaToNativeGallery.postValue(corePreferences.makePublicMediaFilesDownloaded && !vfsEnabled)
            markAsReadWhenDismissingNotification.postValue(
                corePreferences.markConversationAsReadWhenDismissingMessageNotification
            )

            sortContactsBy.postValue(if (corePreferences.sortContactsByFirstName) 0 else 1)
            editNativeContactsInLinphone.postValue(corePreferences.editNativeContactsInLinphone)
            hideEmptyContacts.postValue(corePreferences.hideContactsWithoutPhoneNumberOrSipAddress)
            presenceSubscribe.postValue(core.isFriendListSubscriptionEnabled)

            defaultLayout.postValue(core.defaultConferenceLayout.toInt())

            autoShowDialpad.postValue(corePreferences.automaticallyShowDialpad)
            theme.postValue(corePreferences.darkMode)
            color.postValue(corePreferences.themeMainColor)

            if (core.tunnelAvailable()) {
                setupTunnel()
            }

            sendLogsToCrashlytics.postValue(corePreferences.sendLogsToCrashlytics)
            startAtBoot.postValue(corePreferences.autoStart)
            keepAliveThirdPartyAccountsService.postValue(corePreferences.keepServiceAlive)

            deviceName.postValue(corePreferences.deviceName)
            remoteProvisioningUrl.postValue(core.provisioningUri)

            acceptEarlyMedia.postValue(corePreferences.acceptEarlyMedia)
            ringDuringEarlyMedia.postValue(core.ringDuringIncomingEarlyMedia)
            allowOutgoingEarlyMedia.postValue(corePreferences.allowOutgoingEarlyMedia)
            autoAnswerIncomingCalls.postValue(corePreferences.autoAnswerEnabled)
            autoAnswerIncomingCallsDelay.postValue(corePreferences.autoAnswerDelay)
            autoAnswerIncomingCallsWithVideoDirectionSendReceive.postValue(corePreferences.autoAnswerVideoCallsWithVideoDirectionSendReceive)

            setupMediaEncryption()
            setupAudioDevices()
            setupCodecs()

            logcat.postValue(corePreferences.printLogsInLogcat)
            fileSharingServerUrl.postValue(core.fileTransferServer)
            logsSharingServerUrl.postValue(core.logCollectionUploadServerUrl)
            createEndToEndEncryptedConferences.postValue(corePreferences.createEndToEndEncryptedMeetingsAndGroupCalls)
            enableVuMeters.postValue(corePreferences.showMicrophoneAndSpeakerVuMeters)
            enableAdvancedCallStats.postValue(corePreferences.showAdvancedCallStats)

            val domainsListBuilder = StringBuilder()
            val domainsArray = corePreferences.pushNotificationCompatibleDomains
            for (item in domainsArray) {
                domainsListBuilder.append(item)
                domainsListBuilder.append(",")
            }
            if (domainsListBuilder.isNotEmpty()) {
                domainsListBuilder.deleteAt(domainsListBuilder.length - 1) // Remove last ','
            }
            val domainsList = domainsListBuilder.toString()
            Log.d("$TAG Computed push compatible domains list is [$domainsList]")
            pushCompatibleDomainsList.postValue(domainsList)
        }
    }

    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }

        super.onCleared()
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
                showGreenToast(R.string.settings_security_enable_vfs_success_toast, R.drawable.lock_key)
            }
        } else {
            Log.e("$TAG Failed to enable VFS!")
            isVfsEnabled.postValue(false)
            showRedToast(R.string.settings_security_enable_vfs_failure_toast, R.drawable.warning_circle)
        }
    }

    @UiThread
    fun toggleUiSecureMode() {
        val newValue = isUiSecureModeEnabled.value == false
        coreContext.postOnCoreThread {
            corePreferences.enableSecureMode = newValue
            recreateActivityEvent.postValue(Event(true))
            isUiSecureModeEnabled.postValue(newValue)
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
    fun calibrateEchoCanceller() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Starting echo canceller calibration")
            core.startEchoCancellerCalibration()
            calibratedEchoCancellerValue.postValue(
                AppUtils.getString(R.string.settings_calls_calibrate_echo_canceller_in_progress)
            )
        }
    }

    @UiThread
    fun toggleAdaptiveRateControl() {
        val newValue = adaptiveRateControlEnabled.value == false
        coreContext.postOnCoreThread { core ->
            core.isAdaptiveRateControlEnabled = newValue
            adaptiveRateControlEnabled.postValue(newValue)
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
    fun toggleEnableVideoFec() {
        val newValue = videoFecEnabled.value == false
        coreContext.postOnCoreThread { core ->
            core.isFecEnabled = newValue
            videoFecEnabled.postValue(core.isFecEnabled)
        }
    }

    @UiThread
    fun toggleUseSmffForCallRecording() {
        val newValue = useSmffForCallRecording.value == false
        coreContext.postOnCoreThread { core ->
            corePreferences.callRecordingUseSmffFormat = newValue
            useSmffForCallRecording.postValue(newValue)
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
    fun changeRingtone() {
        coreContext.postOnCoreThread { core ->
            try {
                val defaultDeviceRingtone = RingtoneManager.getActualDefaultRingtoneUri(
                    coreContext.context,
                    RingtoneManager.TYPE_RINGTONE
                )
                val coreRingtone = core.ring?.toUri()
                Log.i("$TAG Currently set ringtone in Core is [$coreRingtone], device default ringtone is [$defaultDeviceRingtone]")
                val currentRingtone = coreRingtone ?: defaultDeviceRingtone
                goToIncomingCallNotificationChannelSettingsEvent.postValue(Event(currentRingtone))
            } catch (e: Exception) {
                Log.e("$TAG Failed to get current ringtone: $e")
            }
        }
    }

    @UiThread
    fun setRingtoneUri(ringtone: Uri) {
        coreContext.postOnCoreThread { core ->
            core.ring = ringtone.toString()
            Log.i("$TAG Newly set ringtone is [${core.ring}]")
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
    fun toggleAutoExportMediaFilesToNativeGallery() {
        val newValue = autoExportMediaToNativeGallery.value == false
        coreContext.postOnCoreThread { core ->
            corePreferences.makePublicMediaFilesDownloaded = newValue
            autoExportMediaToNativeGallery.postValue(newValue)
        }
    }

    @UiThread
    fun toggleMarkConversationAsReadWhenDismissingNotification() {
        val newValue = markAsReadWhenDismissingNotification.value == false
        coreContext.postOnCoreThread {
            corePreferences.markConversationAsReadWhenDismissingMessageNotification = newValue
            markAsReadWhenDismissingNotification.postValue(newValue)
        }
    }

    @UiThread
    fun toggleContactsExpand() {
        expandContacts.value = expandContacts.value == false
    }

    @UiThread
    fun setContactSorting(sortingValue: Int) {
        coreContext.postOnCoreThread { core ->
            corePreferences.sortContactsByFirstName = sortingValue == 0
        }
    }

    @UiThread
    fun toggleEditNativeContactsInLinphone() {
        val newValue = editNativeContactsInLinphone.value == false
        coreContext.postOnCoreThread {
            corePreferences.editNativeContactsInLinphone = newValue
            editNativeContactsInLinphone.postValue(newValue)
        }
    }

    @UiThread
    fun toggleHideEmptyContacts() {
        val newValue = hideEmptyContacts.value == false
        coreContext.postOnCoreThread {
            corePreferences.hideContactsWithoutPhoneNumberOrSipAddress = newValue
            hideEmptyContacts.postValue(newValue)
        }
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
    fun togglePresenceSubscribe() {
        val newValue = presenceSubscribe.value == false
        coreContext.postOnCoreThread { core ->
            core.isFriendListSubscriptionEnabled = newValue
            presenceSubscribe.postValue(newValue)
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
    fun toggleIpv6() {
        val newValue = allowIpv6.value == false
        coreContext.postOnCoreThread { core ->
            core.isIpv6Enabled = newValue
            allowIpv6.postValue(newValue)
        }
    }

    @UiThread
    fun toggleUserInterfaceExpand() {
        expandUserInterface.value = expandUserInterface.value == false
    }

    @UiThread
    fun toggleAutoShowDialpad() {
        val newValue = autoShowDialpad.value == false
        coreContext.postOnCoreThread { core ->
            corePreferences.automaticallyShowDialpad = newValue
            autoShowDialpad.postValue(newValue)
        }
    }

    @UiThread
    fun setTheme(themeValue: Int) {
        coreContext.postOnCoreThread {
            corePreferences.darkMode = themeValue
            Log.i("$TAG Theme [$themeValue] saved")
        }
        theme.value = themeValue
    }

    @UiThread
    fun setColor(colorName: String) {
        coreContext.postOnCoreThread {
            corePreferences.themeMainColor = colorName
            Log.i("$TAG Color [$colorName] saved")
        }
        color.value = colorName
    }

    @UiThread
    fun toggleTunnelExpand() {
        expandTunnel.value = expandTunnel.value == false
    }

    @WorkerThread
    private fun setupTunnel() {
        // Tunnel mode values are 0, 1 and 2, we can use selected item position directly
        val tunnelConfig = coreContext.core.tunnel
        if (tunnelConfig != null) {
            val mainTunnel = tunnelConfig.servers.firstOrNull()
            if (mainTunnel != null) {
                tunnelMainHost.postValue(mainTunnel.host)
                tunnelMainPort.postValue(mainTunnel.port.toString())
                if (tunnelConfig.isDualModeEnabled) {
                    tunnelDualHost.postValue(mainTunnel.host2)
                    tunnelDualPort.postValue(mainTunnel.port2.toString())
                }
            }
            tunnelDualMode.postValue(tunnelConfig.isDualModeEnabled)
            tunnelModeIndex.postValue(tunnelConfig.mode.ordinal)
        } else {
            Log.w("$TAG No tunnel config found!")
            tunnelModeIndex.postValue(0)
        }
    }

    @UiThread
    fun toggleTunnelDualMode() {
        tunnelDualMode.value = tunnelDualMode.value == false
    }

    @UiThread
    fun saveTunnelConfig() {
        coreContext.postOnCoreThread { core ->
            if (core.tunnelAvailable()) {
                val tunnel = core.tunnel
                tunnel?.cleanServers()

                val config = Factory.instance().createTunnelConfig()
                config.host = tunnelMainHost.value.orEmpty()
                config.port = tunnelMainPort.value?.toInt() ?: 0

                tunnel?.isDualModeEnabled = tunnelDualMode.value == true
                if (tunnelDualMode.value == true) {
                    config.host2 = tunnelDualHost.value.orEmpty()
                    config.port2 = tunnelDualPort.value?.toInt() ?: 0
                }

                tunnel?.mode = Tunnel.Mode.fromInt(tunnelModeIndex.value ?: 0)

                tunnel?.addServer(config)
                Log.i("$TAG Tunnel configuration added into Core")
            }
        }
    }

    @UiThread
    fun toggleSendLogsToCrashlytics() {
        val newValue = sendLogsToCrashlytics.value == false

        coreContext.postOnCoreThread {
            corePreferences.sendLogsToCrashlytics = newValue
            sendLogsToCrashlytics.postValue(newValue)
            coreContext.updateCrashlyticsEnabledSetting(newValue)
        }
    }

    @UiThread
    fun toggleStartAtBoot() {
        val newValue = startAtBoot.value == false

        coreContext.postOnCoreThread {
            corePreferences.autoStart = newValue
            startAtBoot.postValue(newValue)
        }
    }

    @UiThread
    fun toggleKeepAliveThirdPartyAccountService() {
        val newValue = keepAliveThirdPartyAccountsService.value == false

        coreContext.postOnCoreThread {
            corePreferences.keepServiceAlive = newValue
            keepAliveThirdPartyAccountsService.postValue(newValue)
            if (newValue) {
                coreContext.startKeepAliveService()
            } else {
                coreContext.stopKeepAliveService()
            }
            keepAliveServiceSettingChangedEvent.postValue(Event(true))
        }
    }

    @WorkerThread
    private fun setupMediaEncryption() {
        val core = coreContext.core

        mediaEncryptionLabels.clear()
        mediaEncryptionValues.clear()

        var index = 0
        val defaultMediaEncryption = core.mediaEncryption
        Log.i("$TAG Current media encryption is [$defaultMediaEncryption]")
        for (encryption in MediaEncryption.entries) {
            if (core.isMediaEncryptionSupported(encryption)) {
                if (encryption == MediaEncryption.ZRTP) {
                    if (core.postQuantumAvailable) {
                        Log.i("$TAG Post Quantum ZRTP is available")
                        mediaEncryptionLabels.add(
                            AppUtils.getString(
                                R.string.call_stats_media_encryption_zrtp_post_quantum
                            )
                        )
                    } else {
                        Log.i(
                            "$TAG Post Quantum ZRTP isn't available, will use classic ZRTP instead"
                        )
                        mediaEncryptionLabels.add(encryption.toString())
                    }
                } else {
                    mediaEncryptionLabels.add(encryption.toString())
                }
                mediaEncryptionValues.add(encryption)
                if (encryption == defaultMediaEncryption) {
                    mediaEncryptionIndex.postValue(index)
                }
                index += 1
            }
        }

        mediaEncryptionMandatory.postValue(core.isMediaEncryptionMandatory)
    }

    @UiThread
    fun setMediaEncryption(index: Int) {
        coreContext.postOnCoreThread { core ->
            val mediaEncryption = mediaEncryptionValues[index]
            core.mediaEncryption = mediaEncryption

            if (mediaEncryption == MediaEncryption.None) {
                core.isMediaEncryptionMandatory = false
                mediaEncryptionMandatory.postValue(false)
            }
        }
    }

    @UiThread
    fun toggleMediaEncryptionMandatory() {
        val newValue = mediaEncryptionMandatory.value == false

        coreContext.postOnCoreThread { core ->
            core.isMediaEncryptionMandatory = newValue
            mediaEncryptionMandatory.postValue(newValue)
        }
    }

    @UiThread
    fun toggleEarlyMediaExpand() {
        expandEarlyMedia.value = expandEarlyMedia.value == false
    }

    @UiThread
    fun toggleAcceptEarlyMedia() {
        val newValue = acceptEarlyMedia.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.acceptEarlyMedia = newValue
            acceptEarlyMedia.postValue(newValue)
        }
    }

    @UiThread
    fun toggleRingDuringEarlyMedia() {
        val newValue = ringDuringEarlyMedia.value == false

        coreContext.postOnCoreThread { core ->
            core.ringDuringIncomingEarlyMedia = newValue
            ringDuringEarlyMedia.postValue(newValue)
        }
    }

    @UiThread
    fun toggleAllowOutgoingEarlyMedia() {
        val newValue = allowOutgoingEarlyMedia.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.allowOutgoingEarlyMedia = newValue
            allowOutgoingEarlyMedia.postValue(newValue)
        }
    }

    @UiThread
    fun toggleAutoAnswerExpand() {
        expandAutoAnswer.value = expandAutoAnswer.value == false
    }

    @UiThread
    fun toggleEnableAutoAnswerIncomingCalls() {
        val newValue = autoAnswerIncomingCalls.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.autoAnswerEnabled = newValue
            autoAnswerIncomingCalls.postValue(newValue)
        }
    }

    @UiThread
    fun updateAutoAnswerIncomingCallsDelay(newValue: String) {
        if (newValue.isNotEmpty()) {
            try {
                val delay = newValue.toInt()
                coreContext.postOnCoreThread {
                    corePreferences.autoAnswerDelay = delay
                }
            } catch (nfe: NumberFormatException) {
                Log.e("$TAG Ignoring new auto answer incoming calls delay as it can't be converted to int: $nfe")
            }
        }
    }

    @UiThread
    fun toggleEnableAutoAnswerIncomingCallsWithVideoDirectionSendReceive() {
        val newValue = autoAnswerIncomingCallsWithVideoDirectionSendReceive.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.autoAnswerVideoCallsWithVideoDirectionSendReceive = newValue
            autoAnswerIncomingCallsWithVideoDirectionSendReceive.postValue(newValue)
        }
    }

    @UiThread
    fun updateDeviceName() {
        coreContext.postOnCoreThread {
            val newDeviceName = deviceName.value.orEmpty().trim()
            if (newDeviceName != corePreferences.deviceName) {
                corePreferences.deviceName = newDeviceName
                Log.i(
                    "$TAG Updated device name to [${corePreferences.deviceName}], re-compute user-agent"
                )
                coreContext.computeUserAgent()
            }
        }
    }

    @UiThread
    fun updateSharingServersUrl() {
        coreContext.postOnCoreThread { core ->
            val newFileSharingServerUrl = fileSharingServerUrl.value.orEmpty().trim()
            if (newFileSharingServerUrl.isNotEmpty()) {
                Log.i("$TAG Updated file sharing server URL to [$newFileSharingServerUrl]")
                core.fileTransferServer = newFileSharingServerUrl
            }

            val newLogsSharingServerUrl = logsSharingServerUrl.value.orEmpty().trim()
            if (newLogsSharingServerUrl.isNotEmpty()) {
                Log.i("$TAG Updated logs upload server URL to [$newLogsSharingServerUrl]")
                core.logCollectionUploadServerUrl = newLogsSharingServerUrl
            }
        }
    }

    @UiThread
    fun updateRemoteProvisioningUrl() {
        coreContext.postOnCoreThread { core ->
            val newProvisioningUri = remoteProvisioningUrl.value.orEmpty().trim()
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

    @UiThread
    fun downloadAndApplyRemoteProvisioning() {
        Log.i("$TAG Updating remote provisioning URI now and then download/apply it")
        updateRemoteProvisioningUrl()
        coreContext.postOnCoreThread {
            Log.i("$TAG Restarting the Core to apply configuration changes")
            coreContext.core.stop()
            Log.i("$TAG Core has been stopped, restarting it")
            coreContext.core.start()
            Log.i("$TAG Core has been restarted")
        }
    }

    @UiThread
    fun toggleAudioDevicesExpand() {
        expandAudioDevices.value = expandAudioDevices.value == false
    }

    @UiThread
    fun setInputAudioDevice(index: Int) {
        coreContext.postOnCoreThread { core ->
            val audioDevice = inputAudioDeviceValues[index]
            core.defaultInputAudioDevice = audioDevice
        }
    }

    @UiThread
    fun setOutputAudioDevice(index: Int) {
        coreContext.postOnCoreThread { core ->
            val audioDevice = outputAudioDeviceValues[index]
            core.defaultOutputAudioDevice = audioDevice
        }
    }

    @WorkerThread
    private fun setupAudioDevices() {
        val core = coreContext.core

        inputAudioDeviceLabels.clear()
        inputAudioDeviceValues.clear()
        outputAudioDeviceLabels.clear()
        outputAudioDeviceValues.clear()

        var inputIndex = 0
        val defaultInputAudioDevice = core.defaultInputAudioDevice
        Log.i("$TAG Current default input audio device ID is [${defaultInputAudioDevice?.id}]")
        for (audioDevice in core.extendedAudioDevices) {
            if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityRecord)) {
                Log.i("$TAG Found record device [${audioDevice.deviceName}] with audio driver [${audioDevice.driverName}] and type [${audioDevice.type}]")
                inputAudioDeviceLabels.add(LinphoneUtils.getAudioDeviceName(audioDevice))
                inputAudioDeviceValues.add(audioDevice)
                if (audioDevice.id == defaultInputAudioDevice?.id) {
                    inputAudioDeviceIndex.postValue(inputIndex)
                }
                inputIndex += 1
            }
        }

        var outputIndex = 0
        val defaultOutputAudioDevice = core.defaultOutputAudioDevice
        Log.i("$TAG Current default output audio device is [${defaultOutputAudioDevice?.id}]")
        for (audioDevice in core.extendedAudioDevices) {
            if (audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                Log.i("$TAG Found playback device [${audioDevice.deviceName}] with audio driver [${audioDevice.driverName}] and type [${audioDevice.type}]")
                outputAudioDeviceLabels.add(LinphoneUtils.getAudioDeviceName(audioDevice))
                outputAudioDeviceValues.add(audioDevice)
                if (audioDevice.id == defaultOutputAudioDevice?.id) {
                    outputAudioDeviceIndex.postValue(outputIndex)
                }
                outputIndex += 1
            }
        }
    }

    @UiThread
    fun toggleAudioCodecsExpand() {
        expandAudioCodecs.value = expandAudioCodecs.value == false
    }

    @UiThread
    fun toggleVideoCodecsExpand() {
        expandVideoCodecs.value = expandVideoCodecs.value == false
    }

    @WorkerThread
    private fun setupCodecs() {
        val core = coreContext.core

        val audioCodecsList = arrayListOf<CodecModel>()
        for (payload in core.audioPayloadTypes) {
            val model = CodecModel(
                payload.mimeType,
                payload.clockRate,
                payload.channels,
                null,
                true,
                payload.enabled()
            ) { enabled ->
                payload.enable(enabled)
            }
            audioCodecsList.add(model)
        }
        audioCodecs.postValue(audioCodecsList)

        val videoCodecsList = arrayListOf<CodecModel>()
        for (payload in core.videoPayloadTypes) {
            val model = CodecModel(payload.mimeType, -1, 0, payload.recvFmtp, false, payload.enabled()) { enabled ->
                payload.enable(enabled)
            }
            videoCodecsList.add(model)
        }
        videoCodecs.postValue(videoCodecsList)
    }

    @WorkerThread
    private fun echoCancellerCalibrationFinished(status: EcCalibratorStatus, delay: Int) {
        val value = when (status) {
            EcCalibratorStatus.DoneNoEcho -> {
                echoCancellerEnabled.postValue(false)
                AppUtils.getString(R.string.settings_calls_calibrate_echo_canceller_done_no_echo)
            }
            EcCalibratorStatus.Done -> {
                echoCancellerEnabled.postValue(true)
                AppUtils.getString(R.string.settings_calls_calibrate_echo_canceller_done).format(
                    delay
                )
            }
            EcCalibratorStatus.Failed -> {
                AppUtils.getString(R.string.settings_calls_calibrate_echo_canceller_failed)
            }
            else -> ""
        }
        calibratedEchoCancellerValue.postValue(value)
    }

    @UiThread
    fun toggleDeveloperSettings() {
        val newValue = showDeveloperSettings.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.showDeveloperSettings = newValue
            showDeveloperSettings.postValue(newValue)
        }
    }

    @UiThread
    fun reloadShowDeveloperSettings() {
        coreContext.postOnCoreThread {
            showDeveloperSettings.postValue(corePreferences.showDeveloperSettings)
        }
    }

    @UiThread
    fun toggleLogcat() {
        val newValue = logcat.value == false
        coreContext.postOnCoreThread {
            corePreferences.printLogsInLogcat = newValue
            coreContext.updateLogcatEnabledSetting(newValue)
            Factory.instance().enableLogcatLogs(newValue)
            logcat.postValue(newValue)
        }
    }

    @UiThread
    fun toggleConferencesEndToEndEncryption() {
        val newValue = createEndToEndEncryptedConferences.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.createEndToEndEncryptedMeetingsAndGroupCalls = newValue
            createEndToEndEncryptedConferences.postValue(newValue)
        }
    }

    @UiThread
    fun toggleEnableVuMeters() {
        val newValue = enableVuMeters.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.showMicrophoneAndSpeakerVuMeters = newValue
            enableVuMeters.postValue(newValue)
        }
    }

    @UiThread
    fun toggleEnableAdvancedCallStats() {
        val newValue = enableAdvancedCallStats.value == false

        coreContext.postOnCoreThread { core ->
            corePreferences.showAdvancedCallStats = newValue
            enableAdvancedCallStats.postValue(newValue)
        }
    }

    @UiThread
    fun updatePushCompatibleDomainsList() {
        coreContext.postOnCoreThread { core ->
            val flatValue = pushCompatibleDomainsList.value.orEmpty().trim()
            Log.d("$TAG Updating push compatible domains list using user input [$flatValue]")
            val newList = flatValue.split(",").toTypedArray()
            corePreferences.pushNotificationCompatibleDomains = newList
        }
    }

    @UiThread
    fun clearNativeFriendsDatabase() {
        coreContext.postOnCoreThread { core ->
            val list = core.getFriendListByName(NATIVE_ADDRESS_BOOK_FRIEND_LIST)
            if (list != null) {
                val friends = list.friends
                Log.i("$TAG Friend list to remove found with [${friends.size}] friends")
                for (friend in friends) {
                    list.removeFriend(friend)
                }
                core.removeFriendList(list)
                Log.i("$TAG Friend list [$NATIVE_ADDRESS_BOOK_FRIEND_LIST] removed")
            }
            showGreenToast(R.string.settings_developer_cleared_native_friends_in_database_toast, R.drawable.trash_simple)
        }
    }
}

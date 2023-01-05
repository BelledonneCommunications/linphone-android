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
package org.linphone.activities.main.settings.viewmodels

import androidx.lifecycle.MutableLiveData
import java.lang.NumberFormatException
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.utils.Event

class ChatSettingsViewModel : GenericSettingsViewModel() {
    val markAsReadNotifDismissalListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.markAsReadUponChatMessageNotificationDismissal = newValue
        }
    }
    val markAsReadNotifDismissal = MutableLiveData<Boolean>()

    val fileSharingUrlListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            core.fileTransferServer = newValue
        }
    }
    val fileSharingUrl = MutableLiveData<String>()

    val autoDownloadListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            val maxSize = when (position) {
                0 -> -1
                1 -> 0
                else -> 10000000
            }
            core.maxSizeForAutoDownloadIncomingFiles = maxSize
            autoDownloadMaxSize.value = maxSize
            updateAutoDownloadIndexFromMaxSize(maxSize)
        }
    }
    val autoDownloadIndex = MutableLiveData<Int>()
    val autoDownloadLabels = MutableLiveData<ArrayList<String>>()

    val autoDownloadMaxSizeListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val maxSize = newValue.toInt()
                core.maxSizeForAutoDownloadIncomingFiles = maxSize
                updateAutoDownloadIndexFromMaxSize(maxSize)
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val autoDownloadMaxSize = MutableLiveData<Int>()

    val autoDownloadVoiceRecordingsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isAutoDownloadVoiceRecordingsEnabled = newValue
            autoDownloadVoiceRecordings.value = newValue
        }
    }
    val autoDownloadVoiceRecordings = MutableLiveData<Boolean>()

    val downloadedMediaPublicListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.makePublicMediaFilesDownloaded = newValue
            downloadedMediaPublic.value = newValue
        }
    }
    val downloadedMediaPublic = MutableLiveData<Boolean>()

    val hideNotificationContentListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.hideChatMessageContentInNotification = newValue
            hideNotificationContent.value = newValue
        }
    }
    val hideNotificationContent = MutableLiveData<Boolean>()

    val useInAppFileViewerListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.useInAppFileViewerForNonEncryptedFiles = newValue
            useInAppFileViewer.value = newValue
        }
    }
    val useInAppFileViewer = MutableLiveData<Boolean>()

    val launcherShortcutsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.chatRoomShortcuts = newValue
            launcherShortcutsEvent.value = Event(newValue)
        }
    }
    val launcherShortcuts = MutableLiveData<Boolean>()
    val launcherShortcutsEvent = MutableLiveData<Event<Boolean>>()

    val hideEmptyRoomsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.hideEmptyRooms = newValue
            reloadChatRoomsEvent.value = Event(true)
        }
    }
    val hideEmptyRooms = MutableLiveData<Boolean>()

    val hideRoomsRemovedProxiesListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.hideRoomsFromRemovedProxies = newValue
            reloadChatRoomsEvent.value = Event(true)
        }
    }
    val hideRoomsRemovedProxies = MutableLiveData<Boolean>()

    val goToAndroidNotificationSettingsListener = object : SettingListenerStub() {
        override fun onClicked() {
            goToAndroidNotificationSettingsEvent.value = Event(true)
        }
    }
    val goToAndroidNotificationSettingsEvent = MutableLiveData<Event<Boolean>>()

    val vfs = MutableLiveData<Boolean>()

    val reloadChatRoomsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    init {
        markAsReadNotifDismissal.value = prefs.markAsReadUponChatMessageNotificationDismissal
        downloadedMediaPublic.value = prefs.makePublicMediaFilesDownloaded && !prefs.vfsEnabled
        useInAppFileViewer.value = prefs.useInAppFileViewerForNonEncryptedFiles || prefs.vfsEnabled
        hideNotificationContent.value = prefs.hideChatMessageContentInNotification
        initAutoDownloadList()
        autoDownloadVoiceRecordings.value = core.isAutoDownloadVoiceRecordingsEnabled
        launcherShortcuts.value = prefs.chatRoomShortcuts
        hideEmptyRooms.value = prefs.hideEmptyRooms
        hideRoomsRemovedProxies.value = prefs.hideRoomsFromRemovedProxies
        fileSharingUrl.value = core.fileTransferServer
        vfs.value = prefs.vfsEnabled
    }

    private fun initAutoDownloadList() {
        val labels = arrayListOf<String>()
        labels.add(prefs.getString(R.string.chat_settings_auto_download_never))
        labels.add(prefs.getString(R.string.chat_settings_auto_download_always))
        labels.add(prefs.getString(R.string.chat_settings_auto_download_under_size))
        autoDownloadLabels.value = labels

        val currentValue = core.maxSizeForAutoDownloadIncomingFiles
        autoDownloadMaxSize.value = currentValue
        updateAutoDownloadIndexFromMaxSize(currentValue)
    }

    private fun updateAutoDownloadIndexFromMaxSize(maxSize: Int) {
        autoDownloadIndex.value = when (maxSize) {
            -1 -> 0
            0 -> 1
            else -> 2
        }
    }
}

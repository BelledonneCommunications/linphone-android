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
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.utils.Event

class ChatSettingsViewModel : GenericSettingsViewModel() {
    val fileSharingUrlListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            core.logCollectionUploadServerUrl = newValue
        }
    }
    val fileSharingUrl = MutableLiveData<String>()

    val downloadedImagesPublicListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.makePublicDownloadedImages = newValue
        }
    }
    val downloadedImagesPublic = MutableLiveData<Boolean>()

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
        }
    }
    val hideEmptyRooms = MutableLiveData<Boolean>()

    val hideRoomsRemovedProxiesListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.hideRoomsFromRemovedProxies = newValue
        }
    }
    val hideRoomsRemovedProxies = MutableLiveData<Boolean>()

    init {
        downloadedImagesPublic.value = prefs.makePublicDownloadedImages
        launcherShortcuts.value = prefs.chatRoomShortcuts
        hideEmptyRooms.value = prefs.hideEmptyRooms
        hideRoomsRemovedProxies.value = prefs.hideRoomsFromRemovedProxies
        fileSharingUrl.value = core.fileTransferServer
    }
}

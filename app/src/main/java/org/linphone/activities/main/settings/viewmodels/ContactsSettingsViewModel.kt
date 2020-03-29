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

class ContactsSettingsViewModel : GenericSettingsViewModel() {
    val friendListSubscribeListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.enableFriendListSubscription(newValue)
        }
    }
    val friendListSubscribe = MutableLiveData<Boolean>()

    val nativePresenceListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.storePresenceInNativeContact = newValue
        }
    }
    val nativePresence = MutableLiveData<Boolean>()

    val showOrganizationListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.displayOrganization = newValue
        }
    }
    val showOrganization = MutableLiveData<Boolean>()

    val launcherShortcutsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.contactsShortcuts = newValue
            launcherShortcutsEvent.value = Event(newValue)
        }
    }
    val launcherShortcuts = MutableLiveData<Boolean>()
    val launcherShortcutsEvent = MutableLiveData<Event<Boolean>>()

    init {
        friendListSubscribe.value = core.isFriendListSubscriptionEnabled
        nativePresence.value = prefs.storePresenceInNativeContact
        showOrganization.value = prefs.displayOrganization
        launcherShortcuts.value = prefs.contactsShortcuts
    }
}

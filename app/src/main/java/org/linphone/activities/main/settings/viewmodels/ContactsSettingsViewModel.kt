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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class ContactsSettingsViewModel : GenericSettingsViewModel() {
    val askWriteContactsPermissionForPresenceStorageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val readContactsPermissionGranted = MutableLiveData<Boolean>()

    val friendListSubscribeListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isFriendListSubscriptionEnabled = newValue
        }
    }
    val friendListSubscribe = MutableLiveData<Boolean>()
    val rlsAddressAvailable = MutableLiveData<Boolean>()

    val showNewContactAccountDialogListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.showNewContactAccountDialog = newValue
        }
    }
    val showNewContactAccountDialog = MutableLiveData<Boolean>()

    val nativePresenceListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            if (newValue) {
                if (PermissionHelper.get().hasWriteContactsPermission()) {
                    prefs.storePresenceInNativeContact = newValue
                } else {
                    askWriteContactsPermissionForPresenceStorageEvent.value = Event(true)
                }
            } else {
                prefs.storePresenceInNativeContact = newValue
            }
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

    val ldapAvailable = MutableLiveData<Boolean>()

    val ldapConfigurations = MutableLiveData<ArrayList<LdapSettingsViewModel>>()

    lateinit var ldapNewSettingsListener: SettingListenerStub
    val ldapSettingsClickedEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }
    private var ldapSettingsListener = object : SettingListenerStub() {
        override fun onAccountClicked(identity: String) {
            ldapSettingsClickedEvent.value = Event(identity.toInt())
        }
    }

    init {
        readContactsPermissionGranted.value = PermissionHelper.get().hasReadContactsPermission()

        friendListSubscribe.value = core.isFriendListSubscriptionEnabled
        rlsAddressAvailable.value = !core.config.getString("sip", "rls_uri", "").isNullOrEmpty()
        showNewContactAccountDialog.value = prefs.showNewContactAccountDialog
        nativePresence.value = prefs.storePresenceInNativeContact
        showOrganization.value = prefs.displayOrganization
        launcherShortcuts.value = prefs.contactsShortcuts

        ldapAvailable.value = core.ldapAvailable()
        ldapConfigurations.value = arrayListOf()

        updateLdapConfigurationsList()
    }

    fun updateLdapConfigurationsList() {
        val list = arrayListOf<LdapSettingsViewModel>()
        var index = 0
        for (ldap in coreContext.core.ldapList) {
            val viewModel = LdapSettingsViewModel(ldap, index.toString())
            viewModel.ldapSettingsListener = ldapSettingsListener
            list.add(viewModel)
            index += 1
        }

        ldapConfigurations.value = list
    }
}

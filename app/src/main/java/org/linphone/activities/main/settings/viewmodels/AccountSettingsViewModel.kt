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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.NumberFormatException
import java.util.*
import kotlin.collections.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class AccountSettingsViewModelFactory(private val identity: String) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        for (account in coreContext.core.accountList) {
            if (account.params.identityAddress?.asStringUriOnly() == identity) {
                return AccountSettingsViewModel(account) as T
            }
        }
        return AccountSettingsViewModel(coreContext.core.defaultAccount!!) as T
    }
}

class AccountSettingsViewModel(val account: Account) : GenericSettingsViewModel() {
    val isDefault = MutableLiveData<Boolean>()

    val displayName = MutableLiveData<String>()

    val identity = MutableLiveData<String>()

    val iconResource = MutableLiveData<Int>()
    val iconContentDescription = MutableLiveData<Int>()

    lateinit var accountsSettingsListener: SettingListenerStub

    val waitForUnregister = MutableLiveData<Boolean>()

    val accountRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val displayUsernameInsteadOfIdentity = corePreferences.replaceSipUriByUsername

    private var accountToDelete: Account? = null

    val listener: AccountListenerStub = object : AccountListenerStub() {
        override fun onRegistrationStateChanged(
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            if (state == RegistrationState.Cleared && account == accountToDelete) {
                Log.i("[Account Settings] Account to remove registration is now cleared")
                waitForUnregister.value = false
                deleteAccount(account)
            } else {
                update()
            }
        }
    }

    /* Settings part */

    val userNameListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            // TODO
        }
    }
    val userName = MutableLiveData<String>()

    val userIdListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            // TODO
        }
    }
    val userId = MutableLiveData<String>()

    val passwordListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            // TODO
        }
    }
    val password = MutableLiveData<String>()

    val domainListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            // TODO
        }
    }
    val domain = MutableLiveData<String>()

    val displayNameListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            account.params.identityAddress?.displayName = newValue
        }
    }
    // displayName mutable is above

    val disableListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            account.params.registerEnabled = !newValue
        }
    }
    val disable = MutableLiveData<Boolean>()

    val isDefaultListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            if (newValue) {
                /core.defaultAccount = account
            }
        }
    }
    // isDefault mutable is above

    private fun deleteAccount(account: Account) {
        val authInfo = account.findAuthInfo()
        if (authInfo != null) {
            Log.i("[Account Settings] Found auth info $authInfo, removing it.")
            core.removeAuthInfo(authInfo)
        } else {
            Log.w("[Account Settings] Couldn't find matching auth info...")
        }

        core.removeAccount(account)
        accountRemovedEvent.value = Event(true)
    }

    val deleteListener = object : SettingListenerStub() {
        override fun onClicked() {
            accountToDelete = account

            val registered = account.state == RegistrationState.Ok
            waitForUnregister.value = registered

            if (core.defaultAccount == account) {
                Log.i("[Account Settings] Account was default, let's look for a replacement")
                for (accountIterator in core.accountList) {
                    if (account != accountIterator) {
                        core.defaultAccount = accountIterator
                        Log.i("[Account Settings] New default account is $accountIterator")
                        break
                    }
                }
            }

            account.params.registerEnabled = false

            if (!registered) {
                Log.w("[Account Settings] Account isn't registered, don't unregister before removing it")
                deleteAccount(account)
            }
        }
    }

    val pushNotificationListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            account.params.pushNotificationAllowed = newValue
        }
    }
    val pushNotification = MutableLiveData<Boolean>()
    val pushNotificationsAvailable = MutableLiveData<Boolean>()

    val transportListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            // TODO
        }
    }
    val transportIndex = MutableLiveData<Int>()
    val transportLabels = MutableLiveData<ArrayList<String>>()

    val proxyListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            account.params.serverAddr = newValue
        }
    }
    val proxy = MutableLiveData<String>()

    val outboundProxyListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            account.params.outboundProxyEnabled = newValue
        }
    }
    val outboundProxy = MutableLiveData<Boolean>()

    val stunServerListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            account.params.natPolicy?.stunServer = newValue
            if (newValue.isEmpty()) ice.value = false
            stunServer.value = newValue
        }
    }
    val stunServer = MutableLiveData<String>()

    val iceListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            account.params.natPolicy?.enableIce(newValue)
        }
    }
    val ice = MutableLiveData<Boolean>()

    val avpfListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            account.params.avpfMode = if (newValue) AVPFMode.Enabled else AVPFMode.Disabled
        }
    }
    val avpf = MutableLiveData<Boolean>()

    val avpfRrIntervalListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                account.params.avpfRrInterval = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val avpfRrInterval = MutableLiveData<Int>()

    val expiresListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                account.params.expires = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val expires = MutableLiveData<Int>()

    val dialPrefixListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            account.params.dialPrefix = newValue
        }
    }
    val dialPrefix = MutableLiveData<String>()

    val escapePlusListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            account.params.dialEscapePlusEnabled = newValue
        }
    }
    val escapePlus = MutableLiveData<Boolean>()

    val linkPhoneNumberListener = object : SettingListenerStub() {
        override fun onClicked() {
            linkPhoneNumberEvent.value = Event(true)
        }
    }
    val linkPhoneNumberEvent = MutableLiveData<Event<Boolean>>()

    init {
        update()
        account.addListener(listener)
        initTransportList()
    }

    override fun onCleared() {
        account.removeListener(listener)
        super.onCleared()
    }

    private fun update() {
        isDefault.value = core.defaultAccount == account
        val identityAddress = account.params.identityAddress
        if (identityAddress != null) {
            displayName.value = LinphoneUtils.getDisplayName(identityAddress)
            identity.value = identityAddress.asStringUriOnly()
        }

        iconResource.value = when (account.state) {
            RegistrationState.Ok -> R.drawable.led_connected
            RegistrationState.Failed -> R.drawable.led_error
            RegistrationState.Progress -> R.drawable.led_inprogress
            else -> R.drawable.led_disconnected
        }
        iconContentDescription.value = when (account.state) {
            RegistrationState.Ok -> R.string.status_connected
            RegistrationState.Progress -> R.string.status_in_progress
            RegistrationState.Failed -> R.string.status_error
            else -> R.string.status_not_connected
        }

        userName.value = account.params.identityAddress?.username
        userId.value = account.findAuthInfo()?.userid
        domain.value = account.params.identityAddress?.domain
        disable.value = !account.params.registerEnabled
        pushNotification.value = account.params.pushNotificationAllowed
        pushNotificationsAvailable.value = core.isPushNotificationAvailable
        proxy.value = account.params.serverAddr
        outboundProxy.value = account.params.outboundProxyEnabled
        stunServer.value = account.params.natPolicy?.stunServer
        ice.value = account.params.natPolicy?.iceEnabled()
        avpf.value = account.params.avpfMode == AVPFMode.Enabled
        avpfRrInterval.value = account.params.avpfRrInterval
        expires.value = account.params.expires
        dialPrefix.value = account.params.dialPrefix
        escapePlus.value = account.params.dialEscapePlusEnabled
    }

    private fun initTransportList() {
        val labels = arrayListOf<String>()

        labels.add(prefs.getString(R.string.account_settings_transport_udp))
        labels.add(prefs.getString(R.string.account_settings_transport_tcp))
        labels.add(prefs.getString(R.string.account_settings_transport_tls))

        transportLabels.value = labels
        val transport = when (account.transport) {
            TransportType.Udp -> "UDP"
            TransportType.Tcp -> "TCP"
            TransportType.Tls -> "TLS"
            TransportType.Dtls -> "DTLS"
        }
        transportIndex.value = labels.indexOf(transport)
    }
}

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
import kotlin.collections.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class AccountSettingsViewModelFactory(private val identity: String) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        for (account in coreContext.core.accountList) {
            if (account.params.identityAddress?.asStringUriOnly() == identity) {
                return AccountSettingsViewModel(account) as T
            }
        }
        val defaultAccount = coreContext.core.defaultAccount
        if (defaultAccount != null) {
            return AccountSettingsViewModel(defaultAccount) as T
        }

        val firstAccount = coreContext.core.accountList.first()
        return AccountSettingsViewModel(firstAccount) as T
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
                if (state == RegistrationState.Ok) {
                    coreContext.contactsManager.updateLocalContacts()
                }
            }
        }
    }

    /* Settings part */

    val userNameListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = account.params.clone()
            val identity = params.identityAddress
            if (identity != null) {
                identity.username = newValue
                params.identityAddress = identity
                account.params = params
            } else {
                Log.e("[Account Settings] Account doesn't have an identity yet")
            }
        }
    }
    val userName = MutableLiveData<String>()

    val userIdListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val authInfo = account.findAuthInfo()
            if (authInfo != null) {
                val newAuthInfo = authInfo.clone()
                newAuthInfo.userid = newValue
                core.removeAuthInfo(authInfo)
                core.addAuthInfo(newAuthInfo)
            } else {
                Log.e("[Account Settings] Failed to find the matching auth info")
            }
        }
    }
    val userId = MutableLiveData<String>()

    val passwordListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val authInfo = account.findAuthInfo()
            if (authInfo != null) {
                val newAuthInfo = authInfo.clone()
                newAuthInfo.password = newValue
                core.removeAuthInfo(authInfo)
                core.addAuthInfo(newAuthInfo)
            } else {
                Log.w("[Account Settings] Failed to find the matching auth info")
                val params = account.params
                val identity = params.identityAddress
                if (identity != null && identity.username != null) {
                    val newAuthInfo = Factory.instance()
                        .createAuthInfo(identity.username!!, userId.value, newValue, null, null, identity.domain)
                    core.addAuthInfo(newAuthInfo)
                } else {
                    Log.e("[Account Settings] Failed to find the user's identity, can't create a new auth info")
                }
            }
        }
    }
    val password = MutableLiveData<String>()

    val domainListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = account.params.clone()
            val identity = params.identityAddress
            if (identity != null) {
                val authInfo = account.findAuthInfo()
                if (authInfo != null) {
                    val newAuthInfo = authInfo.clone()
                    newAuthInfo.domain = newValue
                    core.removeAuthInfo(authInfo)
                    core.addAuthInfo(newAuthInfo)
                } else {
                    Log.e("[Account Settings] Failed to find the matching auth info")
                }

                identity.domain = newValue
                params.identityAddress = identity
                account.params = params
            } else {
                Log.e("[Account Settings] Account doesn't have an identity yet")
            }
        }
    }
    val domain = MutableLiveData<String>()

    val displayNameListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = account.params.clone()
            val identity = params.identityAddress
            if (identity != null) {
                identity.displayName = newValue
                params.identityAddress = identity
                account.params = params
            } else {
                Log.e("[Account Settings] Account doesn't have an identity yet")
            }
        }
    }
    // displayName mutable is above

    val disableListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.registerEnabled = !newValue
            account.params = params
        }
    }
    val disable = MutableLiveData<Boolean>()

    val isDefaultListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            if (newValue) {
                core.defaultAccount = account
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

            val params = account.params.clone()
            params.registerEnabled = false
            account.params = params

            if (!registered) {
                Log.w("[Account Settings] Account isn't registered, don't unregister before removing it")
                deleteAccount(account)
            }
        }
    }

    val pushNotificationListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.pushNotificationAllowed = newValue
            account.params = params
        }
    }
    val pushNotification = MutableLiveData<Boolean>()
    val pushNotificationsAvailable = MutableLiveData<Boolean>()

    val transportListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            val params = account.params.clone()
            params.transport = TransportType.fromInt(position)
            account.params = params
            proxy.value = account.params.serverAddress?.asStringUriOnly()
        }
    }
    val transportIndex = MutableLiveData<Int>()
    val transportLabels = MutableLiveData<ArrayList<String>>()

    val proxyListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = account.params.clone()
            val address = Factory.instance().createAddress(newValue)
            if (address != null) {
                params.serverAddress = address
                account.params = params
                transportIndex.value = account.params.transport.toInt()
            } else {
                Log.e("[Account Settings] Couldn't parse address: $address")
            }
        }
    }
    val proxy = MutableLiveData<String>()

    val outboundProxyListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.outboundProxyEnabled = newValue
            account.params = params
        }
    }
    val outboundProxy = MutableLiveData<Boolean>()

    val stunServerListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = account.params.clone()
            params.natPolicy?.stunServer = newValue
            if (newValue.isEmpty()) ice.value = false
            stunServer.value = newValue
            account.params = params
        }
    }
    val stunServer = MutableLiveData<String>()

    val iceListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.natPolicy?.enableIce(newValue)
            account.params = params
        }
    }
    val ice = MutableLiveData<Boolean>()

    val avpfListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.avpfMode = if (newValue) AVPFMode.Enabled else AVPFMode.Disabled
            account.params = params
        }
    }
    val avpf = MutableLiveData<Boolean>()

    val avpfRrIntervalListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val params = account.params.clone()
                params.avpfRrInterval = newValue.toInt()
                account.params = params
            } catch (nfe: NumberFormatException) {
                Log.e("[Account Settings] Failed to set AVPF RR interval ($newValue): $nfe")
            }
        }
    }
    val avpfRrInterval = MutableLiveData<Int>()

    val expiresListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val params = account.params.clone()
                params.expires = newValue.toInt()
                account.params = params
            } catch (nfe: NumberFormatException) {
                Log.e("[Account Settings] Failed to set expires ($newValue): $nfe")
            }
        }
    }
    val expires = MutableLiveData<Int>()

    val prefixListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = account.params.clone()
            params.internationalPrefix = newValue
            account.params = params
        }
    }
    val prefix = MutableLiveData<String>()

    val dialPrefixListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.useInternationalPrefixForCallsAndChats = newValue
            account.params = params
        }
    }
    val dialPrefix = MutableLiveData<Boolean>()

    val escapePlusListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = account.params.clone()
            params.dialEscapePlusEnabled = newValue
            account.params = params
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
        destroy()
        super.onCleared()
    }

    fun destroy() {
        accountsSettingsListener = object : SettingListenerStub() {}
        account.removeListener(listener)
    }

    private fun update() {
        isDefault.value = core.defaultAccount == account
        val params = account.params
        val identityAddress = params.identityAddress
        if (identityAddress != null) {
            displayName.value = identityAddress.displayName ?: ""
            identity.value = identityAddress.asStringUriOnly()
        }

        iconResource.value = when (account.state) {
            RegistrationState.Ok -> R.drawable.led_registered
            RegistrationState.Failed -> R.drawable.led_error
            RegistrationState.Progress -> R.drawable.led_registration_in_progress
            else -> R.drawable.led_not_registered
        }
        iconContentDescription.value = when (account.state) {
            RegistrationState.Ok -> R.string.status_connected
            RegistrationState.Progress -> R.string.status_in_progress
            RegistrationState.Failed -> R.string.status_error
            else -> R.string.status_not_connected
        }

        userName.value = params.identityAddress?.username
        userId.value = account.findAuthInfo()?.userid
        domain.value = params.identityAddress?.domain
        disable.value = !params.registerEnabled
        pushNotification.value = params.pushNotificationAllowed
        pushNotificationsAvailable.value = core.isPushNotificationAvailable
        proxy.value = params.serverAddress?.asStringUriOnly()
        outboundProxy.value = params.outboundProxyEnabled
        stunServer.value = params.natPolicy?.stunServer
        ice.value = params.natPolicy?.iceEnabled()
        avpf.value = params.avpfMode == AVPFMode.Enabled
        avpfRrInterval.value = params.avpfRrInterval
        expires.value = params.expires
        prefix.value = params.internationalPrefix
        dialPrefix.value = params.useInternationalPrefixForCallsAndChats
        escapePlus.value = params.dialEscapePlusEnabled
    }

    private fun initTransportList() {
        val labels = arrayListOf<String>()

        // Keep following in the same order as TransportType enum
        labels.add(prefs.getString(R.string.account_settings_transport_udp))
        labels.add(prefs.getString(R.string.account_settings_transport_tcp))
        labels.add(prefs.getString(R.string.account_settings_transport_tls))
        if (corePreferences.allowDtlsTransport) {
            labels.add(prefs.getString(R.string.account_settings_transport_dtls))
        }

        transportLabels.value = labels
        transportIndex.value = account.params.transport.toInt()
    }
}

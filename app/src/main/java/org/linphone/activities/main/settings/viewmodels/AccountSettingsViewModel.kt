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
        for (proxy in coreContext.core.proxyConfigList) {
            if (proxy.identityAddress?.asStringUriOnly() == identity) {
                return AccountSettingsViewModel(proxy) as T
            }
        }
        return AccountSettingsViewModel(coreContext.core.defaultProxyConfig!!) as T
    }
}

class AccountSettingsViewModel(val proxyConfig: ProxyConfig) : GenericSettingsViewModel() {
    val isDefault = MutableLiveData<Boolean>()

    val displayName = MutableLiveData<String>()

    val identity = MutableLiveData<String>()

    val iconResource = MutableLiveData<Int>()
    val iconContentDescription = MutableLiveData<Int>()

    lateinit var accountsSettingsListener: SettingListenerStub

    val waitForUnregister = MutableLiveData<Boolean>()

    val proxyConfigRemovedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var proxyConfigToDelete: ProxyConfig? = null

    val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onRegistrationStateChanged(
            core: Core,
            cfg: ProxyConfig,
            state: RegistrationState,
            message: String
        ) {
            if (state == RegistrationState.Cleared && cfg == proxyConfigToDelete) {
                Log.i("[Account Settings] Proxy config to remove registration is now cleared")
                waitForUnregister.value = false
                deleteProxyConfig(cfg)
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
            proxyConfig.identityAddress?.displayName = newValue
        }
    }
    // displayName mutable is above

    val disableListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            proxyConfig.enableRegister(!newValue)
        }
    }
    val disable = MutableLiveData<Boolean>()

    val isDefaultListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            if (newValue) {
                core.defaultProxyConfig = proxyConfig
            }
        }
    }
    // isDefault mutable is above

    private fun deleteProxyConfig(cfg: ProxyConfig) {
        val authInfo = cfg.findAuthInfo()
        if (authInfo != null) {
            Log.i("[Account Settings] Found auth info $authInfo, removing it.")
            core.removeAuthInfo(authInfo)
        } else {
            Log.w("[Account Settings] Couldn't find matching auth info...")
        }

        core.removeProxyConfig(cfg)
        proxyConfigRemovedEvent.value = Event(true)
    }

    val deleteListener = object : SettingListenerStub() {
        override fun onClicked() {
            proxyConfigToDelete = proxyConfig

            val registered = proxyConfig.state == RegistrationState.Ok
            waitForUnregister.value = registered

            if (core.defaultProxyConfig == proxyConfig) {
                Log.i("[Account Settings] Proxy config  was default, let's look for a replacement")
                for (proxyConfigIterator in core.proxyConfigList) {
                    if (proxyConfig != proxyConfigIterator) {
                        core.defaultProxyConfig = proxyConfigIterator
                        Log.i("[Account Settings] New default proxy config is $proxyConfigIterator")
                        break
                    }
                }
            }

            proxyConfig.edit()
            proxyConfig.enableRegister(false)
            proxyConfig.done()

            if (!registered) {
                Log.w("[Account Settings] Proxy config isn't registered, don't unregister before removing it")
                deleteProxyConfig(proxyConfig)
            }
        }
    }

    val pushNotificationListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            proxyConfig.isPushNotificationAllowed = newValue
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
            proxyConfig.serverAddr = newValue
            if (outboundProxy.value == true) {
                // TODO
            }
        }
    }
    val proxy = MutableLiveData<String>()

    val outboundProxyListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            // TODO
        }
    }
    val outboundProxy = MutableLiveData<Boolean>()

    val stunServerListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            proxyConfig.natPolicy?.stunServer = newValue
            if (newValue.isEmpty()) ice.value = false
            stunServer.value = newValue
        }
    }
    val stunServer = MutableLiveData<String>()

    val iceListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            proxyConfig.natPolicy?.enableIce(newValue)
        }
    }
    val ice = MutableLiveData<Boolean>()

    val avpfListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            proxyConfig.avpfMode = if (newValue) AVPFMode.Enabled else AVPFMode.Disabled
        }
    }
    val avpf = MutableLiveData<Boolean>()

    val avpfRrIntervalListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                proxyConfig.avpfRrInterval = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val avpfRrInterval = MutableLiveData<Int>()

    val expiresListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                proxyConfig.expires = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val expires = MutableLiveData<Int>()

    val dialPrefixListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            proxyConfig.dialPrefix = newValue
        }
    }
    val dialPrefix = MutableLiveData<String>()

    val escapePlusListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            proxyConfig.dialEscapePlus = newValue
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
        core.addListener(listener)
        initTransportList()
    }

    override fun onCleared() {
        core.removeListener(listener)
        super.onCleared()
    }

    private fun update() {
        isDefault.value = core.defaultProxyConfig == proxyConfig
        val identityAddress = proxyConfig.identityAddress
        if (identityAddress != null) {
            displayName.value = LinphoneUtils.getDisplayName(identityAddress)
            identity.value = identityAddress.asStringUriOnly()
        }

        iconResource.value = when (proxyConfig.state) {
            RegistrationState.Ok -> R.drawable.led_connected
            RegistrationState.Failed -> R.drawable.led_error
            RegistrationState.Progress -> R.drawable.led_inprogress
            else -> R.drawable.led_disconnected
        }
        iconContentDescription.value = when (proxyConfig.state) {
            RegistrationState.Ok -> R.string.status_connected
            RegistrationState.Progress -> R.string.status_in_progress
            RegistrationState.Failed -> R.string.status_error
            else -> R.string.status_not_connected
        }

        userName.value = proxyConfig.identityAddress?.username
        userId.value = proxyConfig.findAuthInfo()?.userid
        domain.value = proxyConfig.identityAddress?.domain
        disable.value = !proxyConfig.registerEnabled()
        pushNotification.value = proxyConfig.isPushNotificationAllowed
        pushNotificationsAvailable.value = core.isPushNotificationAvailable
        proxy.value = proxyConfig.serverAddr
        outboundProxy.value = proxyConfig.serverAddr == proxyConfig.routes.firstOrNull()
        stunServer.value = proxyConfig.natPolicy?.stunServer
        ice.value = proxyConfig.natPolicy?.iceEnabled()
        avpf.value = proxyConfig.avpfEnabled()
        avpfRrInterval.value = proxyConfig.avpfRrInterval
        expires.value = proxyConfig.expires
        dialPrefix.value = proxyConfig.dialPrefix
        escapePlus.value = proxyConfig.dialEscapePlus
    }

    private fun initTransportList() {
        val labels = arrayListOf<String>()

        labels.add(prefs.getString(R.string.account_settings_transport_udp))
        labels.add(prefs.getString(R.string.account_settings_transport_tcp))
        labels.add(prefs.getString(R.string.account_settings_transport_tls))

        transportLabels.value = labels
        transportIndex.value = labels.indexOf(proxyConfig.transport.toUpperCase(Locale.getDefault()))
    }
}

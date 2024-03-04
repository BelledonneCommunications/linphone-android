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

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AVPFMode
import org.linphone.core.Account
import org.linphone.core.NatPolicy
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class AccountSettingsViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Account Settings ViewModel]"
    }

    val availableTransports = arrayListOf<String>()

    val selectedTransport = MutableLiveData<TransportType>()

    val sipProxyServer = MutableLiveData<String>()

    val outboundProxyEnabled = MutableLiveData<Boolean>()

    val stunServer = MutableLiveData<String>()

    val iceEnabled = MutableLiveData<Boolean>()

    val avpfEnabled = MutableLiveData<Boolean>()

    val expire = MutableLiveData<String>()

    val conferenceFactoryUri = MutableLiveData<String>()

    val audioVideoConferenceFactoryUri = MutableLiveData<String>()

    val limeServerUrl = MutableLiveData<String>()

    val bundleModeEnabled = MutableLiveData<Boolean>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var account: Account
    private lateinit var natPolicy: NatPolicy

    init {
        availableTransports.add(TransportType.Udp.name.uppercase(Locale.getDefault()))
        availableTransports.add(TransportType.Tcp.name.uppercase(Locale.getDefault()))
        availableTransports.add(TransportType.Tls.name.uppercase(Locale.getDefault()))
    }

    @UiThread
    fun findAccountMatchingIdentity(identity: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.accountList.find {
                it.params.identityAddress?.asStringUriOnly() == identity
            }
            if (found != null) {
                Log.i("$TAG Found matching account [$found]")
                account = found

                val params = account.params

                val transportType = params.serverAddress?.transport ?: TransportType.Tls
                selectedTransport.postValue(transportType)

                sipProxyServer.postValue(params.serverAddress?.asStringUriOnly())
                outboundProxyEnabled.postValue(params.isOutboundProxyEnabled)

                natPolicy = params.natPolicy ?: core.createNatPolicy()
                stunServer.postValue(natPolicy.stunServer)
                iceEnabled.postValue(natPolicy.isIceEnabled)

                avpfEnabled.postValue(account.isAvpfEnabled)

                bundleModeEnabled.postValue(params.isRtpBundleEnabled)

                expire.postValue(params.expires.toString())

                conferenceFactoryUri.postValue(params.conferenceFactoryAddress?.asStringUriOnly())

                audioVideoConferenceFactoryUri.postValue(
                    params.audioVideoConferenceFactoryAddress?.asStringUriOnly()
                )

                limeServerUrl.postValue(params.limeServerUrl)

                accountFoundEvent.postValue(Event(true))
            } else {
                Log.e("$TAG Failed to find account matching identity [$identity]")
                accountFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun saveChanges() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Saving changes...")

            if (::account.isInitialized) {
                val newParams = account.params.clone()

                val server = sipProxyServer.value.orEmpty()
                if (server.isNotEmpty()) {
                    val serverAddress = core.interpretUrl(server, false)
                    if (serverAddress != null) {
                        serverAddress.transport = selectedTransport.value
                        newParams.serverAddress = serverAddress
                    }
                }
                newParams.isOutboundProxyEnabled = outboundProxyEnabled.value == true

                if (::natPolicy.isInitialized) {
                    Log.i("$TAG Also applying changes to NAT policy")
                    natPolicy.stunServer = stunServer.value
                    natPolicy.isStunEnabled = stunServer.value.orEmpty().isNotEmpty()
                    natPolicy.isIceEnabled = iceEnabled.value == true
                    newParams.natPolicy = natPolicy
                }

                newParams.avpfMode = if (avpfEnabled.value == true) AVPFMode.Enabled else AVPFMode.Disabled

                newParams.isRtpBundleEnabled = bundleModeEnabled.value == true

                newParams.expires = expire.value?.toInt() ?: 31536000

                val conferenceFactoryAddress = core.interpretUrl(
                    conferenceFactoryUri.value.orEmpty(),
                    false
                )
                newParams.conferenceFactoryAddress = conferenceFactoryAddress

                val audioVideoConferenceFactoryAddress = core.interpretUrl(
                    audioVideoConferenceFactoryUri.value.orEmpty(),
                    false
                )
                newParams.audioVideoConferenceFactoryAddress = audioVideoConferenceFactoryAddress

                newParams.limeServerUrl = limeServerUrl.value

                account.params = newParams
                Log.i("$TAG Changes have been saved")
            }
        }
    }
}

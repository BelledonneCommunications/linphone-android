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
import org.linphone.activities.main.settings.SettingListenerStub

class NetworkSettingsViewModel : GenericSettingsViewModel() {
    val wifiOnlyListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isWifiOnlyEnabled = newValue
        }
    }
    val wifiOnly = MutableLiveData<Boolean>()

    val allowIpv6Listener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isIpv6Enabled = newValue
        }
    }
    val allowIpv6 = MutableLiveData<Boolean>()

    val randomPortsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val port = if (newValue) -1 else 5060
            setTransportPort(port)
            sipPort.value = port
        }
    }
    val randomPorts = MutableLiveData<Boolean>()

    val sipPortListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val port = newValue.toInt()
                setTransportPort(port)
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val sipPort = MutableLiveData<Int>()

    init {
        wifiOnly.value = core.isWifiOnlyEnabled
        allowIpv6.value = core.isIpv6Enabled
        randomPorts.value = getTransportPort() == -1
        sipPort.value = getTransportPort()
    }

    private fun setTransportPort(port: Int) {
        val transports = core.transports
        transports.udpPort = port
        transports.tcpPort = port
        transports.tlsPort = -1
        core.transports = transports
    }

    private fun getTransportPort(): Int {
        val transports = core.transports
        if (transports.udpPort > 0) return transports.udpPort
        return transports.tcpPort
    }
}

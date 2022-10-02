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
import org.linphone.core.Factory
import org.linphone.core.Tunnel
import org.linphone.core.TunnelConfig

class TunnelSettingsViewModel : GenericSettingsViewModel() {
    val hostnameUrlListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val config = getTunnelConfig()
            config.host = newValue
            updateTunnelConfig(config)
        }
    }
    val hostnameUrl = MutableLiveData<String>()

    val portListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val config = getTunnelConfig()
                config.port = newValue.toInt()
                updateTunnelConfig(config)
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val port = MutableLiveData<Int>()

    val useDualModeListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val tunnel = core.tunnel
            tunnel?.isDualModeEnabled = newValue
        }
    }
    val useDualMode = MutableLiveData<Boolean>()

    val hostnameUrl2Listener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val config = getTunnelConfig()
            config.host2 = newValue
            updateTunnelConfig(config)
        }
    }
    val hostnameUrl2 = MutableLiveData<String>()

    val port2Listener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val config = getTunnelConfig()
                config.port2 = newValue.toInt()
                updateTunnelConfig(config)
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val port2 = MutableLiveData<Int>()

    val modeListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.tunnel?.mode = when (position) {
                0 -> Tunnel.Mode.Disable
                1 -> Tunnel.Mode.Enable
                else -> Tunnel.Mode.Auto
            }
        }
    }
    val modeIndex = MutableLiveData<Int>()
    val modeLabels = MutableLiveData<ArrayList<String>>()

    init {
        val tunnel = core.tunnel
        val config = getTunnelConfig()

        hostnameUrl.value = config.host
        port.value = config.port
        useDualMode.value = tunnel?.isDualModeEnabled
        hostnameUrl2.value = config.host2
        port2.value = config.port2

        initModeList()
    }

    private fun getTunnelConfig(): TunnelConfig {
        val tunnel = core.tunnel
        val configs = tunnel?.servers.orEmpty()
        return if (configs.isNotEmpty()) {
            configs.first()
        } else {
            Factory.instance().createTunnelConfig()
        }
    }

    private fun updateTunnelConfig(config: TunnelConfig) {
        val tunnel = core.tunnel
        tunnel?.cleanServers()
        if (config.host?.isNotEmpty() == true) {
            tunnel?.addServer(config)
        }
    }

    private fun initModeList() {
        val labels = arrayListOf<String>()
        labels.add(prefs.getString(R.string.tunnel_settings_disabled_mode))
        labels.add(prefs.getString(R.string.tunnel_settings_always_mode))
        labels.add(prefs.getString(R.string.tunnel_settings_auto_mode))
        modeLabels.value = labels

        modeIndex.value = when (core.tunnel?.mode) {
            Tunnel.Mode.Disable -> 0
            Tunnel.Mode.Enable -> 1
            else -> 2
        }
    }
}

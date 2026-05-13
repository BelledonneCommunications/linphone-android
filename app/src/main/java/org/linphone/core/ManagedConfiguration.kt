/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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
package org.linphone.core

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log
import org.linphone.utils.Event

object ManagedConfiguration {
    private const val TAG = "[Managed Configuration]"

    const val KEY_CONFIG_URI = "config-uri"
    const val KEY_XML_CONFIG = "xml-config"
    const val KEY_ROOT_CA = "root-ca"

    @WorkerThread
    fun getRestrictions(context: Context): Bundle? {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return null
        return try {
            rm.applicationRestrictions
        } catch (e: Exception) {
            Log.e("$TAG Failed to read application restrictions: $e")
            null
        }
    }

    @WorkerThread
    fun applyMdmConfigToCore(context: Context, core: Core) {
        val restrictions = getRestrictions(context) ?: return
        if (restrictions.isEmpty) {
            if (corePreferences.isMdmConfigured) {
                resetConfig(core)
                corePreferences.isMdmConfigured = false
            }
            return
        }

        corePreferences.isMdmConfigured = true
        
        val configUri = restrictions.getString(KEY_CONFIG_URI).orEmpty()
        val rootCa = restrictions.getString(KEY_ROOT_CA).orEmpty()
        val xmlConfig = restrictions.getString(KEY_XML_CONFIG).orEmpty()

        val currentProvisioningUri = core.provisioningUri

        if (xmlConfig.isNotBlank()) {
            Log.w("$TAG Updating  configuration from managed configuration xml-config = [$xmlConfig]")
            core.config.loadFromXmlString(xmlConfig)
        }

        if (rootCa.isNotBlank() && rootCa != core.rootCa) {
            Log.w("$TAG Updating root CA from managed configuration $rootCa")
            core.rootCa = rootCa
        }

        if (configUri.isNotBlank() && configUri != currentProvisioningUri) { // takes priority over potential config-uri set in xml-config
            Log.w("$TAG Updating provisioning URI from managed configuration $configUri")
            core.provisioningUri = configUri
        }

        if (core.globalState == GlobalState.On) {
            Log.w("$TAG Restarting Core to apply managed configuration changes")
            core.stop()
            core.start()
        }

        coreContext.mdmConfigAppliedEvent.postValue(Event(true))
    }

    @WorkerThread
    fun resetConfig(core: Core) {
        Log.w("$TAG managed configuration was removed. Clearing app")
        var startCore = false
        if (core.globalState == GlobalState.On) {
            core.stop()
            startCore = true
        }
        corePreferences.resetConfigToDefault()
        core.config.reload()
        if (startCore) {
            core.start()
        }

        coreContext.mdmConfigRemovedEvent.postValue(Event(true))
    }
}

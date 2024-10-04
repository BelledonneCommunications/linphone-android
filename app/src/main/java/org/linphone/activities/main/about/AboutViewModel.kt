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
package org.linphone.activities.main.about

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.models.UserInfo

class AboutViewModel : ViewModel() {
    private val prefs = corePreferences

    val appVersion: String = coreContext.appVersion

    val sdkVersion: String = coreContext.sdkVersion

    val sipLogModeListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.debugLogs = newValue
        }
    }
    val sipLogMode = MutableLiveData<Boolean>()

    init {
        sipLogMode.value = prefs.debugLogs
    }

    var user: UserInfo = UserInfo()
    var region: String? = null
}

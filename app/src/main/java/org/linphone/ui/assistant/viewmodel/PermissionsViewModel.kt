/*
 * Copyright (c) 2010-2025 Belledonne Communications SARL.
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
package org.linphone.ui.assistant.viewmodel

import android.Manifest
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel

class PermissionsViewModel
@UiThread
constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Permissions ViewModel]"
    }

    val cameraPermissionGranted = MutableLiveData<Boolean>()

    val recordAudioPermissionGranted = MutableLiveData<Boolean>()

    val readContactsPermissionGranted = MutableLiveData<Boolean>()

    val postNotificationsPermissionGranted = MutableLiveData<Boolean>()

    fun setPermissionGranted(permission: String, granted: Boolean) {
        Log.i("$TAG Permission [$permission] is ${if (granted) "granted" else "not granted yet/denied"}")
        when (permission) {
            Manifest.permission.READ_CONTACTS -> readContactsPermissionGranted.postValue(granted)
            Manifest.permission.RECORD_AUDIO -> recordAudioPermissionGranted.postValue(granted)
            Manifest.permission.CAMERA -> cameraPermissionGranted.postValue(granted)
            Manifest.permission.POST_NOTIFICATIONS -> postNotificationsPermissionGranted.postValue(granted)
        }
    }
}

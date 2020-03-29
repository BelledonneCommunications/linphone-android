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
package org.linphone.activities.call

import android.annotation.TargetApi
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.call.viewmodels.IncomingCallViewModel
import org.linphone.activities.call.viewmodels.IncomingCallViewModelFactory
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.databinding.CallIncomingActivityBinding
import org.linphone.mediastream.Version
import org.linphone.utils.PermissionHelper

class IncomingCallActivity : GenericActivity() {
    private lateinit var binding: CallIncomingActivityBinding
    private lateinit var viewModel: IncomingCallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.call_incoming_activity)
        binding.lifecycleOwner = this

        var incomingCall: Call? = null
        for (call in coreContext.core.calls) {
            if (call.state == Call.State.IncomingReceived ||
                call.state == Call.State.IncomingEarlyMedia) {
                incomingCall = call
            }
        }

        if (incomingCall == null) {
            Log.e("[Incoming Call] Couldn't find call in state Incoming")
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            IncomingCallViewModelFactory(incomingCall)
        )[IncomingCallViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.callEndedEvent.observe(this, Observer {
            it.consume {
                finish()
            }
        })

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        viewModel.screenLocked.value = keyguardManager.isKeyguardLocked

        binding.buttons.setViewModel(viewModel)

        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            checkPermissions()
        }
    }

    @TargetApi(Version.API23_MARSHMALLOW_60)
    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Incoming Call] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (viewModel.call.currentParams.videoEnabled() && !PermissionHelper.get().hasCameraPermission()) {
            Log.i("[Incoming Call] Asking for CAMERA permission")
            permissionsRequiredList.add(android.Manifest.permission.CAMERA)
        }
        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }
}

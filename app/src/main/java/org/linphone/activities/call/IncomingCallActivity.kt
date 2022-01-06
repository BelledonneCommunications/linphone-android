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

import android.Manifest
import android.annotation.TargetApi
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.call.viewmodels.IncomingCallViewModel
import org.linphone.activities.call.viewmodels.IncomingCallViewModelFactory
import org.linphone.activities.main.MainActivity
import org.linphone.compatibility.Compatibility
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

        Compatibility.setShowWhenLocked(this, true)
        Compatibility.setTurnScreenOn(this, true)
        // Leaks on API 27+: https://stackoverflow.com/questions/60477120/keyguardmanager-memory-leak
        Compatibility.requestDismissKeyguard(this)

        binding = DataBindingUtil.setContentView(this, R.layout.call_incoming_activity)
        binding.lifecycleOwner = this

        val incomingCall: Call? = findIncomingCall()
        if (incomingCall == null) {
            Log.e("[Incoming Call Activity] Couldn't find call in state Incoming")
            if (isTaskRoot) {
                // When resuming app from recent tasks make sure MainActivity will be launched if there is no call
                val intent = Intent()
                intent.setClass(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                finish()
            }
            return
        }

        viewModel = ViewModelProvider(
            this,
            IncomingCallViewModelFactory(incomingCall)
        )[IncomingCallViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.callEndedEvent.observe(
            this,
            {
                it.consume {
                    Log.i("[Incoming Call Activity] Call ended, finish activity")
                    finish()
                }
            }
        )

        viewModel.earlyMediaVideoEnabled.observe(
            this,
            {
                if (it) {
                    Log.i("[Incoming Call Activity] Early media video being received, set native window id")
                    coreContext.core.nativeVideoWindowId = binding.remoteVideoSurface
                }
            }
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val keyguardLocked = keyguardManager.isKeyguardLocked
        viewModel.screenLocked.value = keyguardLocked
        if (keyguardLocked) {
            // Forbid screen rotation to prevent keyguard to show up above incoming call view
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }

        binding.buttons.setViewModel(viewModel)

        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            checkPermissions()
        }
    }

    override fun onResume() {
        super.onResume()

        val incomingCall: Call? = findIncomingCall()
        if (incomingCall == null) {
            Log.e("[Incoming Call Activity] Couldn't find call in state Incoming")
            if (isTaskRoot) {
                // When resuming app from recent tasks make sure MainActivity will be launched if there is no call
                val intent = Intent()
                intent.setClass(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                finish()
            }
        }
    }

    @TargetApi(Version.API23_MARSHMALLOW_60)
    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Incoming Call Activity] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(Manifest.permission.RECORD_AUDIO)
        }

        if (viewModel.call.currentParams.isVideoEnabled && !PermissionHelper.get().hasCameraPermission()) {
            Log.i("[Incoming Call Activity] Asking for CAMERA permission")
            permissionsRequiredList.add(Manifest.permission.CAMERA)
        }

        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Incoming Call Activity] RECORD_AUDIO permission has been granted")
                    }
                    Manifest.permission.CAMERA -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Incoming Call Activity] CAMERA permission has been granted")
                        coreContext.core.reloadVideoDevices()
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun findIncomingCall(): Call? {
        for (call in coreContext.core.calls) {
            if (call.state == Call.State.IncomingReceived ||
                call.state == Call.State.IncomingEarlyMedia
            ) {
                return call
            }
        }
        return null
    }
}

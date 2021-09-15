/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.voip

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.window.layout.FoldingFeature
import org.linphone.LinphoneApplication
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.ProximitySensorActivity
import org.linphone.activities.main.MainActivity
import org.linphone.activities.navigateToActiveCall
import org.linphone.activities.navigateToIncomingCall
import org.linphone.activities.navigateToOutgoingCall
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipActivityBinding
import org.linphone.mediastream.Version
import org.linphone.utils.PermissionHelper

class CallActivity : ProximitySensorActivity() {
    private lateinit var binding: VoipActivityBinding
    private lateinit var controlsViewModel: ControlsViewModel
    private lateinit var callsViewModel: CallsViewModel

    private var foldingFeature: FoldingFeature? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Compatibility.setShowWhenLocked(this, true)
        Compatibility.setTurnScreenOn(this, true)
        // Leaks on API 27+: https://stackoverflow.com/questions/60477120/keyguardmanager-memory-leak
        Compatibility.requestDismissKeyguard(this)

        binding = DataBindingUtil.setContentView(this, R.layout.voip_activity)
        binding.lifecycleOwner = this
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // This can't be done in onCreate(), has to be at least in onPostCreate() !
        val navController = binding.navHostFragment.findNavController()
        val navControllerStoreOwner = navController.getViewModelStoreOwner(R.id.call_nav_graph)

        controlsViewModel = ViewModelProvider(navControllerStoreOwner)[ControlsViewModel::class.java]
        binding.controlsViewModel = controlsViewModel

        callsViewModel = ViewModelProvider(navControllerStoreOwner)[CallsViewModel::class.java]

        callsViewModel.noMoreCallEvent.observe(
            this
        ) {
            it.consume {
                finish()
            }
        }

        controlsViewModel.askPermissionEvent.observe(
            this
        ) {
            it.consume { permission ->
                Log.i("[Call] Asking for $permission permission")
                requestPermissions(arrayOf(permission), 0)
            }
        }

        controlsViewModel.fullScreenMode.observe(
            this
        ) { hide ->
            Compatibility.hideAndroidSystemUI(hide, window)
        }

        controlsViewModel.proximitySensorEnabled.observe(
            this
        ) { enabled ->
            enableProximitySensor(enabled)
        }

        controlsViewModel.isVideoEnabled.observe(
            this
        ) { enabled ->
            Compatibility.enableAutoEnterPiP(this, enabled)
        }

        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            checkPermissions()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (coreContext.core.currentCall?.currentParams?.isVideoEnabled ?: false) {
            Log.i("[Call] Entering PiP mode")
            Compatibility.enterPipMode(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        Log.i("[Call] Activity is in PiP mode? $isInPictureInPictureMode")
        if (::controlsViewModel.isInitialized) {
            // To hide UI except for TextureViews
            controlsViewModel.pipMode.value = isInPictureInPictureMode
        }
    }

    override fun onResume() {
        super.onResume()

        if (coreContext.core.callsNb == 0) {
            Log.w("[Call] Resuming but no call found...")
            if (isTaskRoot) {
                // When resuming app from recent tasks make sure MainActivity will be launched if there is no call
                val intent = Intent()
                intent.setClass(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                finish()
            }
        } else {
            coreContext.removeCallOverlay()

            val currentCall = coreContext.core.currentCall
            if (currentCall == null) {
                Log.e("[Call] No current call found, assume active call")
                navigateToActiveCall()
                return
            }

            when (currentCall.state) {
                Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> {
                    navigateToOutgoingCall()
                }
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    val earlyMediaVideoEnabled = LinphoneApplication.corePreferences.acceptEarlyMedia &&
                        currentCall.state == Call.State.IncomingEarlyMedia &&
                        currentCall.currentParams.isVideoEnabled
                    navigateToIncomingCall(earlyMediaVideoEnabled)
                }
                else -> navigateToActiveCall()
            }
        }
    }

    override fun onPause() {
        val core = coreContext.core
        if (core.callsNb > 0) {
            coreContext.createCallOverlay()
        }

        super.onPause()
    }

    override fun onDestroy() {
        coreContext.core.nativeVideoWindowId = null
        coreContext.core.nativePreviewWindowId = null

        super.onDestroy()
    }

    @TargetApi(Version.API23_MARSHMALLOW_60)
    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Call] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(Manifest.permission.RECORD_AUDIO)
        }
        if (callsViewModel.currentCallData.value?.call?.currentParams?.isVideoEnabled == true &&
            !PermissionHelper.get().hasCameraPermission()
        ) {
            Log.i("[Call] Asking for CAMERA permission")
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
                        Log.i("[Call] RECORD_AUDIO permission has been granted")
                        controlsViewModel.updateMicState()
                    }
                    Manifest.permission.CAMERA -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[Call] CAMERA permission has been granted")
                        coreContext.core.reloadVideoDevices()
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLayoutChanges(foldingFeature: FoldingFeature?) {
        this.foldingFeature = foldingFeature
        updateConstraintSetDependingOnFoldingState()
    }

    private fun updateConstraintSetDependingOnFoldingState() {
        /*val feature = foldingFeature ?: return
        val constraintLayout = binding.constraintLayout
        val set = ConstraintSet()
        set.clone(constraintLayout)

        if (feature.state == FoldingFeature.State.HALF_OPENED && viewModel.videoEnabled.value == true) {
            set.setGuidelinePercent(R.id.hinge_top, 0.5f)
            set.setGuidelinePercent(R.id.hinge_bottom, 0.5f)
            viewModel.disable(true)
        } else {
            set.setGuidelinePercent(R.id.hinge_top, 0f)
            set.setGuidelinePercent(R.id.hinge_bottom, 1f)
            viewModel.disable(false)
        }

        set.applyTo(constraintLayout)*/
    }
}

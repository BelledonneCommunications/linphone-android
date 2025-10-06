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
package org.linphone.ui.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Api28Compatibility
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.CallActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.call.conference.fragment.ActiveConferenceCallFragmentDirections
import org.linphone.ui.call.conference.fragment.ConferenceLayoutMenuDialogFragment
import org.linphone.ui.call.fragment.ActiveCallFragmentDirections
import org.linphone.ui.call.fragment.AudioDevicesMenuDialogFragment
import org.linphone.ui.call.fragment.CallsListFragmentDirections
import org.linphone.ui.call.fragment.IncomingCallFragmentDirections
import org.linphone.ui.call.fragment.OutgoingCallFragmentDirections
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.call.viewmodel.CallsViewModel
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.ui.call.viewmodel.SharedCallViewModel
import org.linphone.ui.main.MainActivity

@UiThread
class CallActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Call Activity]"
    }

    private lateinit var binding: CallActivityBinding

    private lateinit var sharedViewModel: SharedCallViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var callViewModel: CurrentCallViewModel

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private var isPipSupported = false

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG CAMERA permission has been granted, enabling video")
            callViewModel.toggleVideo()
        } else {
            Log.e("$TAG CAMERA permission has been denied")
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG RECORD_AUDIO permission has been granted, un-muting microphone")
            callViewModel.toggleMuteMicrophone()
        } else {
            Log.e("$TAG RECORD_AUDIO permission has been denied")
        }
    }

    override fun getTheme(): Resources.Theme {
        val mainColor = corePreferences.themeMainColor
        val theme = super.getTheme()
        when (mainColor) {
            "yellow" -> theme.applyStyle(R.style.Theme_LinphoneInCallYellow, true)
            "green" -> theme.applyStyle(R.style.Theme_LinphoneInCallGreen, true)
            "blue" -> theme.applyStyle(R.style.Theme_LinphoneInCallBlue, true)
            "red" -> theme.applyStyle(R.style.Theme_LinphoneInCallRed, true)
            "pink" -> theme.applyStyle(R.style.Theme_LinphoneInCallPink, true)
            "purple" -> theme.applyStyle(R.style.Theme_LinphoneInCallPurple, true)
            else -> theme.applyStyle(R.style.Theme_LinphoneInCall, true)
        }
        return theme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
            true // Force dark mode
        }
        enableEdgeToEdge(style, style)
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.call_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        ViewCompat.setOnApplyWindowInsetsListener(binding.otherCallsTopBar.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(0, insets.top, 0, 0)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.callNavContainer) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(insets.left, 0, insets.right, max(insets.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }

        lifecycleScope.launch(Dispatchers.Main) {
            WindowInfoTracker
                .getOrCreate(this@CallActivity)
                .windowLayoutInfo(this@CallActivity)
                .collect { newLayoutInfo ->
                    updateCurrentLayout(newLayoutInfo)
                }
        }

        isPipSupported = packageManager.hasSystemFeature(
            PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
        Log.i("$TAG Is PiP supported [$isPipSupported]")

        sharedViewModel = run {
            ViewModelProvider(this)[SharedCallViewModel::class.java]
        }

        callViewModel = run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        binding.callViewModel = callViewModel

        callsViewModel = run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }
        binding.callsViewModel = callsViewModel

        callViewModel.showAudioDevicesListEvent.observe(this) {
            it.consume { devices ->
                showAudioRoutesMenu(devices)
            }
        }

        callViewModel.conferenceModel.showLayoutMenuEvent.observe(this) {
            it.consume {
                showConferenceLayoutMenu()
            }
        }

        callViewModel.isVideoEnabled.observe(this) { enabled ->
            if (isPipSupported) {
                // Only enable PiP if video is enabled
                Compatibility.enableAutoEnterPiP(this, enabled)
            }
        }

        callViewModel.transferInProgressEvent.observe(this) {
            it.consume {
                showGreenToast(
                    getString(R.string.call_transfer_in_progress_toast),
                    R.drawable.phone_transfer
                )
            }
        }

        callViewModel.transferFailedEvent.observe(this) {
            it.consume {
                showRedToast(
                    getString(R.string.call_transfer_failed_toast),
                    R.drawable.warning_circle
                )
            }
        }

        callViewModel.goToEndedCallEvent.observe(this) {
            it.consume { message ->
                if (message.isNotEmpty()) {
                    showRedToast(message, R.drawable.warning_circle)
                }

                val action = ActiveCallFragmentDirections.actionGlobalEndedCallFragment()
                findNavController(R.id.call_nav_container).navigate(action)
            }
        }

        callViewModel.finishActivityEvent.observe(this) {
            it.consume {
                Log.i("$TAG Finishing activity")
                finish()
            }
        }

        callViewModel.requestRecordAudioPermission.observe(this) {
            it.consume {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    Log.w("$TAG Asking for RECORD_AUDIO permission")
                    requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    Log.i("$TAG Permission request for RECORD_AUDIO will be automatically denied, go to android app settings instead")
                    goToAndroidPermissionSettings()
                }
            }
        }

        callViewModel.requestCameraPermission.observe(this) {
            it.consume {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    Log.w("$TAG Asking for CAMERA permission")
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    Log.i("$TAG Permission request for CAMERA will be automatically denied, go to android app settings instead")
                    goToAndroidPermissionSettings()
                }
            }
        }

        callViewModel.proximitySensorEnabled.observe(this) { enabled ->
            Log.i("$TAG ${if (enabled) "Enabling" else "Disabling"} proximity sensor")
            coreContext.enableProximitySensor(enabled)
        }

        callsViewModel.showIncomingCallEvent.observe(this) {
            it.consume {
                val action = IncomingCallFragmentDirections.actionGlobalIncomingCallFragment()
                findNavController(R.id.call_nav_container).navigate(action)
            }
        }

        callsViewModel.showOutgoingCallEvent.observe(this) {
            it.consume {
                val action = OutgoingCallFragmentDirections.actionGlobalOutgoingCallFragment()
                findNavController(R.id.call_nav_container).navigate(action)
            }
        }

        callsViewModel.goToActiveCallEvent.observe(this) {
            it.consume { singleCall ->
                navigateToActiveCall(singleCall)
            }
        }

        callsViewModel.noCallFoundEvent.observe(this) {
            it.consume {
                finish()
            }
        }

        callsViewModel.goToCallsListEvent.observe(this) {
            it.consume {
                val navController = findNavController(R.id.call_nav_container)
                if (navController.currentDestination?.id == R.id.activeCallFragment) {
                    val action =
                        ActiveCallFragmentDirections.actionActiveCallFragmentToCallsListFragment()
                    navController.navigate(action)
                } else if (navController.currentDestination?.id == R.id.activeConferenceCallFragment) {
                    val action =
                        ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToCallsListFragment()
                    navController.navigate(action)
                }
            }
        }

        sharedViewModel.toggleFullScreenEvent.observe(this) {
            it.consume { hide ->
                hideUI(hide)
            }
        }

        coreContext.refreshMicrophoneMuteStateEvent.observe(this) {
            it.consume {
                Log.i(
                    "$TAG Refreshing microphone mute state, probably to sync with Android Auto action"
                )
                callViewModel.refreshMicrophoneState()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        findNavController(R.id.call_nav_container).addOnDestinationChangedListener { _, destination, _ ->
            val showTopBar = when (destination.id) {
                R.id.inCallConversationFragment, R.id.transferCallFragment, R.id.newCallFragment -> true
                else -> false
            }
            callsViewModel.showTopBar.postValue(showTopBar)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("$TAG RECORD_AUDIO permission isn't granted")
            val message = R.string.call_audio_record_permission_not_granted_toast
            val icon = R.drawable.warning_circle
            showRedToast(getString(message), icon)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("$TAG CAMERA permission isn't granted")
            val message = R.string.call_camera_permission_not_granted_toast
            val icon = R.drawable.warning_circle
            showRedToast(getString(message), icon)
        }
    }

    override fun onResume() {
        super.onResume()

        val isInPipMode = isInPictureInPictureMode
        Log.i("$TAG onResume: is in PiP mode? [$isInPipMode]")
        if (::callViewModel.isInitialized) {
            callViewModel.pipMode.value = isInPipMode
        }
    }

    override fun onPause() {
        coreContext.enableProximitySensor(false)

        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    override fun onDestroy() {
        coreContext.enableProximitySensor(false)

        super.onDestroy()

        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Clearing native video window ID")
            core.nativeVideoWindowId = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.extras?.getBoolean("ActiveCall", false) == true) {
            navigateToActiveCall(
                callViewModel.conferenceModel.isCurrentCallInConference.value == false
            )
        } else if (intent.extras?.getBoolean("IncomingCall", false) == true) {
            val action = IncomingCallFragmentDirections.actionGlobalIncomingCallFragment()
            findNavController(R.id.call_nav_container).navigate(action)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (::callViewModel.isInitialized) {
            if (isPipSupported && callViewModel.isVideoEnabled.value == true) {
                Log.i("$TAG User leave hint, try entering PiP mode")
                val pipMode = Compatibility.enterPipMode(this)
                if (!pipMode) {
                    Log.e("$TAG Failed to enter PiP mode")
                    callViewModel.pipMode.value = false
                }
            }
        }
    }

    @UiThread
    fun goToMainActivity() {
        if (isPipSupported && callViewModel.isVideoEnabled.value == true) {
            Log.i("$TAG User is going back to MainActivity, try entering PiP mode")
            val pipMode = Api28Compatibility.enterPipMode(this)
            if (!pipMode) {
                Log.e("$TAG Failed to enter PiP mode, finishing Activity")
                callViewModel.pipMode.value = false
                finish()
                return
            }

            Log.i("$TAG Launching MainActivity to have PiP above it")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        } else {
            Log.i("$TAG Either PiP isn't supported or video is not enabled, finishing Activity")
            finish()
        }
    }

    private fun updateCurrentLayout(newLayoutInfo: WindowLayoutInfo) {
        if (newLayoutInfo.displayFeatures.isNotEmpty()) {
            for (feature in newLayoutInfo.displayFeatures) {
                val foldingFeature = feature as? FoldingFeature
                if (foldingFeature != null) {
                    Log.i(
                        "$TAG Folding feature state changed: ${foldingFeature.state}, orientation is ${foldingFeature.orientation}"
                    )
                    sharedViewModel.foldingState.value = foldingFeature
                }
            }
        }
    }

    private fun navigateToActiveCall(notInConference: Boolean) {
        val navController = findNavController(R.id.call_nav_container)
        val action = when (navController.currentDestination?.id) {
            R.id.outgoingCallFragment -> {
                if (notInConference) {
                    Log.i("$TAG Going from outgoing call fragment to call fragment")
                    OutgoingCallFragmentDirections.actionOutgoingCallFragmentToActiveCallFragment()
                } else {
                    Log.i(
                        "$TAG Going from outgoing call fragment to conference call fragment"
                    )
                    OutgoingCallFragmentDirections.actionOutgoingCallFragmentToActiveConferenceCallFragment()
                }
            }
            R.id.incomingCallFragment -> {
                if (notInConference) {
                    Log.i("$TAG Going from incoming call fragment to call fragment")
                    IncomingCallFragmentDirections.actionIncomingCallFragmentToActiveCallFragment()
                } else {
                    Log.i(
                        "$TAG Going from incoming call fragment to conference call fragment"
                    )
                    IncomingCallFragmentDirections.actionIncomingCallFragmentToActiveConferenceCallFragment()
                }
            }
            R.id.activeCallFragment -> {
                if (notInConference) {
                    Log.i("$TAG Going from call fragment to call fragment")
                    ActiveCallFragmentDirections.actionGlobalActiveCallFragment()
                } else {
                    Log.i("$TAG Going from call fragment to conference call fragment")
                    ActiveCallFragmentDirections.actionActiveCallFragmentToActiveConferenceCallFragment()
                }
            }
            R.id.activeConferenceCallFragment -> {
                if (notInConference) {
                    Log.i("$TAG Going from conference call fragment to call fragment")
                    ActiveConferenceCallFragmentDirections.actionActiveConferenceCallFragmentToActiveCallFragment()
                } else {
                    Log.i(
                        "$TAG Going from conference call fragment to conference call fragment"
                    )
                    ActiveConferenceCallFragmentDirections.actionGlobalActiveConferenceCallFragment()
                }
            }
            R.id.callsListFragment -> {
                if (notInConference) {
                    Log.i("$TAG Going calls list fragment to active call fragment")
                    CallsListFragmentDirections.actionCallsListFragmentToActiveCallFragment()
                } else {
                    Log.i("$TAG Going calls list fragment to conference fragment")
                    CallsListFragmentDirections.actionCallsListFragmentToActiveConferenceCallFragment()
                }
            }
            else -> {
                if (notInConference) {
                    Log.i("$TAG Going from call fragment to call fragment")
                    ActiveCallFragmentDirections.actionGlobalActiveCallFragment()
                } else {
                    Log.i("$TAG Going from call fragment to conference call fragment")
                    ActiveConferenceCallFragmentDirections.actionGlobalActiveConferenceCallFragment()
                }
            }
        }
        navController.navigate(action)
    }

    private fun hideUI(hide: Boolean) {
        Log.i("$TAG Switching full screen mode to [${if (hide) "ON" else "OFF"}]")
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showAudioRoutesMenu(devicesList: List<AudioDeviceModel>) {
        val modalBottomSheet = AudioDevicesMenuDialogFragment(devicesList)
        modalBottomSheet.show(supportFragmentManager, AudioDevicesMenuDialogFragment.TAG)
        bottomSheetDialog = modalBottomSheet
    }

    private fun showConferenceLayoutMenu() {
        val modalBottomSheet = ConferenceLayoutMenuDialogFragment(callViewModel.conferenceModel)
        modalBottomSheet.show(supportFragmentManager, ConferenceLayoutMenuDialogFragment.TAG)
        bottomSheetDialog = modalBottomSheet
    }
}

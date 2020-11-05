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
package org.linphone.activities.call.fragments

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.call.viewmodels.CallsViewModel
import org.linphone.activities.call.viewmodels.ControlsViewModel
import org.linphone.activities.call.viewmodels.SharedCallViewModel
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.core.Call
import org.linphone.core.tools.Log
import org.linphone.databinding.CallControlsFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.PermissionHelper

class ControlsFragment : GenericFragment<CallControlsFragmentBinding>() {
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var controlsViewModel: ControlsViewModel
    private lateinit var sharedViewModel: SharedCallViewModel

    private var dialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.call_controls_fragment

    private val bounceAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(resources.getDimension(R.dimen.tabs_fragment_unread_count_bounce_offset), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                view?.findViewById<TextView>(R.id.chat_unread_count)?.translationY = -value
            }
            interpolator = LinearInterpolator()
            duration = 250
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    private val optionsMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(resources.getDimension(R.dimen.call_options_menu_translate_y), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                view?.findViewById<LinearLayout>(R.id.options_menu)?.translationY = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    private val audioRoutesMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(resources.getDimension(R.dimen.call_audio_routes_menu_translate_y), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                view?.findViewById<LinearLayout>(R.id.audio_routes_menu)?.translationY = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedCallViewModel::class.java)
        }

        callsViewModel = ViewModelProvider(this).get(CallsViewModel::class.java)
        binding.viewModel = callsViewModel

        controlsViewModel = ViewModelProvider(this).get(ControlsViewModel::class.java)
        binding.controlsViewModel = controlsViewModel

        callsViewModel.currentCallViewModel.observe(viewLifecycleOwner, {
            if (it != null) {
                binding.activeCallTimer.base =
                    SystemClock.elapsedRealtime() - (1000 * it.call.duration) // Linphone timestamps are in seconds
                binding.activeCallTimer.start()
            }
        })

        callsViewModel.noMoreCallEvent.observe(viewLifecycleOwner, {
            it.consume {
                requireActivity().finish()
            }
        })

        callsViewModel.callUpdateEvent.observe(viewLifecycleOwner, {
            it.consume { call ->
                if (call.state == Call.State.StreamsRunning) {
                    dialog?.dismiss()
                } else if (call.state == Call.State.UpdatedByRemote) {
                    showCallUpdateDialog(call)
                }
            }
        })

        controlsViewModel.chatClickedEvent.observe(viewLifecycleOwner, {
            it.consume {
                val intent = Intent()
                intent.setClass(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                intent.putExtra("Chat", true)
                startActivity(intent)
            }
        })

        controlsViewModel.addCallClickedEvent.observe(viewLifecycleOwner, {
            it.consume {
                val intent = Intent()
                intent.setClass(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                intent.putExtra("Dialer", true)
                intent.putExtra("Transfer", false)
                startActivity(intent)
            }
        })

        controlsViewModel.transferCallClickedEvent.observe(viewLifecycleOwner, {
            it.consume {
                val intent = Intent()
                intent.setClass(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                intent.putExtra("Dialer", true)
                intent.putExtra("Transfer", true)
                startActivity(intent)
            }
        })

        controlsViewModel.askPermissionEvent.observe(viewLifecycleOwner, {
            it.consume { permission ->
                Log.i("[Controls Fragment] Asking for $permission permission")
                requestPermissions(arrayOf(permission), 0)
            }
        })

        controlsViewModel.toggleOptionsMenuEvent.observe(viewLifecycleOwner, {
            it.consume { open ->
                if (open) {
                    optionsMenuAnimator.start()
                } else {
                    optionsMenuAnimator.reverse()
                }
            }
        })

        controlsViewModel.toggleAudioRoutesMenuEvent.observe(viewLifecycleOwner, {
            it.consume { open ->
                if (open) {
                    audioRoutesMenuAnimator.start()
                } else {
                    audioRoutesMenuAnimator.reverse()
                }
            }
        })

        controlsViewModel.somethingClickedEvent.observe(viewLifecycleOwner, {
            it.consume {
                sharedViewModel.resetHiddenInterfaceTimerInVideoCallEvent.value = Event(true)
            }
        })

        if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
            checkPermissions()
        }
    }

    override fun onStart() {
        super.onStart()

        if (corePreferences.enableAnimations) {
            bounceAnimator.start()
        }
    }

    override fun onStop() {
        if (corePreferences.enableAnimations) {
            bounceAnimator.pause()
        }
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> if (grantResults[i] == PERMISSION_GRANTED) {
                        Log.i("[Controls Fragment] RECORD_AUDIO permission has been granted")
                        controlsViewModel.updateMuteMicState()
                    }
                    Manifest.permission.CAMERA -> if (grantResults[i] == PERMISSION_GRANTED) {
                        Log.i("[Controls Fragment] CAMERA permission has been granted")
                        coreContext.core.reloadVideoDevices()
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @TargetApi(Version.API23_MARSHMALLOW_60)
    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()

        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[Controls Fragment] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(android.Manifest.permission.RECORD_AUDIO)
        }

        if (coreContext.isVideoCallOrConferenceActive() && !PermissionHelper.get().hasCameraPermission()) {
            Log.i("[Controls Fragment] Asking for CAMERA permission")
            permissionsRequiredList.add(android.Manifest.permission.CAMERA)
        }

        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }

    private fun showCallUpdateDialog(call: Call) {
        val viewModel = DialogViewModel(AppUtils.getString(R.string.call_video_update_requested_dialog))
        dialog = DialogUtils.getDialog(requireContext(), viewModel)

        viewModel.showCancelButton({
            callsViewModel.answerCallUpdateRequest(call, false)
            dialog?.dismiss()
        }, getString(R.string.dialog_decline))

        viewModel.showOkButton({
            callsViewModel.answerCallUpdateRequest(call, true)
            dialog?.dismiss()
        }, getString(R.string.dialog_accept))

        dialog?.show()
    }
}

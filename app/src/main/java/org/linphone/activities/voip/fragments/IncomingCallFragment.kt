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
package org.linphone.activities.voip.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.navigateToActiveCall
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.activities.voip.viewmodels.ControlsViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipCallIncomingFragmentBinding

class IncomingCallFragment : GenericFragment<VoipCallIncomingFragmentBinding>() {
    private lateinit var controlsViewModel: ControlsViewModel
    private lateinit var callsViewModel: CallsViewModel

    override fun getLayoutId(): Int = R.layout.voip_call_incoming_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Compatibility.setShowWhenLocked(requireActivity(), true)
        Compatibility.setTurnScreenOn(requireActivity(), true)
        // Leaks on API 27+: https://stackoverflow.com/questions/60477120/keyguardmanager-memory-leak
        Compatibility.requestDismissKeyguard(requireActivity())

        binding.lifecycleOwner = viewLifecycleOwner

        controlsViewModel = requireActivity().run {
            ViewModelProvider(this).get(ControlsViewModel::class.java)
        }
        binding.controlsViewModel = controlsViewModel

        callsViewModel = requireActivity().run {
            ViewModelProvider(this).get(CallsViewModel::class.java)
        }
        binding.callsViewModel = callsViewModel

        callsViewModel.callConnectedEvent.observe(
            viewLifecycleOwner,
            {
                navigateToActiveCall()
            }
        )

        val earlyMediaVideo = arguments?.getBoolean("earlyMediaVideo") ?: false
        if (earlyMediaVideo) {
            Log.i("[Incoming Call] Video early media detected, setting native window id")
            coreContext.core.nativeVideoWindowId = binding.remoteVideoSurface
        }
    }
}

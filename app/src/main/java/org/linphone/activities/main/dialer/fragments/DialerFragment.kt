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
package org.linphone.activities.main.dialer.fragments

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.dialer.viewmodels.DialerViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.DialerFragmentBinding

class DialerFragment : Fragment() {
    private lateinit var binding: DialerFragmentBinding
    private lateinit var viewModel: DialerViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialerFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(DialerViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        binding.setEraseClickListener {
            viewModel.eraseLastChar()
        }

        binding.setEraseLongClickListener {
            viewModel.eraseAll()
        }

        binding.setNewContactClickListener {
            val deepLink = "linphone-android://contact/new/${viewModel.enteredUri.value}"
            Log.i("[Dialer] Creating contact, starting deep link: $deepLink")
            findNavController().navigate(Uri.parse(deepLink))
        }

        binding.setStartCallClickListener {
            viewModel.startCall()
        }

        binding.setAddCallClickListener {
            viewModel.startCall()
        }

        binding.setTransferCallClickListener {
            viewModel.transferCall()
            // Transfer has been consumed
            sharedViewModel.pendingCallTransfer = false
            viewModel.transferVisibility.value = false
        }

        if (arguments?.containsKey("Transfer") == true) {
            sharedViewModel.pendingCallTransfer = arguments?.getBoolean("Transfer") ?: false
            Log.i("[Dialer] Is pending call transfer: ${sharedViewModel.pendingCallTransfer}")
        }
        if (arguments?.containsKey("URI") == true) {
            val address = arguments?.getString("URI") ?: ""
            val skipAutoCall = arguments?.getBoolean("SkipAutoCallStart") ?: false

            if (corePreferences.callRightAway && !skipAutoCall) {
                Log.i("[Dialer] Call right away setting is enabled, start the call to $address")
                viewModel.directCall(address)
            } else {
                viewModel.enteredUri.value = address
            }
        }

        viewModel.enteredUri.observe(viewLifecycleOwner, Observer {
            if (it == corePreferences.debugPopupCode) {
                displayDebugPopup()
                viewModel.enteredUri.value = ""
            }
        })

        Log.i("[Dialer] Pending call transfer mode = ${sharedViewModel.pendingCallTransfer}")
        viewModel.transferVisibility.value = sharedViewModel.pendingCallTransfer
    }

    override fun onResume() {
        super.onResume()

        if (resources.getBoolean(R.bool.isTablet)) {
            coreContext.core.nativePreviewWindowId = binding.videoPreviewWindow
        }
        viewModel.updateShowVideoPreview()
    }

    private fun displayDebugPopup() {
        val alertDialog = AlertDialog.Builder(context)
        alertDialog.setTitle(getString(R.string.debug_popup_title))
        if (corePreferences.debugLogs) {
            alertDialog.setItems(resources.getStringArray(R.array.popup_send_log)) { _, which ->
                if (which == 0) {
                    corePreferences.debugLogs = false
                }
                if (which == 1) {
                    // TODO: upload logs
                }
            }
        } else {
            alertDialog.setItems(resources.getStringArray(R.array.popup_enable_log)) { _, which ->
                if (which == 0) {
                    corePreferences.debugLogs = true
                }
            }
        }
        alertDialog.show()
    }
}

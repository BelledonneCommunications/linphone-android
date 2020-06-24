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
package org.linphone.activities.main.history.fragments

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
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.history.viewmodels.CallLogViewModel
import org.linphone.activities.main.history.viewmodels.CallLogViewModelFactory
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.contact.NativeContact
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryDetailFragmentBinding

class DetailCallLogFragment : Fragment() {
    private lateinit var binding: HistoryDetailFragmentBinding
    private lateinit var viewModel: CallLogViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HistoryDetailFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val callLogGroup = sharedViewModel.selectedCallLogGroup.value
        callLogGroup ?: return

        viewModel = ViewModelProvider(
            this,
            CallLogViewModelFactory(callLogGroup.lastCallLog)
        )[CallLogViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.relatedCallLogs.value = callLogGroup.callLogs

        binding.setBackClickListener {
            findNavController().popBackStack()
        }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

        binding.setNewContactClickListener {
            viewModel.callLog.remoteAddress.clean()
            val deepLink = "linphone-android://contact/new/${viewModel.callLog.remoteAddress.asStringUriOnly()}"
            Log.i("[History] Creating contact, starting deep link: $deepLink")
            findNavController().navigate(Uri.parse(deepLink))
        }

        binding.setContactClickListener {
            val contact = viewModel.contact.value as? NativeContact
            if (contact != null) {
                val deepLink = "linphone-android://contact/view/${contact.nativeId}"
                Log.i("[History] Displaying contact, starting deep link: $deepLink")
                findNavController().navigate(Uri.parse(deepLink))
            } else {
                val address = viewModel.callLog.remoteAddress
                address.clean()
                val deepLink = "linphone-android://contact/view-friend/${address.asStringUriOnly()}"
                Log.i("[History] Displaying friend, starting deep link: $deepLink")
                findNavController().navigate(Uri.parse(deepLink))
            }
        }

        viewModel.startCallEvent.observe(viewLifecycleOwner, Observer {
            it.consume { address ->
                if (coreContext.core.callsNb > 0) {
                    Log.i("[History] Starting dialer with pre-filled URI ${address.asStringUriOnly()}, is transfer? ${sharedViewModel.pendingCallTransfer}")
                    val args = Bundle()
                    args.putString("URI", address.asStringUriOnly())
                    args.putBoolean("Transfer", sharedViewModel.pendingCallTransfer)
                    args.putBoolean("SkipAutoCallStart", true) // If auto start call setting is enabled, ignore it
                    findNavController().navigate(
                        R.id.action_global_dialerFragment,
                        args
                    )
                } else {
                    coreContext.startCall(address)
                }
            }
        })

        viewModel.chatRoomCreatedEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatRoom ->
                if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
                    val args = Bundle()
                    args.putString("LocalSipUri", chatRoom.localAddress.asStringUriOnly())
                    args.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
                    findNavController().navigate(R.id.action_global_masterChatRoomsFragment, args)
                }
            }
        })

        viewModel.onErrorEvent.observe(viewLifecycleOwner, Observer {
            it.consume { messageResourceId ->
                (activity as MainActivity).showSnackBar(messageResourceId)
            }
        })
    }
}

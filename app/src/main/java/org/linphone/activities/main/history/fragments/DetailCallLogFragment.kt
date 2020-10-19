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

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionInflater
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.history.viewmodels.CallLogViewModel
import org.linphone.activities.main.history.viewmodels.CallLogViewModelFactory
import org.linphone.activities.main.navigateToContact
import org.linphone.activities.main.navigateToContacts
import org.linphone.activities.main.navigateToFriend
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.contact.NativeContact
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryDetailFragmentBinding

class DetailCallLogFragment : GenericFragment<HistoryDetailFragmentBinding>() {
    private lateinit var viewModel: CallLogViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun getLayoutId(): Int = R.layout.history_detail_fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // For transition animation
        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.move)
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

        // For transition animation
        ViewCompat.setTransitionName(binding.avatar, "avatar_${viewModel.callLog.callId}")
        ViewCompat.setTransitionName(binding.displayName, "display_name_${viewModel.callLog.callId}")

        viewModel.relatedCallLogs.value = callLogGroup.callLogs

        binding.setBackClickListener {
            findNavController().popBackStack()
        }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

        binding.setNewContactClickListener {
            viewModel.callLog.remoteAddress.clean()
            Log.i("[History] Creating contact with SIP URI: ${viewModel.callLog.remoteAddress.asStringUriOnly()}")
            navigateToContacts(viewModel.callLog.remoteAddress.asStringUriOnly())
        }

        binding.setContactClickListener {
            val contact = viewModel.contact.value as? NativeContact
            if (contact != null) {
                Log.i("[History] Displaying contact $contact")
                navigateToContact(contact)
            } else {
                val address = viewModel.callLog.remoteAddress
                address.clean()
                Log.i("[History] Displaying friend with address ${address.asStringUriOnly()}")
                navigateToFriend(address)
            }
        }

        viewModel.startCallEvent.observe(viewLifecycleOwner, {
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

        viewModel.chatRoomCreatedEvent.observe(viewLifecycleOwner, {
            it.consume { chatRoom ->
                if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
                    val args = Bundle()
                    args.putString("LocalSipUri", chatRoom.localAddress.asStringUriOnly())
                    args.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
                    findNavController().navigate(R.id.action_global_masterChatRoomsFragment, args)
                }
            }
        })

        viewModel.onErrorEvent.observe(viewLifecycleOwner, {
            it.consume { messageResourceId ->
                (activity as MainActivity).showSnackBar(messageResourceId)
            }
        })
    }
}

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
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.*
import org.linphone.activities.main.history.viewmodels.CallLogViewModel
import org.linphone.activities.navigateToContact
import org.linphone.activities.navigateToContacts
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryDetailFragmentBinding
import org.linphone.utils.Event

class DetailCallLogFragment : GenericFragment<HistoryDetailFragmentBinding>() {
    private lateinit var viewModel: CallLogViewModel

    override fun getLayoutId(): Int = R.layout.history_detail_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.sharedMainViewModel = sharedViewModel

        val callLogGroup = sharedViewModel.selectedCallLogGroup.value
        if (callLogGroup == null) {
            Log.e("[History] Call log group is null, aborting!")
            findNavController().navigateUp()
            return
        }

        viewModel = callLogGroup.lastCallLogViewModel
        binding.viewModel = viewModel
        viewModel.addRelatedCallLogs(callLogGroup.callLogs)

        useMaterialSharedAxisXForwardAnimation = sharedViewModel.isSlidingPaneSlideable.value == false

        binding.setNewContactClickListener {
            val copy = viewModel.callLog.remoteAddress.clone()
            copy.clean()
            val address = copy.asStringUriOnly()
            Log.i("[History] Creating contact with SIP URI: $address")
            sharedViewModel.updateContactsAnimationsBasedOnDestination.value = Event(R.id.masterCallLogsFragment)
            navigateToContacts(address)
        }

        binding.setContactClickListener {
            sharedViewModel.updateContactsAnimationsBasedOnDestination.value = Event(R.id.masterCallLogsFragment)
            val contactId = viewModel.contact.value?.refKey
            if (contactId != null) {
                Log.i("[History] Displaying contact $contactId")
                navigateToContact(contactId)
            } else {
                val copy = viewModel.callLog.remoteAddress.clone()
                copy.clean()
                val address = copy.asStringUriOnly()
                Log.i("[History] Displaying friend with address $address")
                navigateToContact(address)
            }
        }

        viewModel.startCallEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { callLog ->
                // To remove the GRUU if any
                val address = callLog.remoteAddress.clone()
                address.clean()

                if (coreContext.core.callsNb > 0) {
                    Log.i("[History] Starting dialer with pre-filled URI ${address.asStringUriOnly()}, is transfer? ${sharedViewModel.pendingCallTransfer}")
                    sharedViewModel.updateDialerAnimationsBasedOnDestination.value =
                        Event(R.id.masterCallLogsFragment)

                    val args = Bundle()
                    args.putString("URI", address.asStringUriOnly())
                    args.putBoolean("Transfer", sharedViewModel.pendingCallTransfer)
                    args.putBoolean(
                        "SkipAutoCallStart",
                        true
                    ) // If auto start call setting is enabled, ignore it
                    navigateToDialer(args)
                } else {
                    val localAddress = callLog.localAddress
                    Log.i("[History] Starting call to ${address.asStringUriOnly()} with local address ${localAddress.asStringUriOnly()}")
                    coreContext.startCall(address, localAddress = localAddress)
                }
            }
        }

        viewModel.chatRoomCreatedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatRoom ->
                val args = Bundle()
                args.putString("LocalSipUri", chatRoom.localAddress.asStringUriOnly())
                args.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
                navigateToChatRoom(args)
            }
        }

        viewModel.onMessageToNotifyEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { messageResourceId ->
                (activity as MainActivity).showSnackBar(messageResourceId)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.enableListener(true)
    }

    override fun onPause() {
        viewModel.enableListener(false)
        super.onPause()
    }
}

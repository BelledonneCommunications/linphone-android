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
package org.linphone.activities.main.contact.fragments

import android.content.Intent
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
import org.linphone.activities.main.contact.viewmodels.ContactViewModel
import org.linphone.activities.main.contact.viewmodels.ContactViewModelFactory
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactDetailFragmentBinding

class DetailContactFragment : Fragment() {
    private lateinit var binding: ContactDetailFragmentBinding
    private lateinit var viewModel: ContactViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactDetailFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val contact = sharedViewModel.selectedContact.value
        contact ?: return

        viewModel = ViewModelProvider(
            this,
            ContactViewModelFactory(contact)
        )[ContactViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.sendSmsToEvent.observe(viewLifecycleOwner, Observer {
            it.consume { number ->
                sendSms(number)
            }
        })

        viewModel.startCallToEvent.observe(viewLifecycleOwner, Observer {
            it.consume { address ->
                if (coreContext.core.callsNb > 0) {
                    Log.i("[Contact] Starting dialer with pre-filled URI ${address.asStringUriOnly()}")
                    val args = Bundle()
                    args.putString("URI", address.asStringUriOnly())
                    args.putBoolean("Transfer", sharedViewModel.pendingCallTransfer)
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
                if (findNavController().currentDestination?.id == R.id.detailContactFragment) {
                    val args = Bundle()
                    args.putString("LocalSipUri", chatRoom.localAddress.asStringUriOnly())
                    args.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
                    findNavController().navigate(R.id.action_global_masterChatRoomsFragment, args)
                }
            }
        })

        binding.setBackClickListener {
            findNavController().popBackStack()
        }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

        binding.setEditClickListener {
            if (findNavController().currentDestination?.id == R.id.detailContactFragment) {
                findNavController().navigate(R.id.action_detailContactFragment_to_contactEditorFragment)
            }
        }

        viewModel.onErrorEvent.observe(viewLifecycleOwner, Observer {
            it.consume { messageResourceId ->
                (activity as MainActivity).showSnackBar(messageResourceId)
            }
        })
    }

    private fun sendSms(number: String) {
        val smsIntent = Intent(Intent.ACTION_SENDTO)
        smsIntent.putExtra("address", number)
        smsIntent.data = Uri.parse("smsto:$number")
        val text = getString(R.string.contact_send_sms_invite_text).format(getString(R.string.contact_send_sms_invite_download_link))
        smsIntent.putExtra("sms_body", text)
        startActivity(smsIntent)
    }
}

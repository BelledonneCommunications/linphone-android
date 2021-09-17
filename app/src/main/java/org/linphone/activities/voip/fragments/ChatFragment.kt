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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.chat.adapters.ChatMessagesListAdapter
import org.linphone.activities.main.chat.viewmodels.*
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipChatFragmentBinding

class ChatFragment : GenericFragment<VoipChatFragmentBinding>() {
    private lateinit var adapter: ChatMessagesListAdapter
    private lateinit var viewModel: ChatRoomViewModel
    private lateinit var callsViewModel: CallsViewModel
    private lateinit var listViewModel: ChatMessagesListViewModel
    private lateinit var chatSendingViewModel: ChatMessageSendingViewModel

    override fun getLayoutId(): Int = R.layout.voip_chat_fragment

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == adapter.itemCount - itemCount) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
                scrollToBottom()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.setCancelClickListener {
            goBack()
        }

        val localSipUri = arguments?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri")
        var chatRoom: ChatRoom? = null
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            chatRoom = coreContext.core.searchChatRoom(null, localAddress, remoteSipAddress, arrayOfNulls(0))
        }
        chatRoom ?: return

        viewModel = ViewModelProvider(
            this,
            ChatRoomViewModelFactory(chatRoom)
        )[ChatRoomViewModel::class.java]
        binding.viewModel = viewModel

        callsViewModel = requireActivity().run {
            ViewModelProvider(this).get(CallsViewModel::class.java)
        }

        listViewModel = ViewModelProvider(
            this,
            ChatMessagesListViewModelFactory(chatRoom)
        )[ChatMessagesListViewModel::class.java]

        chatSendingViewModel = ViewModelProvider(
            this,
            ChatMessageSendingViewModelFactory(chatRoom)
        )[ChatMessageSendingViewModel::class.java]
        binding.chatSendingViewModel = chatSendingViewModel

        val listSelectionViewModel = ViewModelProvider(this).get(ListTopBarViewModel::class.java)
        adapter = ChatMessagesListAdapter(listSelectionViewModel, this)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        binding.chatMessagesList.adapter = adapter
        adapter.registerAdapterDataObserver(observer)

        // Disable context menu on each message
        adapter.disableContextMenu()

        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        callsViewModel.noMoreCallEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    requireActivity().finish()
                }
            }
        )

        listViewModel.events.observe(
            viewLifecycleOwner,
            { events ->
                adapter.submitList(events)
            }
        )

        chatSendingViewModel.textToSend.observe(
            viewLifecycleOwner,
            {
                chatSendingViewModel.onTextToSendChanged(it)
            }
        )
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.chatMessagesList.scrollToPosition(adapter.itemCount - 1)
        }
    }
}

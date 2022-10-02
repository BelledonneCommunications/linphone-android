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
package org.linphone.activities.chat_bubble

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.adapters.ChatMessagesListAdapter
import org.linphone.activities.main.chat.viewmodels.*
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatBubbleActivityBinding
import org.linphone.utils.FileUtils

class ChatBubbleActivity : GenericActivity() {
    private lateinit var binding: ChatBubbleActivityBinding
    private lateinit var viewModel: ChatRoomViewModel
    private lateinit var listViewModel: ChatMessagesListViewModel
    private lateinit var chatSendingViewModel: ChatMessageSendingViewModel
    private lateinit var adapter: ChatMessagesListAdapter

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == adapter.itemCount - itemCount) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
                scrollToBottom()
            }
        }
    }

    private val listener = object : ChatRoomListenerStub() {
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<out EventLog>) {
            chatRoom.markAsRead()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.chat_bubble_activity)
        binding.lifecycleOwner = this

        val localSipUri = intent.getStringExtra("LocalSipUri")
        val remoteSipUri = intent.getStringExtra("RemoteSipUri")
        var chatRoom: ChatRoom? = null

        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat Bubble] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            chatRoom = coreContext.core.searchChatRoom(
                null, localAddress, remoteSipAddress,
                arrayOfNulls(
                    0
                )
            )
        }

        if (chatRoom == null) {
            Log.e("[Chat Bubble] Chat room is null, aborting!")
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            ChatRoomViewModelFactory(chatRoom)
        )[ChatRoomViewModel::class.java]
        binding.viewModel = viewModel

        listViewModel = ViewModelProvider(
            this,
            ChatMessagesListViewModelFactory(chatRoom)
        )[ChatMessagesListViewModel::class.java]

        chatSendingViewModel = ViewModelProvider(
            this,
            ChatMessageSendingViewModelFactory(chatRoom)
        )[ChatMessageSendingViewModel::class.java]
        binding.chatSendingViewModel = chatSendingViewModel

        val listSelectionViewModel = ViewModelProvider(this)[ListTopBarViewModel::class.java]
        adapter = ChatMessagesListAdapter(listSelectionViewModel, this)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        binding.chatMessagesList.adapter = adapter
        adapter.registerAdapterDataObserver(observer)

        // Disable context menu on each message
        adapter.disableAdvancedContextMenuOptions()

        adapter.openContentEvent.observe(
            this
        ) {
            it.consume { content ->
                if (content.isFileEncrypted) {
                    Toast.makeText(
                        this,
                        R.string.chat_bubble_cant_open_enrypted_file,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    FileUtils.openFileInThirdPartyApp(this, content.filePath.orEmpty(), true)
                }
            }
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        listViewModel.events.observe(
            this
        ) { events ->
            adapter.submitList(events)
        }

        chatSendingViewModel.textToSend.observe(
            this
        ) {
            chatSendingViewModel.onTextToSendChanged(it)
        }

        binding.setOpenAppClickListener {
            coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null
            coreContext.notificationsManager.changeDismissNotificationUponReadForChatRoom(viewModel.chatRoom, false)

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("RemoteSipUri", remoteSipUri)
            intent.putExtra("LocalSipUri", localSipUri)
            intent.putExtra("Chat", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }

        binding.setCloseBubbleClickListener {
            coreContext.notificationsManager.dismissChatNotification(viewModel.chatRoom)
        }

        binding.setSendMessageClickListener {
            chatSendingViewModel.sendMessage()
            binding.message.text?.clear()
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.chatRoom.addListener(listener)

        // Workaround for the removed notification when a chat room is marked as read
        coreContext.notificationsManager.changeDismissNotificationUponReadForChatRoom(viewModel.chatRoom, true)
        viewModel.chatRoom.markAsRead()

        val peerAddress = viewModel.chatRoom.peerAddress.asStringUriOnly()
        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = peerAddress
        coreContext.notificationsManager.resetChatNotificationCounterForSipUri(peerAddress)

        lifecycleScope.launch {
            // Without the delay the scroll to bottom doesn't happen...
            delay(100)
            scrollToBottom()
        }
    }

    override fun onPause() {
        viewModel.chatRoom.removeListener(listener)

        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null
        coreContext.notificationsManager.changeDismissNotificationUponReadForChatRoom(viewModel.chatRoom, false)

        super.onPause()
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.chatMessagesList.scrollToPosition(adapter.itemCount - 1)
        }
    }
}

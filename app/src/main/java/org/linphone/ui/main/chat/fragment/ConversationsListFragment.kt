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
package org.linphone.ui.main.chat.fragment

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatListFragmentBinding
import org.linphone.ui.main.chat.adapter.ConversationsListAdapter
import org.linphone.ui.main.chat.viewmodel.ConversationsListViewModel
import org.linphone.ui.main.fragment.AbstractTopBarFragment
import org.linphone.ui.main.history.fragment.HistoryMenuDialogFragment
import org.linphone.utils.Event
import org.linphone.utils.setKeyboardInsetListener

@UiThread
class ConversationsListFragment : AbstractTopBarFragment() {
    companion object {
        private const val TAG = "[Conversations List Fragment]"
    }

    private lateinit var binding: ChatListFragmentBinding

    private lateinit var listViewModel: ConversationsListViewModel

    private lateinit var adapter: ConversationsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[ConversationsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        adapter = ConversationsListAdapter(viewLifecycleOwner)
        binding.conversationsList.setHasFixedSize(true)
        binding.conversationsList.adapter = adapter
        binding.conversationsList.layoutManager = LinearLayoutManager(requireContext())

        adapter.conversationLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ConversationDialogFragment(
                    model.isMuted.value == true,
                    model.isGroup,
                    { // onDismiss
                        adapter.resetSelection()
                    },
                    { // onMarkConversationAsRead
                        Log.i("$TAG Marking conversation [${model.id}] as read")
                        model.markAsRead()
                    },
                    { // onToggleMute
                        Log.i("$TAG Changing mute status of conversation [${model.id}]")
                        model.toggleMute()
                    },
                    { // onCall
                        Log.i("$TAG Calling conversation [${model.id}]")
                        model.call()
                    },
                    { // onDeleteConversation
                        Log.i("$TAG Deleting conversation [${model.id}]")
                        model.delete()
                        listViewModel.applyFilter()
                    },
                    { // onLeaveGroup
                        Log.i("$TAG Leaving group conversation [${model.id}]")
                        model.leaveGroup()
                    }
                )
                modalBottomSheet.show(parentFragmentManager, HistoryMenuDialogFragment.TAG)
            }
        }

        adapter.conversationClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i("$TAG Show conversation with ID [${model.id}]")
                sharedViewModel.displayedChatRoom = model.chatRoom
                sharedViewModel.showConversationEvent.value = Event(
                    Pair(model.localSipUri, model.remoteSipUri)
                )
            }
        }

        binding.setOnNewConversationClicked {
            sharedViewModel.showStartConversationEvent.value = Event(true)
        }

        listViewModel.conversations.observe(viewLifecycleOwner) {
            val currentCount = adapter.itemCount
            adapter.submitList(it)
            Log.i("$TAG Conversations list ready with [${it.size}] items")

            if (currentCount < it.size) {
                binding.conversationsList.scrollToPosition(0)
            }
        }

        // TopBarFragment related

        setViewModelAndTitle(
            binding.topBar.search,
            listViewModel,
            getString(R.string.bottom_navigation_conversations_label)
        )

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            binding.bottomNavBar.root.visibility = if (!portraitOrientation || !keyboardVisible) View.VISIBLE else View.GONE
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i(
                    "$TAG Default account changed, updating avatar in top bar & re-computing conversations"
                )
                listViewModel.applyFilter()
            }
        }
    }
}

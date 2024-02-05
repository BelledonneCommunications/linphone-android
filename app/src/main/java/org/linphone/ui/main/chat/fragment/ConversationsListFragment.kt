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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatListFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.adapter.ConversationsListAdapter
import org.linphone.ui.main.chat.viewmodel.ConversationsListViewModel
import org.linphone.ui.main.fragment.AbstractTopBarFragment
import org.linphone.ui.main.history.fragment.HistoryMenuDialogFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

@UiThread
class ConversationsListFragment : AbstractTopBarFragment() {
    companion object {
        private const val TAG = "[Conversations List Fragment]"
    }

    private lateinit var binding: ChatListFragmentBinding

    private lateinit var listViewModel: ConversationsListViewModel

    private lateinit var adapter: ConversationsListAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            Log.i("$TAG [$itemCount] added, scrolling to top")
            binding.conversationsList.scrollToPosition(0)
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            Log.i("$TAG [$itemCount] moved, scrolling to top")
            binding.conversationsList.scrollToPosition(0)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            Log.i("$TAG [$itemCount] removed, scrolling to top")
            binding.conversationsList.scrollToPosition(0)
        }
    }

    override fun onDefaultAccountChanged() {
        Log.i(
            "$TAG Default account changed, updating avatar in top bar & re-computing conversations"
        )
        listViewModel.applyFilter()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (
            findNavController().currentDestination?.id == R.id.startConversationFragment ||
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment ||
            findNavController().currentDestination?.id == R.id.fileViewerFragment ||
            findNavController().currentDestination?.id == R.id.mediaListViewerFragment
        ) {
            // Holds fragment in place while new fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationsListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[ConversationsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        binding.conversationsList.setHasFixedSize(true)
        binding.conversationsList.layoutManager = LinearLayoutManager(requireContext())

        if (binding.conversationsList.adapter != adapter) {
            binding.conversationsList.adapter = adapter
        }

        adapter.conversationLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ConversationDialogFragment(
                    model.isMuted.value == true,
                    model.isGroup,
                    model.isReadOnly,
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
                bottomSheetDialog = modalBottomSheet
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
            if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                Log.i("$TAG Navigating to start conversation fragment")
                val action =
                    ConversationsListFragmentDirections.actionConversationsListFragmentToStartConversationFragment()
                findNavController().navigate(action)
            }
        }

        listViewModel.conversations.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            Log.i("$TAG Conversations list ready with [${it.size}] items")

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        sharedViewModel.showConversationEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val localSipUri = pair.first
                val remoteSipUri = pair.second
                Log.i(
                    "$TAG Navigating to conversation fragment with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
                )
                val action = ConversationFragmentDirections.actionGlobalConversationFragment(
                    localSipUri,
                    remoteSipUri
                )
                binding.chatNavContainer.findNavController().navigate(action)
            }
        }

        sharedViewModel.goToMeetingWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                    Log.i("$TAG Navigating to meeting waiting room fragment with URI [$uri]")
                    val action =
                        ConversationsListFragmentDirections.actionConversationsListFragmentToMeetingWaitingRoomFragment(
                            uri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner) { event ->
            if (!event.consumed()) {
                // Do not consume it yet
                val message = getString(R.string.toast_message_waiting_to_be_forwarded)
                val icon = R.drawable.forward
                (requireActivity() as MainActivity).showGreenToast(message, icon)
                Log.i("$TAG Found a message waiting to be forwarded")
            }
        }

        sharedViewModel.filesToShareFromIntent.observe(viewLifecycleOwner) { filesToShare ->
            val count = filesToShare.size
            if (count > 0) {
                val message = AppUtils.getStringWithPlural(
                    R.plurals.toast_files_waiting_to_be_shared,
                    count,
                    filesToShare.size.toString()
                )
                val icon = R.drawable.file
                (requireActivity() as MainActivity).showGreenToast(message, icon)
                Log.i("$TAG Found [$count] files waiting to be shared")
            }
        }

        sharedViewModel.textToShareFromIntent.observe(viewLifecycleOwner) { textToShare ->
            if (textToShare.isNotEmpty()) {
                val message = getString(R.string.toast_text_waiting_to_be_shared)
                val icon = R.drawable.file_text
                (requireActivity() as MainActivity).showGreenToast(message, icon)
                Log.i("$TAG Found text waiting to be shared")
            }
        }

        // TopBarFragment related

        setViewModelAndTitle(
            binding.topBar.search,
            listViewModel,
            getString(R.string.bottom_navigation_conversations_label)
        )

        initBottomNavBar(binding.bottomNavBar.root)

        initSlidingPane(binding.slidingPaneLayout)

        initNavigation(R.id.conversationsListFragment)

        // Handle intent params if any

        val args = arguments
        if (args != null) {
            val localSipUri = args.getString("LocalSipUri")
            val remoteSipUri = args.getString("RemoteSipUri")
            if (localSipUri != null && remoteSipUri != null) {
                Log.i("$TAG Found local [$localSipUri] & remote [$remoteSipUri] URIs in arguments")
                val pair = Pair(localSipUri, remoteSipUri)
                sharedViewModel.showConversationEvent.value = Event(pair)
                args.clear()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }

        // Scroll to top when fragment is resumed
        binding.conversationsList.scrollToPosition(0)
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null

        try {
            adapter.unregisterAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }
    }
}

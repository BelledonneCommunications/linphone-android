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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatListFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.file_viewer.FileViewerActivity
import org.linphone.ui.file_viewer.MediaViewerActivity
import org.linphone.ui.main.chat.adapter.ConversationsListAdapter
import org.linphone.ui.main.chat.viewmodel.ConversationsListViewModel
import org.linphone.ui.main.fragment.AbstractMainFragment
import org.linphone.ui.main.history.fragment.HistoryMenuDialogFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

@UiThread
class ConversationsListFragment : AbstractMainFragment() {
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

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { }
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
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment
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
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[ConversationsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel
        observeToastEvents(listViewModel)

        binding.conversationsList.setHasFixedSize(true)
        binding.conversationsList.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationsList.outlineProvider = outlineProvider
        binding.conversationsList.clipToOutline = true

        adapter.conversationLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ConversationDialogFragment(
                    model.isMuted.value == true,
                    model.isGroup,
                    model.isReadOnly.value == true,
                    (model.unreadMessageCount.value ?: 0) > 0,
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

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.conversationsList.adapter != adapter) {
                binding.conversationsList.adapter = adapter
            }

            Log.i("$TAG Conversations list ready with [${it.size}] items")
            listViewModel.fetchInProgress.value = false
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

        sharedViewModel.goToAccountProfileEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                    val identity = LinphoneUtils.getDefaultAccount()?.params?.identityAddress?.asStringUriOnly().orEmpty()
                    val action =
                        ConversationsListFragmentDirections.actionConversationsListFragmentToAccountProfileFragment(
                            identity
                        )
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.displayFileEvent.observe(viewLifecycleOwner) {
            it.consume { bundle ->
                if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                    val path = bundle.getString("path", "")
                    val isMedia = bundle.getBoolean("isMedia", false)
                    if (path.isEmpty()) {
                        Log.e("$TAG Can't navigate to file viewer for empty path!")
                        return@consume
                    }

                    Log.i(
                        "$TAG Navigating to ${if (isMedia) "media" else "file"} viewer fragment with path [$path]"
                    )
                    if (isMedia) {
                        val intent = Intent(requireActivity(), MediaViewerActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    } else {
                        val intent = Intent(requireActivity(), FileViewerActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
            }
        }

        sharedViewModel.filesToShareFromIntent.observe(viewLifecycleOwner) { filesToShare ->
            val count = filesToShare.size
            if (count > 0) {
                val message = AppUtils.getStringWithPlural(
                    R.plurals.conversations_files_waiting_to_be_shared_toast,
                    count,
                    filesToShare.size.toString()
                )
                val icon = R.drawable.file
                (requireActivity() as GenericActivity).showGreenToast(message, icon)
                Log.i("$TAG Found [$count] files waiting to be shared")
            }
        }

        sharedViewModel.textToShareFromIntent.observe(viewLifecycleOwner) { textToShare ->
            if (textToShare.isNotEmpty()) {
                val message = getString(R.string.conversations_text_waiting_to_be_shared_toast)
                val icon = R.drawable.file_text
                (requireActivity() as GenericActivity).showGreenToast(message, icon)
                Log.i("$TAG Found text waiting to be shared")
            }
        }

        sharedViewModel.forceRefreshConversations.observe(viewLifecycleOwner) {
            it.consume {
                listViewModel.filter()
            }
        }

        sharedViewModel.forceRefreshDisplayedConversation.observe(viewLifecycleOwner) {
            it.consume {
                val displayChatRoom = sharedViewModel.displayedChatRoom
                if (displayChatRoom != null) {
                    val found = listViewModel.conversations.value.orEmpty().find { model ->
                        model.chatRoom == displayChatRoom
                    }
                    found?.updateMuteState()
                }
            }
        }

        sharedViewModel.updateUnreadMessageCountForCurrentConversationEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val displayChatRoom = sharedViewModel.displayedChatRoom
                if (displayChatRoom != null) {
                    val found = listViewModel.conversations.value.orEmpty().find { model ->
                        model.chatRoom == displayChatRoom
                    }
                    found?.updateUnreadCount()
                }
                listViewModel.updateUnreadMessagesCount()
            }
        }

        // AbstractMainFragment related

        listViewModel.title.value = getString(R.string.bottom_navigation_conversations_label)
        setViewModel(listViewModel)
        initViews(
            binding.slidingPaneLayout,
            binding.topBar,
            binding.bottomNavBar,
            R.id.conversationsListFragment
        )

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

        if (listViewModel.hideConversations.value == true) {
            Log.w(
                "$TAG Resuming fragment that should no longer be accessible, going to contacts list instead"
            )
            sharedViewModel.navigateToContactsEvent.value = Event(true)
        }

        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }
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

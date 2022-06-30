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
package org.linphone.activities.main.chat.fragments

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.transition.MaterialSharedAxis
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.clearDisplayedChatRoom
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.adapters.ChatRoomsListAdapter
import org.linphone.activities.main.chat.viewmodels.ChatRoomsListViewModel
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToChatRoom
import org.linphone.activities.navigateToChatRoomCreation
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomMasterFragmentBinding
import org.linphone.utils.*

class MasterChatRoomsFragment : MasterFragment<ChatRoomMasterFragmentBinding, ChatRoomsListAdapter>() {
    override val dialogConfirmationMessageBeforeRemoval = R.plurals.chat_room_delete_dialog
    private lateinit var listViewModel: ChatRoomsListViewModel

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            scrollToTop()
        }
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == 0 && itemCount == 1) {
                scrollToTop()
            }
        }
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            scrollToTop()
        }
    }

    override fun getLayoutId(): Int = R.layout.chat_room_master_fragment

    override fun onDestroyView() {
        binding.chatList.adapter = null
        adapter.unregisterAdapterDataObserver(observer)
        super.onDestroyView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        useMaterialSharedAxisXForwardAnimation = false
        if (corePreferences.enableAnimations) {
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val axis = if (portraitOrientation) MaterialSharedAxis.X else MaterialSharedAxis.Y
            enterTransition = MaterialSharedAxis(axis, true)
            reenterTransition = MaterialSharedAxis(axis, true)
            returnTransition = MaterialSharedAxis(axis, false)
            exitTransition = MaterialSharedAxis(axis, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isSecure = true
        binding.lifecycleOwner = viewLifecycleOwner

        listViewModel = requireActivity().run {
            ViewModelProvider(this)[ChatRoomsListViewModel::class.java]
        }
        binding.viewModel = listViewModel

        /* Shared view model & sliding pane related */

        setUpSlidingPane(binding.slidingPane)

        binding.slidingPane.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) { }

            override fun onPanelOpened(panel: View) { }

            override fun onPanelClosed(panel: View) {
                // Conversation isn't visible anymore, any new message received in it will trigger a notification
                coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null
            }
        })

        // Chat room loading can take some time, so wait until it is ready before opening the pane
        sharedViewModel.chatRoomFragmentOpenedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                binding.slidingPane.openPane()
            }
        }

        sharedViewModel.layoutChangedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                sharedViewModel.isSlidingPaneSlideable.value = binding.slidingPane.isSlideable
                if (binding.slidingPane.isSlideable) {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
                    if (navHostFragment.navController.currentDestination?.id == R.id.emptyChatFragment) {
                        Log.i("[Chat] Foldable device has been folded, closing side pane with empty fragment")
                        binding.slidingPane.closePane()
                    }
                }
            }
        }

        sharedViewModel.refreshChatRoomInListEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val chatRoom = sharedViewModel.selectedChatRoom.value
                if (chatRoom != null) {
                    listViewModel.notifyChatRoomUpdate(chatRoom)
                }
            }
        }

        /* End of shared view model & sliding pane related */

        _adapter = ChatRoomsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(observer)
        binding.chatList.setHasFixedSize(true)
        binding.chatList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.layoutManager = layoutManager

        // Swipe action
        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        val white = ContextCompat.getColor(requireContext(), R.color.white_color)

        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action(
            requireContext().getString(R.string.dialog_delete),
            white,
            ContextCompat.getColor(requireContext(), R.color.red_color)
        )
        swipeConfiguration.leftToRightAction = RecyclerViewSwipeConfiguration.Action(
            requireContext().getString(R.string.received_chat_notification_mark_as_read_label),
            white,
            ContextCompat.getColor(requireContext(), R.color.imdn_read_color)
        )
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {
                val index = viewHolder.bindingAdapterPosition
                if (index < 0 || index >= adapter.currentList.size) {
                    Log.e("[Chat] Index is out of bound, can't mark chat room as read")
                } else {
                    val chatRoom = adapter.currentList[viewHolder.bindingAdapterPosition]
                    chatRoom.markAsRead()
                    adapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                }
            }

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val viewModel = DialogViewModel(getString(R.string.chat_room_delete_one_dialog))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                val index = viewHolder.bindingAdapterPosition
                if (index < 0 || index >= adapter.currentList.size) {
                    Log.e("[Chat] Index is out of bound, can't delete chat room")
                } else {
                    viewModel.showCancelButton {
                        adapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                        dialog.dismiss()
                    }

                    viewModel.showDeleteButton(
                        {
                            val deletedChatRoom =
                                adapter.currentList[index]
                            listViewModel.deleteChatRoom(deletedChatRoom)
                            if (!binding.slidingPane.isSlideable &&
                                deletedChatRoom == sharedViewModel.selectedChatRoom.value
                            ) {
                                Log.i("[Chat] Currently displayed chat room has been deleted, removing detail fragment")
                                clearDisplayedChatRoom()
                            }
                            dialog.dismiss()
                        },
                        getString(R.string.dialog_delete)
                    )

                    dialog.show()
                }
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.chatList)

        // Divider between items
        binding.chatList.addItemDecoration(AppUtils.getDividerDecoration(requireContext(), layoutManager))

        listViewModel.chatRooms.observe(
            viewLifecycleOwner
        ) { chatRooms ->
            adapter.submitList(chatRooms)
        }

        listViewModel.chatRoomIndexUpdatedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { index ->
                adapter.notifyItemChanged(index)
            }
        }

        adapter.selectedChatRoomEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatRoom ->
                if ((requireActivity() as GenericActivity).isDestructionPending) {
                    Log.w("[Chat] Activity is pending destruction, don't start navigating now!")
                    sharedViewModel.destructionPendingChatRoom = chatRoom
                } else {
                    if (chatRoom.peerAddress.asStringUriOnly() == coreContext.notificationsManager.currentlyDisplayedChatRoomAddress) {
                        if (!binding.slidingPane.isOpen) {
                            Log.w("[Chat] Chat room is displayed but sliding pane is closed...")
                            if (!binding.slidingPane.openPane()) {
                                Log.e("[Chat] Tried to open pane to workaround already displayed chat room issue, failed!")
                            }
                        } else {
                            Log.w("[Chat] This chat room is already displayed!")
                        }
                    } else {
                        sharedViewModel.selectedChatRoom.value = chatRoom
                        navigateToChatRoom(
                            AppUtils.createBundleWithSharedTextAndFiles(
                                sharedViewModel
                            )
                        )
                    }
                }
            }
        }

        binding.setEditClickListener {
            listSelectionViewModel.isEditionEnabled.value = true
        }

        binding.setCancelForwardClickListener {
            sharedViewModel.messageToForwardEvent.value?.consume {
                Log.i("[Chat] Cancelling message forward")
            }
            sharedViewModel.isPendingMessageForward.value = false
        }

        binding.setCancelSharingClickListener {
            Log.i("[Chat] Cancelling text/files sharing")
            sharedViewModel.textToShare.value = ""
            sharedViewModel.filesToShare.value = arrayListOf()
            listViewModel.fileSharingPending.value = false
            listViewModel.textSharingPending.value = false
        }

        binding.setNewOneToOneChatRoomClickListener {
            sharedViewModel.chatRoomParticipants.value = arrayListOf()
            navigateToChatRoomCreation(false, binding.slidingPane)
        }

        binding.setNewGroupChatRoomClickListener {
            sharedViewModel.selectedGroupChatRoom.value = null
            sharedViewModel.chatRoomParticipants.value = arrayListOf()
            navigateToChatRoomCreation(true, binding.slidingPane)
        }

        val pendingDestructionChatRoom = sharedViewModel.destructionPendingChatRoom
        if (pendingDestructionChatRoom != null) {
            Log.w("[Chat] Found pending chat room from before activity was recreated")
            sharedViewModel.destructionPendingChatRoom = null
            sharedViewModel.selectedChatRoom.value = pendingDestructionChatRoom
            navigateToChatRoom(AppUtils.createBundleWithSharedTextAndFiles(sharedViewModel))
        }

        val localSipUri = arguments?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri")
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")
            arguments?.clear()
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            val chatRoom = coreContext.core.searchChatRoom(null, localAddress, remoteSipAddress, arrayOfNulls(0))
            if (chatRoom != null) {
                Log.i("[Chat] Found matching chat room $chatRoom")
                adapter.selectedChatRoomEvent.value = Event(chatRoom)
            }
        } else {
            sharedViewModel.textToShare.observe(
                viewLifecycleOwner
            ) {
                if (it.isNotEmpty()) {
                    Log.i("[Chat] Found text to share")
                    listViewModel.textSharingPending.value = true
                    clearDisplayedChatRoom()
                } else {
                    if (sharedViewModel.filesToShare.value.isNullOrEmpty()) {
                        listViewModel.textSharingPending.value = false
                    }
                }
            }
            sharedViewModel.filesToShare.observe(
                viewLifecycleOwner
            ) {
                if (it.isNotEmpty()) {
                    Log.i("[Chat] Found ${it.size} files to share")
                    listViewModel.fileSharingPending.value = true
                    clearDisplayedChatRoom()
                } else {
                    if (sharedViewModel.textToShare.value.isNullOrEmpty()) {
                        listViewModel.fileSharingPending.value = false
                    }
                }
            }
            sharedViewModel.isPendingMessageForward.observe(
                viewLifecycleOwner
            ) {
                listViewModel.forwardPending.value = it
                adapter.forwardPending(it)
                if (it) {
                    Log.i("[Chat] Found chat message to transfer")
                }
            }

            listViewModel.onMessageToNotifyEvent.observe(
                viewLifecycleOwner
            ) {
                it.consume { messageResourceId ->
                    (activity as MainActivity).showSnackBar(messageResourceId)
                }
            }
        }
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<ChatRoom>()
        var closeSlidingPane = false
        for (index in indexesOfItemToDelete) {
            val chatRoom = adapter.currentList[index]
            list.add(chatRoom)

            if (chatRoom == sharedViewModel.selectedChatRoom.value) {
                closeSlidingPane = true
            }
        }
        listViewModel.deleteChatRooms(list)

        if (!binding.slidingPane.isSlideable && closeSlidingPane) {
            Log.i("[Chat] Currently displayed chat room has been deleted, removing detail fragment")
            clearDisplayedChatRoom()
        }
    }

    private fun scrollToTop() {
        binding.chatList.scrollToPosition(0)
    }
}

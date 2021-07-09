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
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.adapters.ChatRoomsListAdapter
import org.linphone.activities.main.chat.viewmodels.ChatRoomsListViewModel
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
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
    private lateinit var sharedViewModel: SharedMainViewModel

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isSecure = true
        binding.lifecycleOwner = this

        listViewModel = ViewModelProvider(this).get(ChatRoomsListViewModel::class.java)
        binding.viewModel = listViewModel

        /* Shared view model & sliding pane related */

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        view.doOnPreDraw { sharedViewModel.canSlidingPaneBeClosed.value = binding.slidingPane.isSlideable }

        sharedViewModel.closeSlidingPaneEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (!binding.slidingPane.closePane()) {
                    goBack()
                }
            }
        })
        sharedViewModel.layoutChangedEvent.observe(viewLifecycleOwner, {
            it.consume {
                sharedViewModel.canSlidingPaneBeClosed.value = binding.slidingPane.isSlideable
            }
        })
        binding.slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        /*binding.slidingPane.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) { }

            override fun onPanelOpened(panel: View) {
                if (binding.slidingPane.isSlideable) {
                    (requireActivity() as MainActivity).hideTabsFragment()
                }
            }

            override fun onPanelClosed(panel: View) {
                if (binding.slidingPane.isSlideable) {
                    (requireActivity() as MainActivity).showTabsFragment()
                }
            }
        })*/

        /* End of hared view model & sliding pane related */

        _adapter = ChatRoomsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(observer)
        binding.chatList.setHasFixedSize(true)
        binding.chatList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
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
                val chatRoomViewModel = adapter.currentList[viewHolder.adapterPosition]
                chatRoomViewModel.chatRoom.markAsRead()
                coreContext.notificationsManager.dismissChatNotification(chatRoomViewModel.chatRoom)
                adapter.notifyItemChanged(viewHolder.adapterPosition)
            }

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val viewModel = DialogViewModel(getString(R.string.chat_room_delete_one_dialog))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                viewModel.showCancelButton {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                    dialog.dismiss()
                }

                viewModel.showDeleteButton({
                    listViewModel.deleteChatRoom(adapter.currentList[viewHolder.adapterPosition].chatRoom)
                    dialog.dismiss()
                }, getString(R.string.dialog_delete))

                dialog.show()
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.chatList)

        // Divider between items
        binding.chatList.addItemDecoration(AppUtils.getDividerDecoration(requireContext(), layoutManager))

        listViewModel.chatRooms.observe(viewLifecycleOwner, { chatRooms ->
            adapter.submitList(chatRooms)
        })

        listViewModel.contactsUpdatedEvent.observe(viewLifecycleOwner, {
            it.consume {
                adapter.notifyDataSetChanged()
            }
        })

        adapter.selectedChatRoomEvent.observe(viewLifecycleOwner, {
            it.consume { chatRoom ->
                if ((requireActivity() as GenericActivity).isDestructionPending) {
                    Log.w("[Chat] Activity is pending destruction, don't start navigating now!")
                    sharedViewModel.destructionPendingChatRoom = chatRoom
                } else {
                    binding.slidingPane.openPane()
                    sharedViewModel.selectedChatRoom.value = chatRoom
                    navigateToChatRoom(AppUtils.createBundleWithSharedTextAndFiles(sharedViewModel))
                }
            }
        })

        binding.setEditClickListener {
            listSelectionViewModel.isEditionEnabled.value = true
        }

        binding.setCancelForwardClickListener {
            sharedViewModel.messageToForwardEvent.value?.consume {
                Log.i("[Chat] Cancelling message forward")
            }
            listViewModel.forwardPending.value = false
            adapter.forwardPending(false)
        }

        binding.setCancelSharingClickListener {
            Log.i("[Chat] Cancelling text/files sharing")
            sharedViewModel.textToShare.value = ""
            sharedViewModel.filesToShare.value = arrayListOf()
            listViewModel.fileSharingPending.value = false
            listViewModel.textSharingPending.value = false
        }

        binding.setNewOneToOneChatRoomClickListener {
            binding.slidingPane.openPane()
            sharedViewModel.chatRoomParticipants.value = arrayListOf()
            navigateToChatRoomCreation(false)
        }

        binding.setNewGroupChatRoomClickListener {
            binding.slidingPane.openPane()
            sharedViewModel.selectedGroupChatRoom.value = null
            sharedViewModel.chatRoomParticipants.value = arrayListOf()
            navigateToChatRoomCreation(true)
        }

        val pendingDestructionChatRoom = sharedViewModel.destructionPendingChatRoom
        if (pendingDestructionChatRoom != null) {
            binding.slidingPane.openPane()
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
                chatRoom.markAsRead()
                adapter.selectedChatRoomEvent.value = Event(chatRoom)
            }
        } else {
            sharedViewModel.textToShare.observe(viewLifecycleOwner, {
                if (it.isNotEmpty()) {
                    Log.i("[Chat] Found text to share")
                    // val activity = requireActivity() as MainActivity
                    // activity.showSnackBar(R.string.chat_room_toast_choose_for_sharing)
                    listViewModel.textSharingPending.value = true
                } else {
                    if (sharedViewModel.filesToShare.value.isNullOrEmpty()) {
                        listViewModel.textSharingPending.value = false
                    }
                }
            })
            sharedViewModel.filesToShare.observe(viewLifecycleOwner, {
                if (it.isNotEmpty()) {
                    Log.i("[Chat] Found ${it.size} files to share")
                    // val activity = requireActivity() as MainActivity
                    // activity.showSnackBar(R.string.chat_room_toast_choose_for_sharing)
                    listViewModel.fileSharingPending.value = true
                } else {
                    if (sharedViewModel.textToShare.value.isNullOrEmpty()) {
                        listViewModel.fileSharingPending.value = false
                    }
                }
            })
            sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner, {
                if (!it.consumed()) {
                    Log.i("[Chat] Found chat message to transfer")
                    // val activity = requireActivity() as MainActivity
                    // activity.showSnackBar(R.string.chat_room_toast_choose_for_sharing)
                    listViewModel.forwardPending.value = true
                    adapter.forwardPending(true)
                } else {
                    listViewModel.forwardPending.value = false
                    adapter.forwardPending(false)
                }
            })

            listViewModel.onErrorEvent.observe(viewLifecycleOwner, {
                it.consume { messageResourceId ->
                    (activity as MainActivity).showSnackBar(messageResourceId)
                }
            })
        }
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<ChatRoom>()
        for (index in indexesOfItemToDelete) {
            val chatRoomViewModel = adapter.currentList[index]
            list.add(chatRoomViewModel.chatRoom)
        }
        listViewModel.deleteChatRooms(list)
    }

    private fun scrollToTop() {
        binding.chatList.scrollToPosition(0)
    }
}

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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.adapters.ChatRoomsListAdapter
import org.linphone.activities.main.chat.viewmodels.ChatRoomsListViewModel
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomMasterFragmentBinding
import org.linphone.utils.*

class MasterChatRoomsFragment : MasterFragment() {
    private lateinit var binding: ChatRoomMasterFragmentBinding
    private lateinit var listViewModel: ChatRoomsListViewModel
    private lateinit var adapter: ChatRoomsListAdapter
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatRoomMasterFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        listViewModel = ViewModelProvider(this).get(ChatRoomsListViewModel::class.java)
        binding.viewModel = listViewModel

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        adapter = ChatRoomsListAdapter(listSelectionViewModel)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                scrollToTop()
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                scrollToTop()
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                scrollToTop()
            }
        })
        binding.chatList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        binding.chatList.layoutManager = layoutManager

        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        val white = ContextCompat.getColor(requireContext(), R.color.white_color)

        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action("Delete", white, ContextCompat.getColor(requireContext(), R.color.red_color))
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {}

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val viewModel = DialogViewModel(getString(R.string.dialog_default_delete_message))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                viewModel.showCancelButton {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                    dialog.dismiss()
                }

                viewModel.showDeleteButton({
                    listViewModel.deleteChatRoom(listViewModel.chatRooms.value?.get(viewHolder.adapterPosition))
                    dialog.dismiss()
                }, getString(R.string.dialog_delete))

                dialog.show()
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.LEFT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.chatList)

        // Divider between items
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        binding.chatList.addItemDecoration(dividerItemDecoration)

        listViewModel.chatRooms.observe(viewLifecycleOwner, Observer { chatRooms ->
            adapter.submitList(chatRooms)
        })

        listViewModel.latestUpdatedChatRoomId.observe(viewLifecycleOwner, Observer { position ->
            adapter.notifyItemChanged(position)
        })

        listViewModel.contactsUpdatedEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                adapter.notifyDataSetChanged()
            }
        })

        adapter.selectedChatRoomEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatRoom ->
                sharedViewModel.selectedChatRoom.value = chatRoom
                if (!resources.getBoolean(R.bool.isTablet)) {
                    if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
                        findNavController().navigate(R.id.action_masterChatRoomsFragment_to_detailChatRoomFragment)
                    }
                } else {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
                    navHostFragment.navController.navigate(R.id.action_global_detailChatRoomFragment)
                }
            }
        })

        binding.setEditClickListener {
            listSelectionViewModel.isEditionEnabled.value = true
        }

        binding.setNewOneToOneChatRoomClickListener {
            val bundle = bundleOf("createGroup" to false)
            if (!resources.getBoolean(R.bool.isTablet)) {
                if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
                    findNavController().navigate(
                        R.id.action_masterChatRoomsFragment_to_chatRoomCreationFragment,
                        bundle
                    )
                }
            } else {
                val navHostFragment =
                    childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
                navHostFragment.navController.navigate(R.id.action_global_chatRoomCreationFragment, bundle)
            }
        }

        binding.setNewGroupChatRoomClickListener {
            sharedViewModel.selectedGroupChatRoom.value = null

            val bundle = bundleOf("createGroup" to true)
            if (!resources.getBoolean(R.bool.isTablet)) {
                if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
                    findNavController().navigate(
                        R.id.action_masterChatRoomsFragment_to_chatRoomCreationFragment,
                        bundle
                    )
                }
            } else {
                val navHostFragment =
                    childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
                navHostFragment.navController.navigate(R.id.action_global_chatRoomCreationFragment, bundle)
            }
        }

        val localSipUri = arguments?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri")
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")
            arguments?.clear()
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            val chatRoom = coreContext.core.getChatRoom(remoteSipAddress, localAddress)
            if (chatRoom != null) {
                Log.i("[Chat] Found matching chat room $chatRoom")
                chatRoom.markAsRead()
                adapter.selectedChatRoomEvent.value = Event(chatRoom)
            }
        } else {
            sharedViewModel.filesToShare.observe(viewLifecycleOwner, Observer {
                if (it.isNotEmpty()) {
                    Log.i("[Chat] Found ${it.size} files to share")
                    val activity = requireActivity() as MainActivity
                    activity.showSnackBar(R.string.chat_room_toast_choose_for_sharing)
                }
            })
            sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner, Observer {
                if (!it.consumed()) {
                    Log.i("[Chat] Found chat message to transfer")

                    val activity = requireActivity() as MainActivity
                    activity.showSnackBar(R.string.chat_room_toast_choose_for_sharing)
                }
            })

            listViewModel.onErrorEvent.observe(viewLifecycleOwner, Observer {
                it.consume { messageResourceId ->
                    (activity as MainActivity).showSnackBar(messageResourceId)
                }
            })
        }
    }

    override fun getItemCount(): Int {
        return adapter.itemCount
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<ChatRoom>()
        for (index in indexesOfItemToDelete) {
            val chatRoom = adapter.getItemAt(index)
            list.add(chatRoom)
        }
        listViewModel.deleteChatRooms(list)
    }

    private fun scrollToTop() {
        binding.chatList.scrollToPosition(0)
    }
}

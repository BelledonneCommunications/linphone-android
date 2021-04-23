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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.GroupChatRoomMember
import org.linphone.activities.main.chat.adapters.GroupInfoParticipantsAdapter
import org.linphone.activities.main.chat.viewmodels.GroupInfoViewModel
import org.linphone.activities.main.chat.viewmodels.GroupInfoViewModelFactory
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToChatRoom
import org.linphone.activities.navigateToChatRoomCreation
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomCapabilities
import org.linphone.databinding.ChatRoomGroupInfoFragmentBinding
import org.linphone.utils.DialogUtils

class GroupInfoFragment : SecureFragment<ChatRoomGroupInfoFragmentBinding>() {
    private lateinit var viewModel: GroupInfoViewModel
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var adapter: GroupInfoParticipantsAdapter
    private var meAdminStatusChangedDialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.chat_room_group_info_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        val chatRoom: ChatRoom? = sharedViewModel.selectedGroupChatRoom.value
        isSecure = chatRoom?.currentParams?.encryptionEnabled() ?: false

        viewModel = ViewModelProvider(
            this,
            GroupInfoViewModelFactory(chatRoom)
        )[GroupInfoViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.isEncrypted.value = sharedViewModel.createEncryptedChatRoom

        adapter = GroupInfoParticipantsAdapter(
            viewLifecycleOwner,
            chatRoom?.hasCapability(ChatRoomCapabilities.Encrypted.toInt()) ?: viewModel.isEncrypted.value == true
            )
        binding.participants.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        binding.participants.layoutManager = layoutManager

        // Divider between items
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        binding.participants.addItemDecoration(dividerItemDecoration)

        viewModel.participants.observe(viewLifecycleOwner, {
            adapter.submitList(it)
        })

        viewModel.isMeAdmin.observe(viewLifecycleOwner, { isMeAdmin ->
            adapter.showAdminControls(isMeAdmin && chatRoom != null)
        })

        viewModel.meAdminChangedEvent.observe(viewLifecycleOwner, {
            it.consume { isMeAdmin ->
                showMeAdminStateChanged(isMeAdmin)
            }
        })

        adapter.participantRemovedEvent.observe(viewLifecycleOwner, {
            it.consume { participant ->
                viewModel.removeParticipant(participant)
            }
        })

        addParticipantsFromSharedViewModel()

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        viewModel.createdChatRoomEvent.observe(viewLifecycleOwner, {
            it.consume { chatRoom ->
                sharedViewModel.selectedChatRoom.value = chatRoom
                navigateToChatRoom()
            }
        })

        binding.setNextClickListener {
            if (viewModel.chatRoom != null) {
                viewModel.updateRoom()
            } else {
                viewModel.createChatRoom()
            }
        }

        binding.setParticipantsClickListener {
            sharedViewModel.createEncryptedChatRoom = viewModel.isEncrypted.value == true

            val list = arrayListOf<Address>()
            for (participant in viewModel.participants.value.orEmpty()) {
                list.add(participant.address)
            }
            sharedViewModel.chatRoomParticipants.value = list

            val args = Bundle()
            args.putBoolean("createGroup", true)
            navigateToChatRoomCreation(args)
        }

        binding.setLeaveClickListener {
            val dialogViewModel = DialogViewModel(getString(R.string.chat_room_group_info_leave_dialog_message))
            val dialog: Dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

            dialogViewModel.showDeleteButton({
                viewModel.leaveGroup()
                dialog.dismiss()
            }, getString(R.string.chat_room_group_info_leave_dialog_button))

            dialogViewModel.showCancelButton {
                dialog.dismiss()
            }

            dialog.show()
        }

        viewModel.onErrorEvent.observe(viewLifecycleOwner, {
            it.consume { messageResourceId ->
                (activity as MainActivity).showSnackBar(messageResourceId)
            }
        })
    }

    private fun addParticipantsFromSharedViewModel() {
        val participants = sharedViewModel.chatRoomParticipants.value
        if (participants != null && participants.size > 0) {
            val list = arrayListOf<GroupChatRoomMember>()

            for (address in participants) {
                val exists = viewModel.participants.value?.find {
                    it.address.weakEqual(address)
                }

                if (exists != null) {
                    list.add(exists)
                } else {
                    list.add(GroupChatRoomMember(address, false, hasLimeX3DHCapability = viewModel.isEncrypted.value == true))
                }
            }

            viewModel.participants.value = list
        }
    }

    private fun showMeAdminStateChanged(isMeAdmin: Boolean) {
        meAdminStatusChangedDialog?.dismiss()

        val message = if (isMeAdmin) {
            getString(R.string.chat_room_group_info_you_are_now_admin)
        } else {
            getString(R.string.chat_room_group_info_you_are_no_longer_admin)
        }
        val dialogViewModel = DialogViewModel(message)
        val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

        dialogViewModel.showOkButton({
            dialog.dismiss()
        })

        dialog.show()
        meAdminStatusChangedDialog = dialog
    }
}

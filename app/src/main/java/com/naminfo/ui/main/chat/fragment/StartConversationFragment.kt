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
package com.naminfo.ui.main.chat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import com.naminfo.R
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import com.naminfo.databinding.StartChatFragmentBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.main.chat.viewmodel.StartConversationViewModel
import com.naminfo.ui.main.fragment.GenericAddressPickerFragment
import com.naminfo.ui.main.model.GroupSetOrEditSubjectDialogModel
import com.naminfo.utils.DialogUtils
import com.naminfo.utils.Event
import com.naminfo.utils.hideKeyboard

@UiThread
class StartConversationFragment : GenericAddressPickerFragment() {
    companion object {
        private const val TAG = "[Start Conversation Fragment]"
    }

    private lateinit var binding: StartChatFragmentBinding

    override lateinit var viewModel: StartConversationViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = StartChatFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[StartConversationViewModel::class.java]

        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.setAskForGroupConversationSubjectClickListener {
            showGroupConversationSubjectDialog()
        }

        setupRecyclerView(binding.contactsList)

        viewModel.modelsList.observe(
            viewLifecycleOwner
        ) {
            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            adapter.submitList(it)

            attachAdapter()

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        viewModel.chatRoomCreatedEvent.observe(viewLifecycleOwner) {
            it.consume { conversationId ->
                Log.i(
                    "$TAG Conversation [$conversationId] has been created, navigating to it"
                )
                sharedViewModel.showConversationEvent.value = Event(conversationId)
                goBack()
            }
        }

        viewModel.chatRoomCreationErrorEvent.observe(viewLifecycleOwner) {
            it.consume { error ->
                Log.i("$TAG Conversation creation error, showing red toast")
                (requireActivity() as GenericActivity).showRedToast(
                    getString(error),
                    R.drawable.warning_circle
                )
            }
        }

        viewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.updateGroupChatButtonVisibility()
            }
        }
    }

    @WorkerThread
    override fun onSingleAddressSelected(address: Address, friend: Friend) {
        viewModel.createOneToOneChatRoomWith(address)
    }

    private fun showGroupConversationSubjectDialog() {
        val model = GroupSetOrEditSubjectDialogModel("", isGroupConversation = true)

        val dialog = DialogUtils.getSetOrEditGroupSubjectDialog(
            requireContext(),
            viewLifecycleOwner,
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Set conversation subject cancelled")
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume { newSubject ->
                if (newSubject.isNotEmpty()) {
                    Log.i(
                        "$TAG Conversation subject has been set to [$newSubject]"
                    )
                    viewModel.subject.value = newSubject
                    viewModel.createGroupChatRoom()

                    dialog.currentFocus?.hideKeyboard()
                    dialog.dismiss()
                } else {
                    val message = getString(R.string.conversation_invalid_empty_subject_toast)
                    val icon = R.drawable.warning_circle
                    (requireActivity() as GenericActivity).showRedToast(message, icon)
                }
            }
        }

        Log.i("$TAG Showing dialog to set conversation subject")
        dialog.show()
    }
}

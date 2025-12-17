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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatInfoFragmentBinding
import org.linphone.databinding.ChatParticipantAdminPopupMenuBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.chat.adapter.ConversationParticipantsAdapter
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.chat.viewmodel.ConversationInfoViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.ui.main.model.GroupSetOrEditSubjectDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class ConversationInfoFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Conversation Info Fragment]"
    }

    private lateinit var binding: ChatInfoFragmentBinding

    private lateinit var viewModel: ConversationInfoViewModel

    private lateinit var adapter: ConversationParticipantsAdapter

    private val args: ConversationInfoFragmentArgs by navArgs()

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationParticipantsAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatInfoFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationInfoViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val conversationId = args.conversationId
        Log.i("$TAG Looking up for conversation with conversation ID [$conversationId]")
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, conversationId)

        binding.participants.isNestedScrollingEnabled = false
        binding.participants.setHasFixedSize(false)
        binding.participants.layoutManager = LinearLayoutManager(requireContext())

        if (binding.participants.adapter != adapter) {
            binding.participants.adapter = adapter
        }

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    Log.i(
                        "$TAG Found matching conversation with conversation ID [$conversationId]"
                    )
                    startPostponedEnterTransition()
                } else {
                    Log.e("$TAG Failed to find conversation, going back")
                    goBack()
                    val message = getString(R.string.conversation_to_display_no_found_toast)
                    (requireActivity() as GenericActivity).showRedToast(
                        message,
                        R.drawable.warning_circle
                    )
                }
            }
        }

        viewModel.participants.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            Log.i("$TAG Participants list updated with [${items.size}] items")
        }

        viewModel.groupLeftEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Group has been left, leaving conversation info...")
                sharedViewModel.forceRefreshConversationInfoEvent.value = Event(true)
                goBack()
                val message = getString(R.string.conversation_group_left_toast)
                (requireActivity() as GenericActivity).showGreenToast(
                    message,
                    R.drawable.chat_teardrop_text
                )
            }
        }

        viewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG History has been deleted, leaving conversation info...")
                sharedViewModel.updateConversationLastMessageEvent.value = Event(viewModel.conversationId)
                sharedViewModel.forceRefreshConversationEvents.value = Event(true)
                goBack()
                val message = getString(R.string.conversation_info_history_deleted_toast)
                (requireActivity() as GenericActivity).showGreenToast(
                    message,
                    R.drawable.chat_teardrop_text
                )
            }
        }

        viewModel.showParticipantAdminPopupMenuEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                showParticipantAdminPopupMenu(pair.first, pair.second)
            }
        }

        viewModel.goToScheduleMeetingEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val subject = pair.first
                val participants = pair.second
                Log.i(
                    "$TAG Forward subject [$subject] and participants list of size [${participants.size}] to schedule meeting fragment"
                )
                sharedViewModel.goToScheduleMeetingEvent.postValue(Event(pair))
                sharedViewModel.navigateToMeetingsEvent.postValue(Event(true))
            }
        }

        viewModel.infoChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                sharedViewModel.forceRefreshConversationInfoEvent.postValue(Event(true))
            }
        }

        viewModel.confirmGroupCallEvent.observe(viewLifecycleOwner) {
            it.consume {
                showConfirmGroupCallPopup()
            }
        }

        sharedViewModel.listOfSelectedSipUrisEvent.observe(viewLifecycleOwner) {
            it.consume { list ->
                Log.i("$TAG Found [${list.size}] new participants to add to the group, let's do it")
                viewModel.setParticipants(list)
            }
        }

        sharedViewModel.newChatMessageEphemeralLifetimeToSetEvent.observe(viewLifecycleOwner) {
            it.consume { ephemeralLifetime ->
                Log.i(
                    "$TAG Setting [$ephemeralLifetime] as new ephemeral lifetime for messages"
                )
                viewModel.updateEphemeralLifetime(ephemeralLifetime)
            }
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setAddParticipantsClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationInfoFragment) {
                Log.i("$TAG Going into participant picker fragment")
                val selection = arrayListOf<String>()
                for (participant in viewModel.participants.value.orEmpty()) {
                    if (!participant.isParticipantMyself) {
                        // Do not add ourselves to editable list
                        selection.add(participant.address.asStringUriOnly())
                    }
                }
                Log.i("$TAG [${selection.size}] participants are already selected, keeping them")
                val action =
                    ConversationInfoFragmentDirections.actionConversationInfoFragmentToAddParticipantsFragment(
                        selection.toTypedArray()
                    )
                findNavController().navigate(action)
            }
        }

        binding.setEditSubjectClickListener {
            val currentSubject = viewModel.subject.value.orEmpty()
            val model = GroupSetOrEditSubjectDialogModel(currentSubject, isGroupConversation = true)

            val dialog = DialogUtils.getSetOrEditGroupSubjectDialog(
                requireContext(),
                viewLifecycleOwner,
                model
            )

            model.dismissEvent.observe(viewLifecycleOwner) {
                it.consume {
                    Log.i("$TAG Conversation subject edit cancelled")
                    dialog.dismiss()
                }
            }

            model.confirmEvent.observe(viewLifecycleOwner) {
                it.consume { newSubject ->
                    if (newSubject.isNotEmpty()) {
                        Log.i(
                            "$TAG Conversation subject edit confirmed, new subject is [$newSubject] (old was [$currentSubject])"
                        )
                        viewModel.updateSubject(newSubject)
                        dialog.dismiss()
                    } else {
                        val message = getString(R.string.conversation_invalid_empty_subject_toast)
                        val icon = R.drawable.warning_circle
                        (requireActivity() as GenericActivity).showRedToast(message, icon)
                    }
                }
            }

            Log.i("$TAG Showing dialog to edit conversation subject")
            dialog.show()
        }

        binding.setGoToContactClickListener {
            val refKey = viewModel.oneToOneParticipantRefKey.value
            Log.i("$TAG Trying to display participant [$refKey] contact page")
            if (!refKey.isNullOrEmpty()) {
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showContactEvent.value = Event(refKey)
            } else {
                Log.e("$TAG Can't go to contact page, friend ref key is null or empty!")
                val message = getString(
                    R.string.conversation_info_cant_find_contact_to_display_toast
                )
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
        }

        binding.setAddToContactsClickListener {
            val sipUri = viewModel.sipUri.value
            if (!sipUri.isNullOrEmpty()) {
                Log.i("$TAG Trying to add participant [$sipUri] to contacts")
                sharedViewModel.sipAddressToAddToNewContact = sipUri
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showNewContactEvent.value = Event(true)
            } else {
                Log.e("$TAG Can't add empty/null SIP URI to contacts!")
                val message = getString(
                    R.string.conversation_info_no_address_to_add_to_contact_toast
                )
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
        }

        binding.setConfigureEphemeralMessagesClickListener {
            val currentValue = viewModel.ephemeralLifetime.value ?: 0L
            if (findNavController().currentDestination?.id == R.id.conversationInfoFragment) {
                Log.i("$TAG Going to ephemeral lifetime fragment (currently [$currentValue])")
                val action =
                    ConversationInfoFragmentDirections.actionConversationInfoFragmentToConversationEphemeralLifetimeFragment(
                        currentValue
                    )
                findNavController().navigate(action)
            }
        }

        binding.setDeleteHistoryClickListener {
            showDeleteHistoryConfirmationDialog()
        }

        binding.setCopySipUriClickListener {
            copyAddressToClipboard(viewModel.sipUri.value.orEmpty())
        }

        binding.setCopyPeerSipUriClickListener {
            copyAddressToClipboard(viewModel.peerSipUri.value.orEmpty())
        }

        binding.setGoToSharedMediaClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationInfoFragment) {
                Log.i("$TAG Going to shared media fragment")
                val action =
                    ConversationInfoFragmentDirections.actionConversationInfoFragmentToConversationMediaListFragment(conversationId)
                findNavController().navigate(action)
            }
        }

        binding.setGoToSharedDocumentsClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationInfoFragment) {
                Log.i("$TAG Going to shared documents fragment")
                val action =
                    ConversationInfoFragmentDirections.actionConversationInfoFragmentToConversationDocumentsListFragment(conversationId)
                findNavController().navigate(action)
            }
        }
    }

    private fun showParticipantAdminPopupMenu(view: View, participantModel: ParticipantModel) {
        val popupView: ChatParticipantAdminPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.chat_participant_admin_popup_menu,
            null,
            false
        )

        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val address = participantModel.sipUri
        val isAdmin = participantModel.isParticipantAdmin
        popupView.isParticipantAdmin = isAdmin
        popupView.isMeAdmin = participantModel.isMyselfAdmin
        val friendRefKey = participantModel.refKey
        popupView.isParticipantContact = participantModel.friendAvailable
        popupView.disableAddContact = corePreferences.disableAddContact

        popupView.setRemoveParticipantClickListener {
            Log.i("$TAG Trying to remove participant [$address]")
            viewModel.removeParticipant(participantModel)
            popupWindow.dismiss()
        }

        popupView.setSetAdminClickListener {
            Log.i("$TAG Trying to give admin rights to participant [$address]")
            viewModel.giveAdminRightsTo(participantModel)
            popupWindow.dismiss()
        }

        popupView.setUnsetAdminClickListener {
            Log.i("$TAG Trying to remove admin rights from participant [$address]")
            viewModel.removeAdminRightsFrom(participantModel)
            popupWindow.dismiss()
        }

        popupView.setSeeContactProfileClickListener {
            Log.i("$TAG Trying to display participant [$address] contact page")
            if (friendRefKey.isNotEmpty()) {
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showContactEvent.value = Event(friendRefKey)
            } else {
                Log.e("$TAG Can't go to contact page, friend ref key is null or empty!")
                val message = getString(
                    R.string.conversation_info_cant_find_contact_to_display_toast
                )
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
            popupWindow.dismiss()
        }

        popupView.setAddToContactsClickListener {
            val sipUri = participantModel.sipUri
            if (sipUri.isNotEmpty()) {
                Log.i("$TAG Trying to add participant [${participantModel.sipUri}] to contacts")
                sharedViewModel.sipAddressToAddToNewContact = sipUri
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showNewContactEvent.value = Event(true)
            } else {
                Log.e("$TAG Can't add empty/null SIP URI to contacts!")
                val message = getString(
                    R.string.conversation_info_no_address_to_add_to_contact_toast
                )
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
            popupWindow.dismiss()
        }

        popupView.setCopySipUriClickListener {
            val sipUri = participantModel.sipUri
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SIP address", sipUri))

            val message = getString(R.string.sip_address_copied_to_clipboard_toast)
            (requireActivity() as GenericActivity).showGreenToast(
                message,
                R.drawable.check
            )
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }

    private fun showDeleteHistoryConfirmationDialog() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getDeleteConversationHistoryConfirmationDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.deleteHistory()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showConfirmGroupCallPopup() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getConfirmGroupCallDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.startGroupCall()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun copyAddressToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SIP address", value))
        val message = getString(R.string.sip_address_copied_to_clipboard_toast)
        (requireActivity() as GenericActivity).showGreenToast(
            message,
            R.drawable.check
        )
    }
}

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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatInfoFragmentBinding
import org.linphone.databinding.ChatParticipantAdminPopupMenuBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.adapter.ConversationParticipantsAdapter
import org.linphone.ui.main.chat.model.ConversationEditSubjectDialogModel
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.chat.viewmodel.ConversationInfoViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class ConversationInfoFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Conversation Info Fragment]"
    }

    private lateinit var binding: ChatInfoFragmentBinding

    private lateinit var viewModel: ConversationInfoViewModel

    private lateinit var adapter: ConversationParticipantsAdapter

    private val args: ConversationInfoFragmentArgs by navArgs()

    override fun goBack(): Boolean {
        return findNavController().popBackStack()
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
        // This fragment is displayed in a SlidingPane "child" area
        isSlidingPaneChild = true

        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationInfoViewModel::class.java]
        binding.viewModel = viewModel

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
        )
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, localSipUri, remoteSipUri)

        binding.participants.setHasFixedSize(true)
        binding.participants.layoutManager = LinearLayoutManager(requireContext())

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    Log.i(
                        "$TAG Found matching conversation for local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
                    )
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                } else {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find conversation, going back")
                        goBack()
                        val message = getString(R.string.toast_cant_find_conversation_to_display)
                        (requireActivity() as MainActivity).showRedToast(message, R.drawable.x)
                    }
                }
            }
        }
        viewModel.participants.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            Log.i("$TAG Participants list updated with [${items.size}] items")

            if (binding.participants.adapter != adapter) {
                binding.participants.adapter = adapter
            }
        }

        viewModel.groupLeftEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Group has been left, leaving conversation info...")
                goBack()
                val message = getString(R.string.toast_group_conversation_left)
                (requireActivity() as MainActivity).showGreenToast(
                    message,
                    R.drawable.chat_teardrop_text
                )
            }
        }

        viewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG History has been deleted, leaving conversation info...")
                sharedViewModel.forceRefreshConversationEvents.value = Event(true)
                goBack()
                val message = getString(R.string.toast_conversation_history_deleted)
                (requireActivity() as MainActivity).showGreenToast(
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
            it.consume { participants ->
                Log.i(
                    "$TAG Forward participants list of size [${participants.size}] to schedule meeting fragment"
                )
                sharedViewModel.goToScheduleMeetingEvent.postValue(Event(participants))
                sharedViewModel.navigateToMeetingsEvent.postValue(Event(true))
            }
        }

        viewModel.infoChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                sharedViewModel.forceRefreshConversationInfo.postValue(Event(true))
            }
        }

        viewModel.showGreenToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as MainActivity).showGreenToast(message, icon)
            }
        }

        viewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as MainActivity).showRedToast(message, icon)
            }
        }

        sharedViewModel.listOfSelectedSipUrisEvent.observe(viewLifecycleOwner) {
            it.consume { list ->
                Log.i("$TAG Found [${list.size}] new participants to add to the group, let's do it")
                viewModel.addParticipants(list)
            }
        }

        sharedViewModel.newChatMessageEphemeralLifetimeToSet.observe(viewLifecycleOwner) {
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
            val action = ConversationInfoFragmentDirections.actionConversationInfoFragmentToAddParticipantsFragment()
            findNavController().navigate(action)
        }

        binding.setEditSubjectClickListener {
            val currentSubject = viewModel.subject.value.orEmpty()
            val model = ConversationEditSubjectDialogModel(currentSubject)

            val dialog = DialogUtils.getEditConversationSubjectDialog(
                requireContext(),
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
                    Log.i(
                        "$TAG Conversation subject edit confirmed, new subject is [$newSubject] (old was [$currentSubject])"
                    )
                    viewModel.updateSubject(newSubject)
                    dialog.dismiss()
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
                val message = getString(R.string.toast_cant_find_contact_to_display)
                (requireActivity() as MainActivity).showRedToast(message, R.drawable.x)
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
                val message = getString(R.string.toast_no_address_to_add_to_contact)
                (requireActivity() as MainActivity).showRedToast(message, R.drawable.x)
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
        val friendRefKey = participantModel.avatarModel.friend.refKey
        popupView.isParticipantContact = !friendRefKey.isNullOrEmpty()

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
            if (!friendRefKey.isNullOrEmpty()) {
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showContactEvent.value = Event(friendRefKey)
            } else {
                Log.e("$TAG Can't go to contact page, friend ref key is null or empty!")
                val message = getString(R.string.toast_cant_find_contact_to_display)
                (requireActivity() as MainActivity).showRedToast(message, R.drawable.x)
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
                val message = getString(R.string.toast_no_address_to_add_to_contact)
                (requireActivity() as MainActivity).showRedToast(message, R.drawable.x)
            }
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }
}

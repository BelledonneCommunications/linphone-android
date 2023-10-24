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
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatInfoFragmentBinding
import org.linphone.databinding.ChatParticipantAdminPopupMenuBinding
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.chat.viewmodel.ConversationInfoViewModel
import org.linphone.ui.main.fragment.GenericFragment

@UiThread
class ConversationInfoFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Conversation Info Fragment]"
    }

    private lateinit var binding: ChatInfoFragmentBinding

    private lateinit var viewModel: ConversationInfoViewModel

    private val args: ConversationInfoFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatInfoFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        findNavController().popBackStack()
        return true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // This fragment is displayed in a SlidingPane "child" area
        isSlidingPaneChild = true

        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationInfoViewModel::class.java]
        binding.viewModel = viewModel

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
        )
        viewModel.findChatRoom(localSipUri, remoteSipUri)

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    Log.i(
                        "$TAG Found matching chat room for local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
                    )
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                } else {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find chat room, going back")
                        goBack()
                    }
                }
            }
        }

        viewModel.groupLeftEvent.observe(viewLifecycleOwner) {
            it.consume {
                // TODO: show toast ?
                Log.i("$TAG Group has been left, leaving conversation info...")
                goBack()
            }
        }

        viewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                // TODO: show toast ?
                Log.i("$TAG History has been deleted, leaving conversation info...")
                goBack()
            }
        }

        viewModel.showParticipantAdminPopupMenuEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                showParticipantAdminPopupMenu(pair.first, pair.second)
            }
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setAddParticipantsClickListener {
            val action = ConversationInfoFragmentDirections.actionConversationInfoFragmentToAddParticipantsFragment()
            findNavController().navigate(action)
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

        popupView.setRemoveParticipantClickListener {
            Log.w("$TAG Trying to remove participant [$address]")
            viewModel.removeParticipant(participantModel)
            popupWindow.dismiss()
        }

        popupView.setSetAdminClickListener {
            Log.w("$TAG Trying to give admin rights to participant [$address]")
            viewModel.giveAdminRightsTo(participantModel)
            popupWindow.dismiss()
        }

        popupView.setUnsetAdminClickListener {
            Log.w("$TAG Trying to remove admin rights from participant [$address]")
            viewModel.removeAdminRightsFrom(participantModel)
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }
}

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
package org.linphone.ui.main.history.fragment

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
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryContactFragmentBinding
import org.linphone.databinding.HistoryContactPopupMenuBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.ui.main.history.adapter.ContactHistoryListAdapter
import org.linphone.ui.main.history.model.ConfirmationDialogModel
import org.linphone.ui.main.history.viewmodel.ContactHistoryViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class HistoryContactFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Call Fragment]"
    }

    private lateinit var binding: HistoryContactFragmentBinding

    private lateinit var viewModel: ContactHistoryViewModel

    private lateinit var adapter: ContactHistoryListAdapter

    private val args: HistoryContactFragmentArgs by navArgs()

    override fun goBack(): Boolean {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)

        // If not done this fragment won't be paused, which will cause us issues
        val action = HistoryContactFragmentDirections.actionHistoryContactFragmentToEmptyFragment()
        findNavController().navigate(action)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ContactHistoryListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HistoryContactFragmentBinding.inflate(layoutInflater)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ContactHistoryViewModel::class.java]
        binding.viewModel = viewModel

        val callId = args.callId
        Log.i("$TAG Looking up for call log with call id [$callId]")
        viewModel.findCallLogByCallId(callId)

        binding.callHistory.setHasFixedSize(true)
        binding.callHistory.layoutManager = LinearLayoutManager(requireContext())

        if (binding.callHistory.adapter != adapter) {
            binding.callHistory.adapter = adapter
        }

        viewModel.callLogFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    Log.i(
                        "$TAG Found matching call log for call ID [$callId]"
                    )
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                        sharedViewModel.openSlidingPaneEvent.value = Event(true)
                    }
                } else {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find call log, going back")
                        goBack()
                    }
                }
            }
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.historyCallLogs.observe(viewLifecycleOwner) {
            Log.i("$TAG Call history list ready with [${it.size}] items")
            adapter.submitList(it)
        }

        viewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                sharedViewModel.forceRefreshCallLogsListEvent.value = Event(true)

                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.toast_call_history_deleted),
                    R.drawable.check
                )
                goBack() // TODO FIXME : issue with tablet when pane can't be closed
            }
        }

        viewModel.goToConversationEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                Log.i("$TAG Going to conversation [${pair.first}][${pair.second}]")
                sharedViewModel.showConversationEvent.value = Event(pair)
                sharedViewModel.navigateToConversationsEvent.value = Event(true)
            }
        }

        viewModel.conferenceToJoinEvent.observe(viewLifecycleOwner) {
            it.consume { conferenceUri ->
                Log.i("$TAG Requesting to go to waiting room for conference URI [$conferenceUri]")
                sharedViewModel.goToMeetingWaitingRoomEvent.value = Event(conferenceUri)
            }
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setMenuClickListener {
            showPopupMenu()
        }
    }

    private fun copyNumberOrAddressToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "SIP address"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        (requireActivity() as MainActivity).showGreenToast(
            getString(R.string.toast_sip_address_copied_to_clipboard),
            R.drawable.check
        )
    }

    private fun showPopupMenu() {
        val popupView: HistoryContactPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.history_contact_popup_menu,
            null,
            false
        )

        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.contactExists = viewModel.callLogModel.value?.friendExists == true
        popupView.isConferenceCallLog = viewModel.isConferenceCallLog.value == true

        popupView.setAddToContactsListener {
            sharedViewModel.sipAddressToAddToNewContact = viewModel.callLogModel.value?.displayedAddress.orEmpty()
            sharedViewModel.navigateToContactsEvent.value = Event(true)
            sharedViewModel.showNewContactEvent.value = Event(true)
            popupWindow.dismiss()
        }

        popupView.setGoToContactListener {
            val friendRefKey = viewModel.callLogModel.value?.friendRefKey
            if (!friendRefKey.isNullOrEmpty()) {
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showContactEvent.value = Event(friendRefKey)
            }
            popupWindow.dismiss()
        }

        popupView.setDeleteAllHistoryClickListener {
            showDeleteConfirmationDialog()
            popupWindow.dismiss()
        }

        popupView.setCopyNumberClickListener {
            popupWindow.dismiss()
            copyNumberOrAddressToClipboard(viewModel.callLogModel.value?.sipUri.orEmpty())
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }

    private fun showDeleteConfirmationDialog() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getRemoveCallLogsConfirmationDialog(
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
                Log.w(
                    "$TAG Removing call entries with [${viewModel.callLogModel.value?.address?.asStringUriOnly()}] from database"
                )
                viewModel.deleteHistory()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

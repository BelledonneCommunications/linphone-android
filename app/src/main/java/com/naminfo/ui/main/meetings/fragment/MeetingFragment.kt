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
package com.naminfo.ui.main.meetings.fragment

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
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
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.MeetingFragmentBinding
import com.naminfo.databinding.MeetingPopupMenuBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.main.fragment.SlidingPaneChildFragment
import com.naminfo.utils.ConfirmationDialogModel
import com.naminfo.ui.main.meetings.adapter.MeetingParticipantsAdapter
import com.naminfo.ui.main.meetings.viewmodel.MeetingViewModel
import com.naminfo.utils.DialogUtils
import com.naminfo.utils.Event

@UiThread
class MeetingFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Meeting Fragment]"
    }

    private lateinit var binding: MeetingFragmentBinding

    private lateinit var adapter: MeetingParticipantsAdapter

    private lateinit var viewModel: MeetingViewModel

    private val args: MeetingFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = MeetingParticipantsAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)

        if (findNavController().currentDestination?.id == R.id.meetingFragment) {
            // If not done this fragment won't be paused, which will cause us issues
            val action = MeetingFragmentDirections.actionMeetingFragmentToEmptyFragment()
            findNavController().navigate(action)
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[MeetingViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val uri = args.conferenceUri
        Log.i(
            "$TAG Looking up for conference with SIP URI [$uri]"
        )
        val conferenceInfo = sharedViewModel.displayedMeeting
        viewModel.findConferenceInfo(conferenceInfo, uri)

        binding.participants.isNestedScrollingEnabled = false
        binding.participants.setHasFixedSize(false)
        binding.participants.layoutManager = LinearLayoutManager(requireContext())

        if (binding.participants.adapter != adapter) {
            binding.participants.adapter = adapter
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setEditClickListener {
            val conferenceUri = viewModel.sipUri.value.orEmpty()
            if (conferenceUri.isNotEmpty()) {
                Log.i(
                    "$TAG Navigating to meeting edit fragment with conference URI [$conferenceUri]"
                )
                if (findNavController().currentDestination?.id == R.id.meetingFragment) {
                    val action =
                        MeetingFragmentDirections.actionMeetingFragmentToEditMeetingFragment(
                            conferenceUri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        binding.setShareClickListener {
            copyMeetingAddressIntoClipboard(uri)
        }

        binding.setMenuClickListener {
            showPopupMenu()
        }

        binding.setJoinClickListener {
            // Release the currently displayed meeting
            sharedViewModel.displayedMeeting = null

            val conferenceUri = args.conferenceUri
            Log.i("$TAG Requesting to go to waiting room for conference URI [$conferenceUri]")
            sharedViewModel.goToMeetingWaitingRoomEvent.value = Event(conferenceUri)
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        sharedViewModel.meetingEditedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Meeting with URI [$uri] has been edited, reloading info")
                viewModel.refreshInfo(uri)
            }
        }

        viewModel.conferenceInfoFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    startPostponedEnterTransition()
                } else {
                    Log.e("$TAG Failed to find meeting with URI [$uri], going back")
                    goBack()
                }
            }
        }

        viewModel.participants.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            Log.i("$TAG Participants list updated with [${items.size}] items")

            coreContext.postOnMainThread {
                sharedViewModel.openSlidingPaneEvent.postValue(Event(true))
            }
        }

        viewModel.conferenceInfoDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Meeting info has been deleted successfully")
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.meeting_info_deleted_toast),
                    R.drawable.trash_simple
                )
                sharedViewModel.forceRefreshMeetingsListEvent.value = Event(true)
                goBack()
            }
        }

        viewModel.conferenceCancelledEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Meeting has been cancelled successfully")
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.meeting_info_cancelled_toast),
                    R.drawable.trash_simple
                )
                viewModel.delete()
            }
        }
    }

    private fun showPopupMenu() {
        val popupView: MeetingPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.meeting_popup_menu,
            null,
            false
        )
        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val isUserOrganizer = viewModel.isEditable.value == true && viewModel.isCancelled.value == false
        popupView.cancelInsteadOfDelete = isUserOrganizer
        popupView.setDeleteClickListener {
            if (isUserOrganizer) {
                // In case we are organizer of the meeting, ask user confirmation before cancelling it
                showCancelMeetingDialog()
            } else {
                // If we're not organizer, ask user confirmation of removing itself from participants & deleting it locally
                showDeleteMeetingDialog()
            }
            popupWindow.dismiss()
        }

        popupView.setCreateCalendarEventListener {
            shareMeetingInfoAsCalendarEvent()
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }

    private fun copyMeetingAddressIntoClipboard(meetingSipUri: String) {
        Log.i("$TAG Copying conference SIP URI [$meetingSipUri] into clipboard")

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "Meeting SIP address"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, meetingSipUri))

        (requireActivity() as GenericActivity).showGreenToast(
            getString(R.string.meeting_address_copied_to_clipboard_toast),
            R.drawable.check
        )
    }

    private fun shareMeetingInfoAsCalendarEvent() {
        Log.i("$TAG Sharing conference info as Google Calendar event")

        val intent = Intent(Intent.ACTION_EDIT)
        intent.type = "vnd.android.cursor.item/event"
        intent.putExtra(CalendarContract.Events.TITLE, viewModel.subject.value)

        val description = viewModel.description.value.orEmpty()
        if (description.isNotEmpty()) {
            intent.putExtra(CalendarContract.Events.DESCRIPTION, description)
        }

        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, viewModel.startTimeStamp.value)
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, viewModel.endTimeStamp.value)

        intent.putExtra(CalendarContract.Events.CUSTOM_APP_URI, viewModel.sipUri.value)
        intent.putExtra(
            CalendarContract.Events.CUSTOM_APP_PACKAGE,
            requireContext().packageName
        )

        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Log.e("$TAG No activity found to handle intent: $exception")
        }
    }

    private fun showCancelMeetingDialog() {
        Log.i("$TAG Meeting is editable, asking whether to cancel it or not")
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getCancelMeetingDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.cancel()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteMeetingDialog() {
        Log.i("$TAG Meeting is not editable or already cancelled, asking whether to delete it or not")
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getDeleteMeetingDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.delete()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

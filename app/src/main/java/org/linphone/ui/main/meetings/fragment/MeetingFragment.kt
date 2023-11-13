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
package org.linphone.ui.main.meetings.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
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
import org.linphone.databinding.MeetingFragmentBinding
import org.linphone.databinding.MeetingPopupMenuBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.meetings.viewmodel.MeetingViewModel
import org.linphone.utils.Event

@UiThread
class MeetingFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Meeting Fragment]"
    }

    private lateinit var binding: MeetingFragmentBinding

    private lateinit var viewModel: MeetingViewModel

    private val args: MeetingFragmentArgs by navArgs()

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
        // If not done, when going back to MeetingsListFragment this fragment will be created again
        return findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // This fragment is displayed in a SlidingPane "child" area
        isSlidingPaneChild = true

        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[MeetingViewModel::class.java]
        binding.viewModel = viewModel

        val uri = args.conferenceUri
        Log.i(
            "$TAG Looking up for conference with SIP URI [$uri]"
        )
        viewModel.findConferenceInfo(uri)

        binding.setBackClickListener {
            goBack()
        }

        binding.setShareClickListener {
            Log.i("$TAG Sharing conference info as Google Calendar event")
            shareMeetingInfoAsCalendarEvent()
        }

        binding.setMenuClickListener {
            showPopupMenu()
        }

        binding.setJoinClickListener {
            val conferenceUri = args.conferenceUri
            Log.i("$TAG Requesting to go to waiting room for conference URI [$conferenceUri]")
            sharedViewModel.goToMeetingWaitingRoomEvent.value = Event(conferenceUri)
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.conferenceInfoFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                        sharedViewModel.openSlidingPaneEvent.value = Event(true)
                    }
                } else {
                    Log.e("$TAG Failed to find meeting with URI [$uri], going back")
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        goBack()
                    }
                }
            }
        }

        viewModel.conferenceInfoDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Meeting info has been deleted successfully")
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.meeting_info_deleted_toast),
                    R.drawable.trash_simple
                )
                sharedViewModel.forceRefreshMeetingsListEvent.value = Event(true)
                goBack()
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

        popupView.setDeleteClickListener {
            viewModel.delete()
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }

    private fun shareMeetingInfoAsCalendarEvent() {
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
}

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

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MeetingScheduleFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.meetings.viewmodel.ScheduleMeetingViewModel
import org.linphone.utils.Event

@UiThread
class ScheduleMeetingFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Schedule Meeting Fragment]"
    }

    private lateinit var binding: MeetingScheduleFragmentBinding

    private lateinit var viewModel: ScheduleMeetingViewModel

    private val args: ScheduleMeetingFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingScheduleFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        return findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ScheduleMeetingViewModel::class.java]
        binding.viewModel = viewModel

        val participants = args.participants
        if (!participants.isNullOrEmpty()) {
            Log.i("$TAG Found pre-populated array of participants of size [${participants.size}]")
            viewModel.addParticipants(participants.toList())
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setPickStartDateClickListener {
            val constraintsBuilder =
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
            val picker =
                MaterialDatePicker.Builder.datePicker()
                    .setCalendarConstraints(constraintsBuilder.build())
                    .setTitleText(R.string.meeting_schedule_pick_start_date_title)
                    .setSelection(viewModel.getCurrentlySelectedStartDate())
                    .build()
            picker.addOnPositiveButtonClickListener {
                val selection = picker.selection
                if (selection != null) {
                    viewModel.setStartDate(selection)
                }
            }
            picker.show(parentFragmentManager, "Start date picker")
        }

        binding.setPickEndDateClickListener {
            val constraintsBuilder =
                CalendarConstraints.Builder()
                    .setValidator(
                        DateValidatorPointForward.from(viewModel.getCurrentlySelectedStartDate())
                    )
            val picker =
                MaterialDatePicker.Builder.datePicker()
                    .setCalendarConstraints(constraintsBuilder.build())
                    .setTitleText(R.string.meeting_schedule_pick_end_date_title)
                    .setSelection(viewModel.getCurrentlySelectedEndDate())
                    .build()
            picker.addOnPositiveButtonClickListener {
                val selection = picker.selection
                if (selection != null) {
                    viewModel.setEndDate(selection)
                }
            }
            picker.show(parentFragmentManager, "End date picker")
        }

        binding.setPickStartTimeClickListener {
            val isSystem24Hour = DateFormat.is24HourFormat(requireContext())
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val picker =
                MaterialTimePicker.Builder()
                    .setTimeFormat(clockFormat)
                    .setTitleText(R.string.meeting_schedule_pick_start_time_title)
                    .setHour(viewModel.startHour)
                    .setMinute(viewModel.startMinutes)
                    .build()
            picker.addOnPositiveButtonClickListener {
                viewModel.setStartTime(picker.hour, picker.minute)
            }
            picker.show(parentFragmentManager, "Start time picker")
        }

        binding.setPickEndTimeClickListener {
            val isSystem24Hour = DateFormat.is24HourFormat(
                requireContext()
            )
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val picker =
                MaterialTimePicker.Builder()
                    .setTimeFormat(clockFormat)
                    .setTitleText(R.string.meeting_schedule_pick_end_time_title)
                    .setHour(viewModel.endHour)
                    .setMinute(viewModel.endMinutes)
                    .build()
            picker.addOnPositiveButtonClickListener {
                viewModel.setEndTime(picker.hour, picker.minute)
            }
            picker.show(parentFragmentManager, "End time picker")
        }

        binding.setPickParticipantsClickListener {
            if (findNavController().currentDestination?.id == R.id.scheduleMeetingFragment) {
                Log.i("$TAG Going into participant picker fragment")
                val action =
                    ScheduleMeetingFragmentDirections.actionScheduleMeetingFragmentToAddParticipantsFragment()
                findNavController().navigate(action)
            }
        }

        viewModel.conferenceCreatedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Conference was scheduled, leaving fragment and ask list to refresh")
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.meeting_info_created_toast),
                    R.drawable.check
                )
                sharedViewModel.forceRefreshMeetingsListEvent.value = Event(true)
                goBack()
            }
        }

        sharedViewModel.listOfSelectedSipUrisEvent.observe(viewLifecycleOwner) {
            it.consume { list ->
                Log.i(
                    "$TAG Found [${list.size}] new participants to add to the meeting, let's do it"
                )
                viewModel.addParticipants(list)
            }
        }
    }
}

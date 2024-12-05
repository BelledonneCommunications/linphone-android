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
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.meetings.viewmodel.ScheduleMeetingViewModel
import org.linphone.utils.Event

@UiThread
class ScheduleMeetingFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Schedule Meeting Fragment]"
    }

    private lateinit var binding: MeetingScheduleFragmentBinding

    private lateinit var viewModel: ScheduleMeetingViewModel

    private val args: ScheduleMeetingFragmentArgs by navArgs()

    private val timeZonePickerListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val timeZone = viewModel.availableTimeZones[position]
            Log.i("$TAG Selected time zone is now [$timeZone] at index [$position]")
            viewModel.updateTimeZone(timeZone)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingScheduleFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ScheduleMeetingViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val subject = args.subject
        if (subject.isNotEmpty()) {
            viewModel.subject.value = subject
        }

        val participants = args.participants
        if (!participants.isNullOrEmpty()) {
            Log.i("$TAG Found pre-populated array of participants of size [${participants.size}]")
            viewModel.setParticipants(participants.toList())
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
                    .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
                    .build()
            picker.addOnPositiveButtonClickListener {
                val selection = picker.selection
                if (selection != null) {
                    viewModel.setStartDate(selection)
                }
            }
            picker.show(parentFragmentManager, "Start date picker")
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
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
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
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .build()
            picker.addOnPositiveButtonClickListener {
                viewModel.setEndTime(picker.hour, picker.minute)
            }
            picker.show(parentFragmentManager, "End time picker")
        }

        binding.setPickParticipantsClickListener {
            if (findNavController().currentDestination?.id == R.id.scheduleMeetingFragment) {
                Log.i("$TAG Going into participant picker fragment")
                val selection = arrayListOf<String>()
                for (participant in viewModel.participants.value.orEmpty()) {
                    selection.add(participant.address.asStringUriOnly())
                }
                Log.i("$TAG [${selection.size}] participants are already selected, keeping them")
                val action =
                    ScheduleMeetingFragmentDirections.actionScheduleMeetingFragmentToAddParticipantsFragment(
                        selection.toTypedArray()
                    )
                findNavController().navigate(action)
            }
        }

        viewModel.conferenceCreatedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Conference was scheduled, leaving fragment and ask list to refresh")
                (requireActivity() as GenericActivity).showGreenToast(
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
                viewModel.setParticipants(list)
            }
        }

        setupTimeZonePicker()
    }

    private fun setupTimeZonePicker() {
        val timeZoneIndex = viewModel.availableTimeZones.indexOf(viewModel.selectedTimeZone.value)
        Log.i("$TAG Setting default time zone at index [$timeZoneIndex]")
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableTimeZones
        )
        adapter.setDropDownViewResource(
            R.layout.generic_dropdown_cell
        )
        binding.timezonePicker.adapter = adapter
        binding.timezonePicker.onItemSelectedListener = timeZonePickerListener
        binding.timezonePicker.setSelection(if (timeZoneIndex == -1) 0 else timeZoneIndex)
    }
}

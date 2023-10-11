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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MeetingsListFragmentBinding
import org.linphone.ui.main.fragment.AbstractTopBarFragment
import org.linphone.ui.main.meetings.adapter.MeetingsListAdapter
import org.linphone.ui.main.meetings.viewmodel.MeetingsListViewModel
import org.linphone.utils.Event
import org.linphone.utils.RecyclerViewHeaderDecoration
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

@UiThread
class MeetingsListFragment : AbstractTopBarFragment() {
    companion object {
        private const val TAG = "[Meetings List Fragment]"
    }

    private lateinit var binding: MeetingsListFragmentBinding

    private lateinit var listViewModel: MeetingsListViewModel

    private lateinit var adapter: MeetingsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        listViewModel = requireActivity().run {
            ViewModelProvider(this)[MeetingsListViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        adapter = MeetingsListAdapter(viewLifecycleOwner)
        binding.meetingsList.setHasFixedSize(true)
        binding.meetingsList.adapter = adapter

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter, true)
        binding.meetingsList.addItemDecoration(headerItemDecoration)

        val layoutManager = LinearLayoutManager(requireContext())
        binding.meetingsList.layoutManager = layoutManager

        binding.setNewMeetingClicked {
            sharedViewModel.showScheduleMeetingEvent.value = Event(true)
        }

        binding.setTodayClickListener {
            scrollToToday()
        }

        adapter.meetingClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i("$TAG Show conversation with ID [${model.id}]")
                sharedViewModel.showMeetingEvent.value = Event(model.id)
            }
        }

        listViewModel.meetings.observe(viewLifecycleOwner) {
            val currentCount = adapter.itemCount
            adapter.submitList(it)
            Log.i("$TAG Meetings list ready with [${it.size}] items")

            if (currentCount < it.size) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                    scrollToToday()
                }
            }
        }

        sharedViewModel.forceRefreshMeetingsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG We were asked to refresh the meetings list, doing it now")
                listViewModel.applyFilter()
            }
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i(
                    "$TAG Default account changed, updating avatar in top bar & re-computing meetings list"
                )
                listViewModel.applyFilter()
            }
        }

        // TopBarFragment related

        setViewModelAndTitle(
            listViewModel,
            getString(R.string.bottom_navigation_meetings_label)
        )

        listViewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            listViewModel.applyFilter(filter.trim())
        }

        listViewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.topBar.search.showKeyboard()
                } else {
                    binding.topBar.search.hideKeyboard()
                }
            }
        }
    }

    private fun scrollToToday() {
        Log.i("$TAG Scrolling to today's meeting (if any)")
        val todayMeeting = listViewModel.meetings.value.orEmpty().find {
            it.isToday
        }
        val position = if (todayMeeting != null) {
            val index = listViewModel.meetings.value.orEmpty().indexOf(todayMeeting)
            Log.i(
                "$TAG Found (at least) a meeting for today [${todayMeeting.subject.value}] at index [$index]"
            )
            // Return the element before so today's event will be properly displayed (due to header)
            if (index > 0) index - 1 else index
        } else {
            Log.i("$TAG No meeting found for today")
            0 // TODO FIXME: improve by getting closest meeting
        }
        binding.meetingsList.smoothScrollToPosition(position)
    }
}

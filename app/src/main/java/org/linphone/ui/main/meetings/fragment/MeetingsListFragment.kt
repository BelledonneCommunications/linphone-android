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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MeetingsListFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.AbstractMainFragment
import org.linphone.ui.main.history.model.ConfirmationDialogModel
import org.linphone.ui.main.meetings.adapter.MeetingsListAdapter
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.ui.main.meetings.viewmodel.MeetingsListViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.RecyclerViewHeaderDecoration

@UiThread
class MeetingsListFragment : AbstractMainFragment() {
    companion object {
        private const val TAG = "[Meetings List Fragment]"
    }

    private lateinit var binding: MeetingsListFragmentBinding

    private lateinit var listViewModel: MeetingsListViewModel

    private lateinit var adapter: MeetingsListAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private var meetingViewModelBeingCancelled: MeetingModel? = null

    private val dataObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i("$TAG First time meeting list is filled, scrolling to 'today'")
                scrollToToday()
            }
        }
    }

    override fun onDefaultAccountChanged() {
        if (!goToContactsIfMeetingsAreDisabledForCurrentlyDefaultAccount()) {
            Log.i(
                "$TAG Default account changed, updating avatar in top bar & re-computing meetings list"
            )
            listViewModel.applyFilter()
        }
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (
            findNavController().currentDestination?.id == R.id.scheduleMeetingFragment ||
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment
        ) {
            // Holds fragment in place while new fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = MeetingsListAdapter()
    }

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

        listViewModel = ViewModelProvider(this)[MeetingsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        binding.meetingsList.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(requireContext())
        binding.meetingsList.layoutManager = layoutManager

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.meetingsList.addItemDecoration(headerItemDecoration)
        binding.meetingsList.outlineProvider = outlineProvider
        binding.meetingsList.clipToOutline = true

        binding.setNewMeetingClicked {
            if (findNavController().currentDestination?.id == R.id.meetingsListFragment) {
                Log.i("$TAG Navigating to schedule meeting fragment")
                val action =
                    MeetingsListFragmentDirections.actionMeetingsListFragmentToScheduleMeetingFragment()
                findNavController().navigate(action)
            }
        }

        binding.setTodayClickListener {
            scrollToToday()
        }

        adapter.meetingClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                if (model.isCancelled) {
                    Log.w("$TAG Meeting with ID [${model.id}] is cancelled, can't show the details")
                } else {
                    Log.i("$TAG Show meeting with ID [${model.id}]")
                    sharedViewModel.displayedMeeting = model.conferenceInfo
                    val action = MeetingFragmentDirections.actionGlobalMeetingFragment(model.id)
                    binding.meetingsNavContainer.findNavController().navigate(action)
                }
            }
        }

        listViewModel.meetings.observe(viewLifecycleOwner) {
            val newCount = it.size
            adapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.meetingsList.adapter != adapter) {
                binding.meetingsList.adapter = adapter
            }

            Log.i("$TAG Meetings list ready with [$newCount] items")
            listViewModel.fetchInProgress.value = false
        }

        listViewModel.conferenceCancelledEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Meeting has been cancelled successfully, deleting it")
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.meeting_info_cancelled_toast),
                    R.drawable.trash_simple
                )

                meetingViewModelBeingCancelled?.delete()
                meetingViewModelBeingCancelled = null
                listViewModel.applyFilter()
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.meeting_info_deleted_toast),
                    R.drawable.trash_simple
                )
            }
        }

        adapter.meetingLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = MeetingsMenuDialogFragment(
                    { // onDismiss
                        adapter.resetSelection()
                    },
                    { // onDelete
                        if (model.isOrganizer() && !model.isCancelled) {
                            showCancelMeetingDialog(model)
                        } else {
                            Log.i("$TAG Deleting meeting [${model.id}]")
                            model.delete()
                            listViewModel.applyFilter()

                            (requireActivity() as GenericActivity).showGreenToast(
                                getString(R.string.meeting_info_deleted_toast),
                                R.drawable.trash_simple
                            )
                        }
                    }
                )
                modalBottomSheet.show(parentFragmentManager, MeetingsMenuDialogFragment.TAG)
                bottomSheetDialog = modalBottomSheet
            }
        }

        sharedViewModel.forceRefreshMeetingsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG We were asked to refresh the meetings list, doing it now")
                listViewModel.applyFilter()
            }
        }

        sharedViewModel.goToMeetingWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                if (findNavController().currentDestination?.id == R.id.meetingsListFragment) {
                    Log.i("$TAG Navigating to meeting waiting room fragment with URI [$uri]")
                    val action =
                        MeetingsListFragmentDirections.actionMeetingsListFragmentToMeetingWaitingRoomFragment(
                            uri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.goToScheduleMeetingEvent.observe(viewLifecycleOwner) {
            it.consume { participants ->
                if (findNavController().currentDestination?.id == R.id.meetingsListFragment) {
                    val participantsArray = participants.toTypedArray()
                    Log.i(
                        "$TAG Going to schedule meeting fragment with pre-populated participants array of size [${participantsArray.size}]"
                    )
                    val action =
                        MeetingsListFragmentDirections.actionMeetingsListFragmentToScheduleMeetingFragment(
                            participantsArray
                        )
                    findNavController().navigate(action)
                }
            }
        }

        // AbstractMainFragment related

        listViewModel.title.value = getString(R.string.bottom_navigation_meetings_label)
        setViewModel(listViewModel)
        initViews(
            binding.slidingPaneLayout,
            binding.topBar,
            binding.bottomNavBar,
            R.id.meetingsListFragment
        )
    }

    override fun onPause() {
        super.onPause()

        try {
            adapter.unregisterAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    override fun onResume() {
        super.onResume()

        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to register data observer to adapter: $e")
        }

        goToContactsIfMeetingsAreDisabledForCurrentlyDefaultAccount()
    }

    private fun goToContactsIfMeetingsAreDisabledForCurrentlyDefaultAccount(): Boolean {
        if (listViewModel.hideMeetings.value == true) {
            Log.w(
                "$TAG Resuming fragment that should no longer be accessible, going to contacts list instead"
            )
            sharedViewModel.navigateToContactsEvent.value = Event(true)
            return true
        }
        return false
    }

    private fun scrollToToday() {
        val todayMeeting = listViewModel.meetings.value.orEmpty().find {
            it.isToday
        }
        val index = listViewModel.meetings.value.orEmpty().indexOf(todayMeeting)
        Log.i("$TAG 'Today' is at position [$index]")
        binding.meetingsList.smoothScrollToPosition(index) // Workaround to have header decoration visible at top
        (binding.meetingsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            index,
            AppUtils.getDimension(R.dimen.meeting_list_decoration_height).toInt()
        )
    }

    private fun showCancelMeetingDialog(meetingModel: MeetingModel) {
        Log.i("$TAG Meeting is editable, asking whether to cancel it or not before deleting it")

        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getCancelMeetingDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.cancelEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Deleting meeting [${meetingModel.id}]")
                meetingModel.delete()
                listViewModel.applyFilter()

                dialog.dismiss()
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.meeting_info_deleted_toast),
                    R.drawable.trash_simple
                )
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Cancelling meeting [${meetingModel.id}]")
                meetingViewModelBeingCancelled = meetingModel
                listViewModel.cancelMeeting(meetingModel.conferenceInfo)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

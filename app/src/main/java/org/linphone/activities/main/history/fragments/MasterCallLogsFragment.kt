/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.history.fragments

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.clearDisplayedCallHistory
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.history.adapters.CallLogsListAdapter
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.activities.main.history.viewmodels.CallLogsListViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.TabsViewModel
import org.linphone.activities.navigateToCallHistory
import org.linphone.activities.navigateToConferenceCallHistory
import org.linphone.activities.navigateToDialer
import org.linphone.core.ConferenceInfo
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryMasterFragmentBinding
import org.linphone.utils.*

class MasterCallLogsFragment : MasterFragment<HistoryMasterFragmentBinding, CallLogsListAdapter>() {
    override val dialogConfirmationMessageBeforeRemoval = R.plurals.history_delete_dialog
    private lateinit var listViewModel: CallLogsListViewModel

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == 0 && itemCount == 1) {
                scrollToTop()
            }
        }
    }

    override fun getLayoutId(): Int = R.layout.history_master_fragment

    override fun onDestroyView() {
        binding.callLogsList.adapter = null
        adapter.unregisterAdapterDataObserver(observer)
        super.onDestroyView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useMaterialSharedAxisXForwardAnimation = false

        if (corePreferences.enableAnimations) {
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val axis = if (portraitOrientation) MaterialSharedAxis.X else MaterialSharedAxis.Y
            enterTransition = MaterialSharedAxis(axis, false)
            reenterTransition = MaterialSharedAxis(axis, false)
            returnTransition = MaterialSharedAxis(axis, true)
            exitTransition = MaterialSharedAxis(axis, true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        listViewModel = ViewModelProvider(this)[CallLogsListViewModel::class.java]
        binding.viewModel = listViewModel

        /* Shared view model & sliding pane related */

        setUpSlidingPane(binding.slidingPane)

        sharedViewModel.layoutChangedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                sharedViewModel.isSlidingPaneSlideable.value = binding.slidingPane.isSlideable
                if (binding.slidingPane.isSlideable) {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.history_nav_container) as NavHostFragment
                    if (navHostFragment.navController.currentDestination?.id == R.id.emptyCallHistoryFragment) {
                        Log.i("[History] Foldable device has been folded, closing side pane with empty fragment")
                        binding.slidingPane.closePane()
                    }
                }
            }
        }

        /* End of shared view model & sliding pane related */

        _adapter = CallLogsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(observer)
        binding.callLogsList.setHasFixedSize(true)
        binding.callLogsList.adapter = adapter

        binding.setEditClickListener {
            listSelectionViewModel.isEditionEnabled.value = true
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.callLogsList.layoutManager = layoutManager

        // Swipe action
        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        val white = ContextCompat.getColor(requireContext(), R.color.white_color)

        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action(
            requireContext().getString(R.string.dialog_delete),
            white,
            ContextCompat.getColor(requireContext(), R.color.red_color)
        )
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {}

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val viewModel = DialogViewModel(getString(R.string.history_delete_one_dialog))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                val index = viewHolder.bindingAdapterPosition
                if (index < 0 || index >= adapter.currentList.size) {
                    Log.e("[History] Index is out of bound, can't delete call log")
                } else {
                    viewModel.showCancelButton {
                        adapter.notifyItemChanged(index)
                        dialog.dismiss()
                    }

                    viewModel.showDeleteButton(
                        {
                            val deletedCallGroup = adapter.currentList[index]
                            listViewModel.deleteCallLogGroup(deletedCallGroup)
                            if (!binding.slidingPane.isSlideable &&
                                deletedCallGroup.lastCallLog.callId == sharedViewModel.selectedCallLogGroup.value?.lastCallLog?.callId
                            ) {
                                Log.i("[History] Currently displayed history has been deleted, removing detail fragment")
                                clearDisplayedCallHistory()
                            }
                            dialog.dismiss()
                        },
                        getString(R.string.dialog_delete)
                    )
                }

                dialog.show()
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.LEFT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.callLogsList)

        // Divider between items
        binding.callLogsList.addItemDecoration(AppUtils.getDividerDecoration(requireContext(), layoutManager))

        // Displays formatted date header
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.callLogsList.addItemDecoration(headerItemDecoration)

        listViewModel.callLogs.observe(
            viewLifecycleOwner
        ) { callLogs ->
            adapter.submitList(callLogs)
        }

        listViewModel.contactsUpdatedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
        }

        adapter.selectedCallLogEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { callLog ->
                sharedViewModel.selectedCallLogGroup.value = callLog
                if (callLog.lastCallLog.wasConference()) {
                    navigateToConferenceCallHistory(binding.slidingPane)
                } else {
                    navigateToCallHistory(binding.slidingPane)
                }
            }
        }

        adapter.startCallToEvent.observe(
            viewLifecycleOwner,
        ) {
            it.consume { callLogGroup ->
                val callLog = callLogGroup.lastCallLog
                val conferenceInfo = callLog.conferenceInfo
                when {
                    conferenceInfo != null -> {
                        if (conferenceInfo.state == ConferenceInfo.State.Cancelled) {
                            var snackRes = R.string.conference_scheduled_cancelled_by_organizer

                            val organizer = conferenceInfo.organizer
                            if (organizer != null) {
                                val localAccount = coreContext.core.accountList.find { account ->
                                    val address = account.params.identityAddress
                                    address != null && organizer.weakEqual(address)
                                }
                                if (localAccount != null) {
                                    snackRes = R.string.conference_scheduled_cancelled_by_me
                                }
                            }

                            val activity = requireActivity() as MainActivity
                            activity.showSnackBar(snackRes)
                        } else {
                            navigateToConferenceWaitingRoom(
                                conferenceInfo.uri?.asStringUriOnly().orEmpty(),
                                conferenceInfo.subject
                            )
                        }
                    }
                    coreContext.core.callsNb > 0 -> {
                        val cleanAddress = LinphoneUtils.getCleanedAddress(callLog.remoteAddress)
                        Log.i("[History] Starting dialer with pre-filled URI ${cleanAddress.asStringUriOnly()}, is transfer? ${sharedViewModel.pendingCallTransfer}")
                        sharedViewModel.updateDialerAnimationsBasedOnDestination.value = Event(R.id.masterCallLogsFragment)
                        val args = Bundle()
                        args.putString("URI", cleanAddress.asStringUriOnly())
                        args.putBoolean("Transfer", sharedViewModel.pendingCallTransfer)
                        args.putBoolean("SkipAutoCallStart", true) // If auto start call setting is enabled, ignore it
                        navigateToDialer(args)
                    }
                    else -> {
                        val cleanAddress = LinphoneUtils.getCleanedAddress(callLog.remoteAddress)
                        val localAddress = callLogGroup.lastCallLog.localAddress
                        Log.i("[History] Starting call to ${cleanAddress.asStringUriOnly()} with local address ${localAddress.asStringUriOnly()}")
                        coreContext.startCall(cleanAddress, localAddress = localAddress)
                    }
                }
            }
        }

        coreContext.core.resetMissedCallsCount()
        coreContext.notificationsManager.dismissMissedCallNotification()
    }

    override fun onResume() {
        super.onResume()

        val tabsViewModel = requireActivity().run {
            ViewModelProvider(this)[TabsViewModel::class.java]
        }
        tabsViewModel.updateMissedCallCount()
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<GroupedCallLogData>()
        var closeSlidingPane = false
        for (index in indexesOfItemToDelete) {
            val callLogGroup = adapter.currentList[index]
            list.add(callLogGroup)

            if (callLogGroup.lastCallLog.callId == sharedViewModel.selectedCallLogGroup.value?.lastCallLog?.callId) {
                closeSlidingPane = true
            }
        }
        listViewModel.deleteCallLogGroups(list)

        if (!binding.slidingPane.isSlideable && closeSlidingPane) {
            Log.i("[History] Currently displayed history has been deleted, removing detail fragment")
            clearDisplayedCallHistory()
        }
    }

    private fun scrollToTop() {
        binding.callLogsList.scrollToPosition(0)
    }
}

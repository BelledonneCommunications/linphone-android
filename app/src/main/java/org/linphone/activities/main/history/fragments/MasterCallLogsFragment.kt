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
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.history.adapters.CallLogsListAdapter
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.activities.main.history.viewmodels.CallLogsListViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.main.viewmodels.TabsViewModel
import org.linphone.activities.navigateToCallHistory
import org.linphone.activities.navigateToDialer
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryMasterFragmentBinding
import org.linphone.utils.*

class MasterCallLogsFragment : MasterFragment<HistoryMasterFragmentBinding, CallLogsListAdapter>() {
    override val dialogConfirmationMessageBeforeRemoval = R.plurals.history_delete_dialog
    private lateinit var listViewModel: CallLogsListViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        listViewModel = ViewModelProvider(this).get(CallLogsListViewModel::class.java)
        binding.viewModel = listViewModel

        /* Shared view model & sliding pane related */

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        view.doOnPreDraw { sharedViewModel.canSlidingPaneBeClosed.value = binding.slidingPane.isSlideable }

        sharedViewModel.closeSlidingPaneEvent.observe(viewLifecycleOwner, {
            it.consume {
                if (!binding.slidingPane.closePane()) {
                    goBack()
                }
            }
        })
        sharedViewModel.layoutChangedEvent.observe(viewLifecycleOwner, {
            it.consume {
                sharedViewModel.canSlidingPaneBeClosed.value = binding.slidingPane.isSlideable
            }
        })
        binding.slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        binding.slidingPane.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) { }

            override fun onPanelOpened(panel: View) {
                if (binding.slidingPane.isSlideable) {
                    (requireActivity() as MainActivity).hideTabsFragment()
                }
            }

            override fun onPanelClosed(panel: View) {
                if (binding.slidingPane.isSlideable) {
                    (requireActivity() as MainActivity).showTabsFragment()
                }
            }
        })

        /* End of hared view model & sliding pane related */

        _adapter = CallLogsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(observer)
        binding.callLogsList.setHasFixedSize(true)
        binding.callLogsList.adapter = adapter

        binding.setEditClickListener {
            listSelectionViewModel.isEditionEnabled.value = true
        }

        val layoutManager = LinearLayoutManager(activity)
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

                viewModel.showCancelButton {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                    dialog.dismiss()
                }

                viewModel.showDeleteButton({
                    listViewModel.deleteCallLogGroup(adapter.currentList[viewHolder.adapterPosition])
                    dialog.dismiss()
                }, getString(R.string.dialog_delete))

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

        listViewModel.callLogs.observe(viewLifecycleOwner, { callLogs ->
            if (listViewModel.missedCallLogsSelected.value == false) {
                adapter.submitList(callLogs)
            }
        })

        listViewModel.missedCallLogs.observe(viewLifecycleOwner, { callLogs ->
            if (listViewModel.missedCallLogsSelected.value == true) {
                adapter.submitList(callLogs)
            }
        })

        listViewModel.missedCallLogsSelected.observe(viewLifecycleOwner, {
            if (it) {
                adapter.submitList(listViewModel.missedCallLogs.value)
            } else {
                adapter.submitList(listViewModel.callLogs.value)
            }
        })

        listViewModel.contactsUpdatedEvent.observe(viewLifecycleOwner, {
            it.consume {
                adapter.notifyDataSetChanged()
            }
        })

        adapter.selectedCallLogEvent.observe(viewLifecycleOwner, {
            it.consume { callLog ->
                sharedViewModel.selectedCallLogGroup.value = callLog
                binding.slidingPane.openPane()
                navigateToCallHistory()
            }
        })

        adapter.startCallToEvent.observe(viewLifecycleOwner, {
            it.consume { callLogGroup ->
                val remoteAddress = callLogGroup.lastCallLog.remoteAddress
                if (coreContext.core.callsNb > 0) {
                    Log.i("[History] Starting dialer with pre-filled URI ${remoteAddress.asStringUriOnly()}, is transfer? ${sharedViewModel.pendingCallTransfer}")
                    val args = Bundle()
                    args.putString("URI", remoteAddress.asStringUriOnly())
                    args.putBoolean("Transfer", sharedViewModel.pendingCallTransfer)
                    args.putBoolean("SkipAutoCallStart", true) // If auto start call setting is enabled, ignore it
                    navigateToDialer(args)
                } else {
                    val localAddress = callLogGroup.lastCallLog.localAddress
                    coreContext.startCall(remoteAddress, localAddress = localAddress)
                }
            }
        })

        binding.setAllCallLogsToggleClickListener {
            listViewModel.missedCallLogsSelected.value = false
        }
        binding.setMissedCallLogsToggleClickListener {
            listViewModel.missedCallLogsSelected.value = true
        }
    }

    override fun onResume() {
        super.onResume()

        coreContext.core.resetMissedCallsCount()
        coreContext.notificationsManager.dismissMissedCallNotification()

        val tabsViewModel = requireActivity().run {
            ViewModelProvider(this).get(TabsViewModel::class.java)
        }
        tabsViewModel.updateMissedCallCount()
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<GroupedCallLogData>()
        for (index in indexesOfItemToDelete) {
            val callLogGroup = adapter.currentList[index]
            list.add(callLogGroup)
        }
        listViewModel.deleteCallLogGroups(list)
    }

    private fun scrollToTop() {
        binding.callLogsList.scrollToPosition(0)
    }
}

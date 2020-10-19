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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.history.adapters.CallLogsListAdapter
import org.linphone.activities.main.history.viewmodels.CallLogsListViewModel
import org.linphone.activities.main.history.viewmodels.GroupedCallLogViewModel
import org.linphone.activities.main.navigateToCallHistory
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.main.viewmodels.TabsViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryMasterFragmentBinding
import org.linphone.utils.*

class MasterCallLogsFragment : MasterFragment<HistoryMasterFragmentBinding, CallLogsListAdapter>() {
    override val dialogConfirmationMessageBeforeRemoval = R.plurals.history_delete_dialog
    private lateinit var listViewModel: CallLogsListViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            scrollToTop()
        }
    }

    override fun getLayoutId(): Int = R.layout.history_master_fragment

    override fun onDestroyView() {
        binding.callLogsList.adapter = null
        adapter.unregisterAdapterDataObserver(observer)
        super.onDestroyView()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        listViewModel = ViewModelProvider(this).get(CallLogsListViewModel::class.java)
        binding.viewModel = listViewModel

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        _adapter = CallLogsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(observer)
        binding.callLogsList.adapter = adapter

        // For transition animation
        binding.callLogsList.apply {
            postponeEnterTransition()
            viewTreeObserver.addOnPreDrawListener {
                startPostponedEnterTransition()
                true
            }
        }

        binding.setEditClickListener {
            listSelectionViewModel.isEditionEnabled.value = true
        }

        val layoutManager = LinearLayoutManager(activity)
        binding.callLogsList.layoutManager = layoutManager

        // Swipe action
        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        val white = ContextCompat.getColor(requireContext(), R.color.white_color)

        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action("Delete", white, ContextCompat.getColor(requireContext(), R.color.red_color))
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
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        binding.callLogsList.addItemDecoration(dividerItemDecoration)

        // Displays formatted date header
        val headerItemDecoration = RecyclerViewHeaderDecoration(adapter)
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
                navigateToCallHistory(adapter.selectionFragmentNavigationExtras)
            }
        })

        adapter.startCallToEvent.observe(viewLifecycleOwner, {
            it.consume { address ->
                if (coreContext.core.callsNb > 0) {
                    Log.i("[History] Starting dialer with pre-filled URI ${address.asStringUriOnly()}, is transfer? ${sharedViewModel.pendingCallTransfer}")
                    val args = Bundle()
                    args.putString("URI", address.asStringUriOnly())
                    args.putBoolean("Transfer", sharedViewModel.pendingCallTransfer)
                    args.putBoolean("SkipAutoCallStart", true) // If auto start call setting is enabled, ignore it
                    findNavController().navigate(
                        R.id.action_global_dialerFragment,
                        args
                    )
                } else {
                    coreContext.startCall(address)
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

        val tabsViewModel = activity?.run {
            ViewModelProvider(this).get(TabsViewModel::class.java)
        }
        tabsViewModel?.updateMissedCallCount()
    }

    override fun getItemCount(): Int {
        return adapter.itemCount
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<GroupedCallLogViewModel>()
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

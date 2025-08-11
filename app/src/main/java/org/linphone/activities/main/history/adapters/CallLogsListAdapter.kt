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
package org.linphone.activities.main.history.adapters

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.adapters.SelectionListAdapter
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.activities.voip.TransferState
import org.linphone.databinding.GenericListHeaderBinding
import org.linphone.databinding.HistoryListCellBinding
import org.linphone.models.callhistory.CallHistoryItemViewModel
import org.linphone.models.callhistory.PbxType
import org.linphone.services.TransferService
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter
import org.linphone.utils.Log
import org.linphone.utils.TimestampUtils

class CallLogsListAdapter(
    selectionVM: ListTopBarViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private val context: Context?
) : SelectionListAdapter<GroupedCallLogData, RecyclerView.ViewHolder>(
    selectionVM,
    CallLogDiffCallback()
),
    HeaderAdapter {
    val selectedCallLogEvent: MutableLiveData<Event<GroupedCallLogData>> by lazy {
        MutableLiveData<Event<GroupedCallLogData>>()
    }

    val startCallToEvent: MutableLiveData<Event<GroupedCallLogData>> by lazy {
        MutableLiveData<Event<GroupedCallLogData>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: HistoryListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.history_list_cell,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: HistoryListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(callLogGroup: GroupedCallLogData) {
            with(binding) {
                val callLogViewModel = callLogGroup.lastCallLogViewModel
                viewModel = callLogViewModel

                lifecycleOwner = viewLifecycleOwner

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(
                    viewLifecycleOwner
                ) {
                    position = bindingAdapterPosition
                }

                setClickListener {
                    try {
                        if (selectionViewModel.isEditionEnabled.value == true) {
                            selectionViewModel.onToggleSelect(bindingAdapterPosition)
                        } else {
                            val lastCallLog = callLogGroup.lastCallLog
                            if (lastCallLog is CallHistoryItemViewModel) {
                                if (lastCallLog.call.pbxType != PbxType.Teams) {
                                    showMakeCallDialog(callLogGroup)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("setClickListener", e)
                    }
                }

                setLongClickListener {
                    // SD 20250415 - Removed long click behaviour
//                    if (selectionViewModel.isEditionEnabled.value == false) {
//                        selectionViewModel.isEditionEnabled.value = true
//                        // Selection will be handled by click listener
//                        true
//                    }
                    false
                }

                // This listener is disabled when in edition mode
                setDetailsClickListener {
                    selectedCallLogEvent.value = Event(callLogGroup)
                }

                groupCount = callLogGroup.callLogs.size

                executePendingBindings()
            }
        }
    }

    private fun showMakeCallDialog(callLogGroup: GroupedCallLogData) {
        val callHistoryItemViewModel = callLogGroup.lastCallLog
        if (callHistoryItemViewModel is CallHistoryItemViewModel) {
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.call_history_make_call_dialog, null)

            val titleTextView: TextView = dialogView.findViewById(
                R.id.callHistoryMakeCallDialogTitle
            )
            titleTextView.text = callHistoryItemViewModel.contactName

            val dialog = AlertDialog.Builder(context)
                .setView(dialogView) // Set the custom layout as the dialog's content
                .create()

            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_background)

            val button: Button = dialogView.findViewById(R.id.callHistoryMakeCallDialogButton)

            val isTransfer = TransferService.getInstance().transferState.value == TransferState.PENDING_BLIND

            if (isTransfer) button.text = "Transfer"

            button.setOnClickListener {
                dialog.dismiss()
                startCallToEvent.value = Event(callLogGroup)
            }

            dialog.show()
        }
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false
        val callLogGroup = getItem(position)
        val date = callLogGroup.lastCallLogStartTimestamp
        val previousPosition = position - 1
        return if (previousPosition >= 0) {
            val previousItemDate = getItem(previousPosition).lastCallLogStartTimestamp
            !TimestampUtils.isSameDay(date, previousItemDate)
        } else {
            true
        }
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val callLog = getItem(position)
        val date = formatDate(context, callLog.lastCallLogStartTimestamp)
        val binding: GenericListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.generic_list_header,
            null,
            false
        )
        binding.title = date
        binding.executePendingBindings()
        return binding.root
    }

    private fun formatDate(context: Context, date: Long): String {
        if (TimestampUtils.isToday(date)) {
            return context.getString(R.string.today)
        } else if (TimestampUtils.isYesterday(date)) {
            return context.getString(R.string.yesterday)
        }
        return TimestampUtils.toString(date, onlyDate = true, shortDate = false, hideYear = false)
    }
}

private class CallLogDiffCallback : DiffUtil.ItemCallback<GroupedCallLogData>() {
    override fun areItemsTheSame(
        oldItem: GroupedCallLogData,
        newItem: GroupedCallLogData
    ): Boolean {
        return false // oldItem.lastCallLogId == newItem.lastCallLogId //FixME: The original code prevents the UI redrawing
    }

    override fun areContentsTheSame(
        oldItem: GroupedCallLogData,
        newItem: GroupedCallLogData
    ): Boolean {
        return false // oldItem.callLogs.size == newItem.callLogs.size //FixME: The original code prevents the UI redrawing
    }
}

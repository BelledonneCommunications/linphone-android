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
package org.linphone.ui.main.history.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.databinding.GenericAddressPickerListDecorationBinding
import org.linphone.databinding.HistoryListCellBinding
import org.linphone.databinding.HistoryListContactSuggestionCellBinding
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.ui.main.history.model.CallLogModelWrapper
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class HistoryListAdapter :
    ListAdapter<CallLogModelWrapper, RecyclerView.ViewHolder>(CallLogDiffCallback()),
    HeaderAdapter {
    companion object {
        private const val CALL_LOG_TYPE = 0
        private const val CONTACT_TYPE = 1
        private const val SUGGESTION_TYPE = 2
    }

    var selectedAdapterPosition = -1

    val callLogClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData()
    }

    val callLogLongClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData()
    }

    val callLogCallBackClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData()
    }

    val callFriendClickedEvent: MutableLiveData<Event<Friend>> by lazy {
        MutableLiveData()
    }

    val callAddressClickedEvent: MutableLiveData<Event<Address>> by lazy {
        MutableLiveData()
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        // Don't show header for call history section
        if (position == 0 && getItemViewType(0) == CALL_LOG_TYPE) {
            return false
        }

        return getItemViewType(position) != getItemViewType(position - 1)
    }

    override fun getHeaderViewForPosition(
        context: Context,
        position: Int
    ): View {
        val binding = GenericAddressPickerListDecorationBinding.inflate(
            LayoutInflater.from(context)
        )
        binding.header.text = when (getItemViewType(position)) {
            SUGGESTION_TYPE -> {
                AppUtils.getString(R.string.generic_address_picker_suggestions_list_title)
            }
            else -> {
                AppUtils.getString(R.string.generic_address_picker_contacts_list_title)
            }
        }
        return binding.root
    }

    override fun getItemViewType(position: Int): Int {
        try {
            val model = getItem(position)
            return if (model.isCallLog) {
                CALL_LOG_TYPE
            } else if (model.contactModel?.friend != null) {
                CONTACT_TYPE
            } else {
                SUGGESTION_TYPE
            }
        } catch (ioobe: IndexOutOfBoundsException) {

        }
        return CALL_LOG_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            CALL_LOG_TYPE -> {
                val binding: HistoryListCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.history_list_cell,
                    parent,
                    false
                )
                val viewHolder = CallLogViewHolder(binding)
                binding.apply {
                    lifecycleOwner = parent.findViewTreeLifecycleOwner()

                    setOnClickListener {
                        callLogClickedEvent.value = Event(model!!)
                    }

                    setOnLongClickListener {
                        selectedAdapterPosition = viewHolder.bindingAdapterPosition
                        root.isSelected = true
                        callLogLongClickedEvent.value = Event(model!!)
                        true
                    }

                    setOnCallClickListener {
                        callLogCallBackClickedEvent.value = Event(model!!)
                    }
                }
                viewHolder
            }
            else -> {
                val binding: HistoryListContactSuggestionCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.history_list_contact_suggestion_cell,
                    parent,
                    false
                )
                binding.apply {
                    lifecycleOwner = parent.findViewTreeLifecycleOwner()

                    setOnCallClickListener {
                        val friend = model?.friend
                        if (friend != null) {
                            callFriendClickedEvent.value = Event(friend)
                        } else {
                            callAddressClickedEvent.value = Event(model!!.address)
                        }
                    }
                }
                ContactSuggestionViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            CALL_LOG_TYPE -> (holder as CallLogViewHolder).bind(getItem(position).callLogModel!!)
            else -> (holder as ContactSuggestionViewHolder).bind(getItem(position).contactModel!!)
        }
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class CallLogViewHolder(
        val binding: HistoryListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(callLogModel: CallLogModel) {
            with(binding) {
                model = callLogModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    class ContactSuggestionViewHolder(
        val binding: HistoryListContactSuggestionCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationContactOrSuggestionModel: ConversationContactOrSuggestionModel) {
            with(binding) {
                model = conversationContactOrSuggestionModel

                executePendingBindings()
            }
        }
    }

    private class CallLogDiffCallback : DiffUtil.ItemCallback<CallLogModelWrapper>() {
        override fun areItemsTheSame(oldItem: CallLogModelWrapper, newItem: CallLogModelWrapper): Boolean {
            if (oldItem.isCallLog && newItem.isCallLog) {
                return oldItem.callLogModel?.id == newItem.callLogModel?.id && oldItem.callLogModel?.timestamp == newItem.callLogModel?.timestamp
            } else if (oldItem.isContactOrSuggestion && newItem.isContactOrSuggestion) {
                return oldItem.contactModel?.id == newItem.contactModel?.id
            }
            return false
        }

        override fun areContentsTheSame(oldItem: CallLogModelWrapper, newItem: CallLogModelWrapper): Boolean {
            if (oldItem.isCallLog && newItem.isCallLog) {
                return newItem.callLogModel?.avatarModel?.compare(oldItem.callLogModel?.avatarModel) == true
            }
            return false
        }
    }
}

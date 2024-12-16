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
package org.linphone.ui.main.adapter

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
import org.linphone.databinding.GenericAddressPickerContactListCellBinding
import org.linphone.databinding.GenericAddressPickerConversationListCellBinding
import org.linphone.databinding.GenericAddressPickerListDecorationBinding
import org.linphone.databinding.GenericAddressPickerSuggestionListCellBinding
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class ConversationsContactsAndSuggestionsListAdapter :
    ListAdapter<ConversationContactOrSuggestionModel, RecyclerView.ViewHolder>(
        ConversationContactOrSuggestionDiffCallback()
    ),
    HeaderAdapter {
    companion object {
        private const val CONTACT_TYPE = 0
        private const val FAVORITE_TYPE = 1
        private const val SUGGESTION_TYPE = 2
        private const val CONVERSATION_TYPE = 3
    }

    val onClickedEvent: MutableLiveData<Event<ConversationContactOrSuggestionModel>> by lazy {
        MutableLiveData<Event<ConversationContactOrSuggestionModel>>()
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position == 0) { // Always start by a header
            return true
        }

        return getItemViewType(position) != getItemViewType(position - 1)
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = GenericAddressPickerListDecorationBinding.inflate(
            LayoutInflater.from(context)
        )
        binding.header.text = when (getItemViewType(position)) {
            CONVERSATION_TYPE -> {
                AppUtils.getString(R.string.generic_address_picker_conversations_list_title)
            }
            SUGGESTION_TYPE -> {
                AppUtils.getString(R.string.generic_address_picker_suggestions_list_title)
            }
            FAVORITE_TYPE -> {
                AppUtils.getString(R.string.generic_address_picker_favorites_list_title)
            }
            else -> {
                AppUtils.getString(R.string.generic_address_picker_contacts_list_title)
            }
        }
        return binding.root
    }

    override fun getItemViewType(position: Int): Int {
        val model = getItem(position)
        return if (model.localAddress != null) {
            CONVERSATION_TYPE
        } else if (model.friend != null) {
            if (model.starred) {
                FAVORITE_TYPE
            } else {
                CONTACT_TYPE
            }
        } else {
            SUGGESTION_TYPE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            CONVERSATION_TYPE -> {
                val binding: GenericAddressPickerConversationListCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.generic_address_picker_conversation_list_cell,
                    parent,
                    false
                )
                binding.apply {
                    lifecycleOwner = parent.findViewTreeLifecycleOwner()

                    setOnClickListener {
                        onClickedEvent.value = Event(model!!)
                    }
                }
                ConversationViewHolder(binding)
            }
            CONTACT_TYPE, FAVORITE_TYPE -> {
                val binding: GenericAddressPickerContactListCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.generic_address_picker_contact_list_cell,
                    parent,
                    false
                )
                binding.apply {
                    lifecycleOwner = parent.findViewTreeLifecycleOwner()

                    setOnClickListener {
                        onClickedEvent.value = Event(model!!)
                    }
                }
                ContactViewHolder(binding)
            }
            else -> {
                val binding: GenericAddressPickerSuggestionListCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.generic_address_picker_suggestion_list_cell,
                    parent,
                    false
                )
                binding.apply {
                    lifecycleOwner = parent.findViewTreeLifecycleOwner()

                    setOnClickListener {
                        onClickedEvent.value = Event(model!!)
                    }
                }
                SuggestionViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            CONVERSATION_TYPE -> (holder as ConversationViewHolder).bind(getItem(position))
            CONTACT_TYPE, FAVORITE_TYPE -> (holder as ContactViewHolder).bind(getItem(position))
            else -> (holder as SuggestionViewHolder).bind(getItem(position))
        }
    }

    inner class ConversationViewHolder(
        val binding: GenericAddressPickerConversationListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationContactOrSuggestionModel: ConversationContactOrSuggestionModel) {
            with(binding) {
                model = conversationContactOrSuggestionModel

                executePendingBindings()
            }
        }
    }

    inner class ContactViewHolder(
        val binding: GenericAddressPickerContactListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationContactOrSuggestionModel: ConversationContactOrSuggestionModel) {
            with(binding) {
                model = conversationContactOrSuggestionModel

                val previousItem = bindingAdapterPosition - 1
                val previousLetter = if (previousItem >= 0) {
                    getItem(previousItem).name[0].toString()
                } else {
                    ""
                }

                val currentLetter = conversationContactOrSuggestionModel.name[0].toString()
                val displayLetter = previousLetter.isEmpty() || currentLetter != previousLetter
                firstContactStartingByThatLetter = displayLetter

                executePendingBindings()
            }
        }
    }

    inner class SuggestionViewHolder(
        val binding: GenericAddressPickerSuggestionListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationContactOrSuggestionModel: ConversationContactOrSuggestionModel) {
            with(binding) {
                model = conversationContactOrSuggestionModel

                executePendingBindings()
            }
        }
    }

    private class ConversationContactOrSuggestionDiffCallback : DiffUtil.ItemCallback<ConversationContactOrSuggestionModel>() {
        override fun areItemsTheSame(
            oldItem: ConversationContactOrSuggestionModel,
            newItem: ConversationContactOrSuggestionModel
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: ConversationContactOrSuggestionModel,
            newItem: ConversationContactOrSuggestionModel
        ): Boolean {
            return false
        }
    }
}

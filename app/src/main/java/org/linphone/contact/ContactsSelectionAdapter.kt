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
package org.linphone.contact

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.FriendCapability
import org.linphone.core.SearchResult
import org.linphone.databinding.ContactSelectionCellBinding
import org.linphone.utils.Event

class ContactsSelectionAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<SearchResult, RecyclerView.ViewHolder>(SearchResultDiffCallback()) {
    val selectedContact = MutableLiveData<Event<SearchResult>>()

    private var selectedAddresses = ArrayList<Address>()

    private var requireGroupChatCapability: Boolean = false
    private var requireLimeCapability: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelectedAddresses(selection: ArrayList<Address>) {
        selectedAddresses = selection
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setLimeCapabilityRequired(enabled: Boolean) {
        requireLimeCapability = enabled
        notifyDataSetChanged()
    }

    fun setGroupChatCapabilityRequired(enabled: Boolean) {
        requireGroupChatCapability = enabled
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ContactSelectionCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.contact_selection_cell, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ContactSelectionCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(searchResult: SearchResult) {
            with(binding) {
                val searchResultViewModel = ContactSelectionData(searchResult)
                data = searchResultViewModel

                lifecycleOwner = viewLifecycleOwner

                updateSecurity(searchResult, searchResultViewModel, requireLimeCapability)

                val selected = selectedAddresses.find { address ->
                    val searchAddress = searchResult.address
                    if (searchAddress != null) address.weakEqual(searchAddress) else false
                }
                searchResultViewModel.isSelected.value = selected != null

                setClickListener {
                    selectedContact.value = Event(searchResult)
                }

                executePendingBindings()
            }
        }

        private fun updateSecurity(
            searchResult: SearchResult,
            viewModel: ContactSelectionData,
            securityEnabled: Boolean
        ) {
            val searchAddress = searchResult.address
            val isMyself = securityEnabled && searchAddress != null && coreContext.core.defaultAccount?.params?.identityAddress?.weakEqual(searchAddress) ?: false
            val limeCheck = !securityEnabled || (securityEnabled && searchResult.hasCapability(FriendCapability.LimeX3Dh))
            val groupCheck = !requireGroupChatCapability || (requireGroupChatCapability && searchResult.hasCapability(FriendCapability.GroupChat))
            val disabled = if (searchResult.friend != null) !limeCheck || !groupCheck || isMyself else false // Generated entry from search filter

            viewModel.isDisabled.value = disabled

            if (disabled && viewModel.isSelected.value == true) {
                // Remove item from selection if both selected and disabled
                selectedContact.postValue(Event(searchResult))
            }
        }
    }
}

private class SearchResultDiffCallback : DiffUtil.ItemCallback<SearchResult>() {
    override fun areItemsTheSame(
        oldItem: SearchResult,
        newItem: SearchResult
    ): Boolean {
        val oldAddress = oldItem.address
        val newAddress = newItem.address
        return if (oldAddress != null && newAddress != null) oldAddress.weakEqual(newAddress) else false
    }

    override fun areContentsTheSame(
        oldItem: SearchResult,
        newItem: SearchResult
    ): Boolean {
        return newItem.friend != null
    }
}

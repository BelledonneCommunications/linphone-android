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
package org.linphone.activities.main.chat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.chat.viewmodels.ChatRoomCreationContactViewModel
import org.linphone.core.Address
import org.linphone.core.FriendCapability
import org.linphone.core.SearchResult
import org.linphone.databinding.ChatRoomCreationContactCellBinding
import org.linphone.utils.Event
import org.linphone.utils.LifecycleListAdapter
import org.linphone.utils.LifecycleViewHolder

class ChatRoomCreationContactsAdapter : LifecycleListAdapter<SearchResult, ChatRoomCreationContactsAdapter.ViewHolder>(SearchResultDiffCallback()) {
    val selectedContact = MutableLiveData<Event<SearchResult>>()

    val selectedAddresses = MutableLiveData<ArrayList<Address>>()

    var groupChatEnabled: Boolean = false

    val securityEnabled = MutableLiveData<Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: ChatRoomCreationContactCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_room_creation_contact_cell, parent, false
        )
        val viewHolder = ViewHolder(binding)
        binding.lifecycleOwner = viewHolder
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ChatRoomCreationContactCellBinding
    ) : LifecycleViewHolder(binding) {
        fun bind(searchResult: SearchResult) {
            with(binding) {
                val searchResultViewModel = ChatRoomCreationContactViewModel(searchResult)
                viewModel = searchResultViewModel

                securityEnabled.observe(this@ViewHolder, Observer {
                    updateSecurity(searchResult, searchResultViewModel, it)
                })

                selectedAddresses.observe(this@ViewHolder, Observer {
                    val selected = it.find { address ->
                        val searchAddress = searchResult.address
                        if (searchAddress != null) address.weakEqual(searchAddress) else false
                    }
                    searchResultViewModel.isSelected.value = selected != null
                })

                setClickListener {
                    selectedContact.value = Event(searchResult)
                }

                executePendingBindings()
            }
        }

        private fun updateSecurity(
            searchResult: SearchResult,
            viewModel: ChatRoomCreationContactViewModel,
            securityEnabled: Boolean
        ) {
            val searchAddress = searchResult.address
            val isMyself = securityEnabled && searchAddress != null && coreContext.core.defaultProxyConfig?.identityAddress?.weakEqual(searchAddress) ?: false
            val limeCheck = !securityEnabled || (securityEnabled && searchResult.hasCapability(FriendCapability.LimeX3Dh))
            val groupCheck = !groupChatEnabled || (groupChatEnabled && searchResult.hasCapability(FriendCapability.GroupChat))
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

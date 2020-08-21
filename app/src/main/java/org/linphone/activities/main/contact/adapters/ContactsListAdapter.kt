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
package org.linphone.activities.main.contact.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import org.linphone.R
import org.linphone.activities.main.contact.viewmodels.ContactViewModel
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.contact.Contact
import org.linphone.databinding.ContactListCellBinding
import org.linphone.databinding.GenericListHeaderBinding
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter
import org.linphone.utils.LifecycleListAdapter
import org.linphone.utils.LifecycleViewHolder

class ContactsListAdapter(val selectionViewModel: ListTopBarViewModel) : LifecycleListAdapter<Contact, ContactsListAdapter.ViewHolder>(ContactDiffCallback()), HeaderAdapter {
    val selectedContactEvent: MutableLiveData<Event<Contact>> by lazy {
        MutableLiveData<Event<Contact>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: ContactListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.contact_list_cell, parent, false
        )
        val viewHolder = ViewHolder(binding)
        binding.lifecycleOwner = viewHolder
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ContactListCellBinding
    ) : LifecycleViewHolder(binding) {
        fun bind(contact: Contact) {
            with(binding) {
                val contactViewModel = ContactViewModel(contact)
                viewModel = contactViewModel

                // This is for item selection through ListTopBarFragment
                selectionListViewModel = selectionViewModel
                selectionViewModel.isEditionEnabled.observe(this@ViewHolder, {
                    position = adapterPosition
                })

                binding.setClickListener {
                    if (selectionViewModel.isEditionEnabled.value == true) {
                        selectionViewModel.onToggleSelect(adapterPosition)
                    } else {
                        selectedContactEvent.value = Event(contact)
                    }
                }

                executePendingBindings()
            }
        }
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false
        val contact = getItem(position)
        val firstLetter = contact.fullName?.first().toString()
        val previousPosition = position - 1
        return if (previousPosition >= 0) {
            val previousItemFirstLetter = getItem(previousPosition).fullName?.first().toString()
            previousItemFirstLetter != firstLetter
        } else true
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val contact = getItem(position)
        val firstLetter = contact.fullName?.first().toString()
        val binding: GenericListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.generic_list_header, null, false
        )
        binding.title = firstLetter
        binding.executePendingBindings()
        return binding.root
    }
}

private class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
    override fun areItemsTheSame(
        oldItem: Contact,
        newItem: Contact
    ): Boolean {
        return oldItem.compareTo(newItem) == 0
    }

    override fun areContentsTheSame(
        oldItem: Contact,
        newItem: Contact
    ): Boolean {
        return false // For headers
    }
}

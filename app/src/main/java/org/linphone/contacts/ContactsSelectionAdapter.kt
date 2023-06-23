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
package org.linphone.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ContactSelectionCellBinding

class ContactsSelectionAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ContactData, RecyclerView.ViewHolder>(ContactDataDiffCallback()) {
    init {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ContactSelectionCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.contact_selection_cell,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ContactSelectionCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contactData: ContactData) {
            with(binding) {
                data = contactData

                lifecycleOwner = viewLifecycleOwner

                executePendingBindings()
            }
        }
    }
}

private class ContactDataDiffCallback : DiffUtil.ItemCallback<ContactData>() {
    override fun areItemsTheSame(
        oldItem: ContactData,
        newItem: ContactData
    ): Boolean {
        return oldItem.friend.refKey == newItem.friend.refKey
    }

    override fun areContentsTheSame(
        oldItem: ContactData,
        newItem: ContactData
    ): Boolean {
        return true
    }
}

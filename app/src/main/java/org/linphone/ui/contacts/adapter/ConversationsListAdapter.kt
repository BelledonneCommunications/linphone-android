package org.linphone.ui.contacts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ContactListCellBinding
import org.linphone.ui.contacts.model.ContactModel

class ContactsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ContactModel, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    var selectedAdapterPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ContactListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.contact_list_cell,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: ContactListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contactModel: ContactModel) {
            with(binding) {
                model = contactModel

                lifecycleOwner = viewLifecycleOwner

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }
}

private class ContactDiffCallback : DiffUtil.ItemCallback<ContactModel>() {
    override fun areItemsTheSame(oldItem: ContactModel, newItem: ContactModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ContactModel, newItem: ContactModel): Boolean {
        return true
    }
}

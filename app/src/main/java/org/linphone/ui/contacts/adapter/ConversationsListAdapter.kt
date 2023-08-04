package org.linphone.ui.contacts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ContactListCellBinding
import org.linphone.ui.contacts.model.ContactModel
import org.linphone.utils.Event

class ContactsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ContactModel, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    var selectedAdapterPosition = -1

    val contactClickedEvent: MutableLiveData<Event<ContactModel>> by lazy {
        MutableLiveData<Event<ContactModel>>()
    }

    val contactLongClickedEvent: MutableLiveData<Event<ContactModel>> by lazy {
        MutableLiveData<Event<ContactModel>>()
    }

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

    fun showHeaderForPosition(position: Int): Boolean {
        if (position >= itemCount) return false

        val contact = getItem(position)
        val firstLetter = contact.name.value?.get(0).toString()
        val previousPosition = position - 1

        return if (previousPosition >= 0) {
            val previousItemFirstLetter = getItem(previousPosition).name.value?.get(0).toString()
            !firstLetter.equals(previousItemFirstLetter, ignoreCase = true)
        } else {
            true
        }
    }

    inner class ViewHolder(
        val binding: ContactListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contactModel: ContactModel) {
            with(binding) {
                model = contactModel
                firstLetter = contactModel.name.value?.get(0).toString()
                showFirstLetter = showHeaderForPosition(bindingAdapterPosition)

                lifecycleOwner = viewLifecycleOwner

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                binding.setOnClickListener {
                    contactClickedEvent.value = Event(contactModel)
                }

                binding.setOnLongClickListener {
                    selectedAdapterPosition = bindingAdapterPosition
                    binding.root.isSelected = true
                    contactLongClickedEvent.value = Event(contactModel)
                    true
                }

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

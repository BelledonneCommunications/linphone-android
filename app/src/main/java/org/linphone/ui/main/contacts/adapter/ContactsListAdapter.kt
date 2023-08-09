package org.linphone.ui.main.contacts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ContactFavouriteListCellBinding
import org.linphone.databinding.ContactListCellBinding
import org.linphone.ui.main.contacts.model.ContactModel
import org.linphone.utils.Event

class ContactsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner,
    private val favourites: Boolean
) : ListAdapter<ContactModel, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    var selectedAdapterPosition = -1

    val contactClickedEvent: MutableLiveData<Event<ContactModel>> by lazy {
        MutableLiveData<Event<ContactModel>>()
    }

    val contactLongClickedEvent: MutableLiveData<Event<ContactModel>> by lazy {
        MutableLiveData<Event<ContactModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (favourites) {
            val binding: ContactFavouriteListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_favourite_list_cell,
                parent,
                false
            )
            return FavouriteViewHolder(binding)
        } else {
            val binding: ContactListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_list_cell,
                parent,
                false
            )
            return ViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (favourites) {
            (holder as FavouriteViewHolder).bind(getItem(position))
        } else {
            (holder as ViewHolder).bind(getItem(position))
        }
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

    inner class FavouriteViewHolder(
        val binding: ContactFavouriteListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contactModel: ContactModel) {
            with(binding) {
                model = contactModel

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
        return oldItem.showFirstLetter.value == newItem.showFirstLetter.value
    }
}

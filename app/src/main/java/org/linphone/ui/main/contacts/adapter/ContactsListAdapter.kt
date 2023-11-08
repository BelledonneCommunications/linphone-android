package org.linphone.ui.main.contacts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.core.ConsolidatedPresence
import org.linphone.databinding.ContactFavouriteListCellBinding
import org.linphone.databinding.ContactListCellBinding
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event

class ContactsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner,
    private val favourites: Boolean = false,
    private val disableLongClick: Boolean = false
) : ListAdapter<ContactAvatarModel, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    var selectedAdapterPosition = -1

    val contactClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData<Event<ContactAvatarModel>>()
    }

    val contactLongClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData<Event<ContactAvatarModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (favourites) {
            val binding: ContactFavouriteListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_favourite_list_cell,
                parent,
                false
            )
            val viewHolder = FavouriteViewHolder(binding)
            binding.apply {
                lifecycleOwner = viewLifecycleOwner

                setOnClickListener {
                    contactClickedEvent.value = Event(model!!)
                }

                setOnLongClickListener {
                    selectedAdapterPosition = viewHolder.bindingAdapterPosition
                    root.isSelected = true
                    contactLongClickedEvent.value = Event(model!!)
                    true
                }
            }
            return viewHolder
        } else {
            val binding: ContactListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_list_cell,
                parent,
                false
            )
            val viewHolder = ViewHolder(binding)
            binding.apply {
                lifecycleOwner = viewLifecycleOwner

                setOnClickListener {
                    contactClickedEvent.value = Event(model!!)
                }

                if (!disableLongClick) {
                    setOnLongClickListener {
                        selectedAdapterPosition = viewHolder.bindingAdapterPosition
                        root.isSelected = true
                        contactLongClickedEvent.value = Event(model!!)
                        true
                    }
                }
            }
            return viewHolder
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
        @UiThread
        fun bind(contactModel: ContactAvatarModel) {
            with(binding) {
                model = contactModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    inner class FavouriteViewHolder(
        val binding: ContactFavouriteListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(contactModel: ContactAvatarModel) {
            with(binding) {
                model = contactModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<ContactAvatarModel>() {
        override fun areItemsTheSame(oldItem: ContactAvatarModel, newItem: ContactAvatarModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ContactAvatarModel, newItem: ContactAvatarModel): Boolean {
            return oldItem.firstContactStartingByThatLetter.value == newItem.firstContactStartingByThatLetter.value &&
                oldItem.presenceStatus.value == newItem.presenceStatus.value &&
                (newItem.presenceStatus.value == ConsolidatedPresence.Busy || newItem.presenceStatus.value == ConsolidatedPresence.Online)
        }
    }
}

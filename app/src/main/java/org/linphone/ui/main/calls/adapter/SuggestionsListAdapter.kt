package org.linphone.ui.main.calls.adapter

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
import org.linphone.databinding.ContactListCellBinding
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event

class SuggestionsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ContactAvatarModel, RecyclerView.ViewHolder>(SuggestionDiffCallback()) {
    val contactClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData<Event<ContactAvatarModel>>()
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

    inner class ViewHolder(
        val binding: ContactListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(contactModel: ContactAvatarModel) {
            with(binding) {
                model = contactModel

                lifecycleOwner = viewLifecycleOwner

                binding.setOnClickListener {
                    contactClickedEvent.value = Event(contactModel)
                }

                executePendingBindings()
            }
        }
    }
}

private class SuggestionDiffCallback : DiffUtil.ItemCallback<ContactAvatarModel>() {
    override fun areItemsTheSame(oldItem: ContactAvatarModel, newItem: ContactAvatarModel): Boolean {
        return oldItem.friend == newItem.friend
    }

    override fun areContentsTheSame(oldItem: ContactAvatarModel, newItem: ContactAvatarModel): Boolean {
        return false
    }
}

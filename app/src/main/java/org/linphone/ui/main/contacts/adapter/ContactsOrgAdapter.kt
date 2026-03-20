package org.linphone.ui.main.contacts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hansol.siphone.R
import com.hansol.siphone.databinding.ContactListCellBinding
import com.hansol.siphone.databinding.OrgCategoryListCellBinding
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.OrgCategoryModel
import org.linphone.ui.main.contacts.model.OrgListItem
import org.linphone.utils.Event

class ContactsOrgAdapter : ListAdapter<OrgListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_CONTACT = 1
    }

    var selectedAdapterPosition = -1

    val categoryClickedEvent: MutableLiveData<Event<OrgCategoryModel>> by lazy {
        MutableLiveData<Event<OrgCategoryModel>>()
    }

    val contactClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData<Event<ContactAvatarModel>>()
    }

    val contactLongClickedEvent: MutableLiveData<Event<ContactAvatarModel>> by lazy {
        MutableLiveData<Event<ContactAvatarModel>>()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is OrgListItem.Category -> VIEW_TYPE_CATEGORY
            is OrgListItem.Contact -> VIEW_TYPE_CONTACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_CATEGORY) {
            val binding: OrgCategoryListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.org_category_list_cell,
                parent,
                false
            )
            val holder = CategoryViewHolder(binding)
            binding.apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()
                setOnClickListener {
                    categoryClickedEvent.value = Event(model!!)
                }
            }
            holder
        } else {
            val binding: ContactListCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_list_cell,
                parent,
                false
            )
            val holder = ContactViewHolder(binding)
            binding.apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()
                setOnClickListener {
                    contactClickedEvent.value = Event(model!!)
                }
                setOnLongClickListener {
                    selectedAdapterPosition = holder.bindingAdapterPosition
                    root.isSelected = true
                    contactLongClickedEvent.value = Event(model!!)
                    true
                }
            }
            holder
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is OrgListItem.Category -> (holder as CategoryViewHolder).bind(item.model)
            is OrgListItem.Contact -> (holder as ContactViewHolder).bind(item.model)
        }
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class CategoryViewHolder(val binding: OrgCategoryListCellBinding) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(model: OrgCategoryModel) {
            binding.model = model
            binding.executePendingBindings()
        }
    }

    inner class ContactViewHolder(val binding: ContactListCellBinding) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(model: ContactAvatarModel) {
            binding.model = model
            binding.firstContactStartingByThatLetter = false
            binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition
            binding.chevron.visibility = android.view.View.GONE
            binding.separator.visibility = android.view.View.GONE
            binding.executePendingBindings()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<OrgListItem>() {
        override fun areItemsTheSame(oldItem: OrgListItem, newItem: OrgListItem): Boolean {
            return when {
                oldItem is OrgListItem.Category && newItem is OrgListItem.Category ->
                    oldItem.model.path == newItem.model.path
                oldItem is OrgListItem.Contact && newItem is OrgListItem.Contact ->
                    oldItem.model.id == newItem.model.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: OrgListItem, newItem: OrgListItem): Boolean {
            return when {
                oldItem is OrgListItem.Category && newItem is OrgListItem.Category ->
                    oldItem.model == newItem.model
                oldItem is OrgListItem.Contact && newItem is OrgListItem.Contact ->
                    newItem.model.compare(oldItem.model)
                else -> false
            }
        }
    }
}

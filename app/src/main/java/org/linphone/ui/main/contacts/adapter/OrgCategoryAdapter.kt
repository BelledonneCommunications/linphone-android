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
import com.hansol.siphone.databinding.OrgCategoryListCellBinding
import org.linphone.ui.main.contacts.model.OrgCategoryModel
import org.linphone.utils.Event

class OrgCategoryAdapter : ListAdapter<OrgCategoryModel, OrgCategoryAdapter.ViewHolder>(DiffCallback()) {

    val itemClickedEvent: MutableLiveData<Event<OrgCategoryModel>> by lazy {
        MutableLiveData<Event<OrgCategoryModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: OrgCategoryListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.org_category_list_cell,
            parent,
            false
        )
        val holder = ViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
            setOnClickListener {
                itemClickedEvent.value = Event(model!!)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(val binding: OrgCategoryListCellBinding) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(model: OrgCategoryModel) {
            binding.model = model
            binding.executePendingBindings()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<OrgCategoryModel>() {
        override fun areItemsTheSame(oldItem: OrgCategoryModel, newItem: OrgCategoryModel): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: OrgCategoryModel, newItem: OrgCategoryModel): Boolean {
            return oldItem == newItem
        }
    }
}

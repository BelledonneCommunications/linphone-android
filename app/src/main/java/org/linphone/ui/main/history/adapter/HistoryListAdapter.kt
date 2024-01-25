package org.linphone.ui.main.history.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.HistoryListCellBinding
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.utils.Event

class HistoryListAdapter : ListAdapter<CallLogModel, RecyclerView.ViewHolder>(CallLogDiffCallback()) {
    var selectedAdapterPosition = -1

    val callLogClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData<Event<CallLogModel>>()
    }

    val callLogLongClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData<Event<CallLogModel>>()
    }

    val callLogCallBackClickedEvent: MutableLiveData<Event<CallLogModel>> by lazy {
        MutableLiveData<Event<CallLogModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: HistoryListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.history_list_cell,
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                callLogClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                callLogLongClickedEvent.value = Event(model!!)
                true
            }

            setOnCallClickListener {
                callLogCallBackClickedEvent.value = Event(model!!)
            }
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: HistoryListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(callLogModel: CallLogModel) {
            with(binding) {
                model = callLogModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    private class CallLogDiffCallback : DiffUtil.ItemCallback<CallLogModel>() {
        override fun areItemsTheSame(oldItem: CallLogModel, newItem: CallLogModel): Boolean {
            return oldItem.id == newItem.id && oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: CallLogModel, newItem: CallLogModel): Boolean {
            return oldItem.avatarModel.id == newItem.avatarModel.id && oldItem.iconResId == newItem.iconResId
        }
    }
}

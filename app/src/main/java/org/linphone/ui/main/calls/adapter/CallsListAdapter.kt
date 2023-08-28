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
import org.linphone.databinding.CallListCellBinding
import org.linphone.ui.main.calls.model.CallLogModel
import org.linphone.utils.Event

class CallsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<CallLogModel, RecyclerView.ViewHolder>(CallLogDiffCallback()) {
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
        val binding: CallListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.call_list_cell,
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

    fun deleteSelection() {
        notifyItemRemoved(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: CallListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(callLogModel: CallLogModel) {
            with(binding) {
                model = callLogModel

                lifecycleOwner = viewLifecycleOwner

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                binding.setOnClickListener {
                    callLogClickedEvent.value = Event(callLogModel)
                }

                binding.setOnLongClickListener {
                    selectedAdapterPosition = bindingAdapterPosition
                    binding.root.isSelected = true
                    callLogLongClickedEvent.value = Event(callLogModel)
                    true
                }

                binding.setOnCallClickListener {
                    callLogCallBackClickedEvent.value = Event(callLogModel)
                }

                executePendingBindings()
            }
        }
    }

    private class CallLogDiffCallback : DiffUtil.ItemCallback<CallLogModel>() {
        override fun areItemsTheSame(oldItem: CallLogModel, newItem: CallLogModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallLogModel, newItem: CallLogModel): Boolean {
            return oldItem.avatarModel.id == newItem.avatarModel.id
        }
    }
}

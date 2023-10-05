package org.linphone.ui.main.chat.adapter

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
import org.linphone.databinding.ChatListCellBinding
import org.linphone.ui.main.chat.model.ConversationModel
import org.linphone.utils.Event

class ConversationsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ConversationModel, RecyclerView.ViewHolder>(ChatRoomDiffCallback()) {
    var selectedAdapterPosition = -1

    val conversationClickedEvent: MutableLiveData<Event<ConversationModel>> by lazy {
        MutableLiveData<Event<ConversationModel>>()
    }

    val conversationLongClickedEvent: MutableLiveData<Event<ConversationModel>> by lazy {
        MutableLiveData<Event<ConversationModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_list_cell,
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
        val binding: ChatListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationModel: ConversationModel) {
            with(binding) {
                model = conversationModel

                lifecycleOwner = viewLifecycleOwner

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                binding.setOnClickListener {
                    conversationClickedEvent.value = Event(conversationModel)
                }

                binding.setOnLongClickListener {
                    selectedAdapterPosition = bindingAdapterPosition
                    binding.root.isSelected = true
                    conversationLongClickedEvent.value = Event(conversationModel)
                    true
                }

                executePendingBindings()
            }
        }
    }

    private class ChatRoomDiffCallback : DiffUtil.ItemCallback<ConversationModel>() {
        override fun areItemsTheSame(oldItem: ConversationModel, newItem: ConversationModel): Boolean {
            return oldItem.id == newItem.id && oldItem.lastUpdateTime == newItem.lastUpdateTime
        }

        override fun areContentsTheSame(oldItem: ConversationModel, newItem: ConversationModel): Boolean {
            return oldItem.avatarModel.id == newItem.avatarModel.id
        }
    }
}

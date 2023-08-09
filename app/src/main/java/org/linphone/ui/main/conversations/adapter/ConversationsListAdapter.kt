package org.linphone.ui.main.conversations.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ChatRoomListCellBinding
import org.linphone.ui.main.conversations.data.ChatRoomData
import org.linphone.ui.main.conversations.data.ChatRoomDataListener
import org.linphone.utils.Event

class ConversationsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ChatRoomData, RecyclerView.ViewHolder>(ConversationDiffCallback()) {
    val chatRoomClickedEvent: MutableLiveData<Event<ChatRoomData>> by lazy {
        MutableLiveData<Event<ChatRoomData>>()
    }

    val chatRoomLongClickedEvent: MutableLiveData<Event<ChatRoomData>> by lazy {
        MutableLiveData<Event<ChatRoomData>>()
    }

    var selectedAdapterPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatRoomListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_room_list_cell,
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
        val binding: ChatRoomListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatRoomData: ChatRoomData) {
            with(binding) {
                data = chatRoomData

                lifecycleOwner = viewLifecycleOwner

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                chatRoomData.chatRoomDataListener = object : ChatRoomDataListener() {
                    override fun onClicked() {
                        chatRoomClickedEvent.value = Event(chatRoomData)
                    }

                    override fun onLongClicked() {
                        selectedAdapterPosition = bindingAdapterPosition
                        binding.root.isSelected = true
                        chatRoomLongClickedEvent.value = Event(chatRoomData)
                    }
                }

                executePendingBindings()
            }
        }
    }
}

private class ConversationDiffCallback : DiffUtil.ItemCallback<ChatRoomData>() {
    override fun areItemsTheSame(oldItem: ChatRoomData, newItem: ChatRoomData): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatRoomData, newItem: ChatRoomData): Boolean {
        return false
    }
}

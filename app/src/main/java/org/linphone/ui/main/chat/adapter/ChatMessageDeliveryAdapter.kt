package org.linphone.ui.main.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ChatMessageDeliveryListCellBinding
import org.linphone.ui.main.chat.model.ChatMessageParticipantDeliveryModel

class ChatMessageDeliveryAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<ChatMessageParticipantDeliveryModel, RecyclerView.ViewHolder>(
    ChatDeliveryDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatMessageDeliveryListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_message_delivery_list_cell,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: ChatMessageDeliveryListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(deliveryModel: ChatMessageParticipantDeliveryModel) {
            with(binding) {
                model = deliveryModel

                lifecycleOwner = viewLifecycleOwner

                executePendingBindings()
            }
        }
    }

    private class ChatDeliveryDiffCallback : DiffUtil.ItemCallback<ChatMessageParticipantDeliveryModel>() {
        override fun areItemsTheSame(
            oldItem: ChatMessageParticipantDeliveryModel,
            newItem: ChatMessageParticipantDeliveryModel
        ): Boolean {
            return oldItem.sipUri == newItem.sipUri
        }

        override fun areContentsTheSame(
            oldItem: ChatMessageParticipantDeliveryModel,
            newItem: ChatMessageParticipantDeliveryModel
        ): Boolean {
            return oldItem.time == newItem.time
        }
    }
}

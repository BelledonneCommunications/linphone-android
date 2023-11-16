package org.linphone.ui.main.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ChatParticipantListCellBinding
import org.linphone.ui.main.chat.model.ParticipantModel

class ConversationParticipantsAdapter : ListAdapter<ParticipantModel, RecyclerView.ViewHolder>(
    ChatRoomParticipantDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatParticipantListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_participant_list_cell,
            parent,
            false
        )
        binding.lifecycleOwner = parent.findViewTreeLifecycleOwner()
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: ChatParticipantListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(participantModel: ParticipantModel) {
            with(binding) {
                model = participantModel
                executePendingBindings()
            }
        }
    }

    private class ChatRoomParticipantDiffCallback : DiffUtil.ItemCallback<ParticipantModel>() {
        override fun areItemsTheSame(oldItem: ParticipantModel, newItem: ParticipantModel): Boolean {
            return oldItem.sipUri == newItem.sipUri
        }

        override fun areContentsTheSame(oldItem: ParticipantModel, newItem: ParticipantModel): Boolean {
            return oldItem.avatarModel.id == newItem.avatarModel.id
        }
    }
}

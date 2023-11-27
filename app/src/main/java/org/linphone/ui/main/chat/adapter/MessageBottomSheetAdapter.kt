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
import org.linphone.databinding.ChatMessageBottomSheetListCellBinding
import org.linphone.ui.main.chat.model.MessageBottomSheetParticipantModel

class MessageBottomSheetAdapter : ListAdapter<MessageBottomSheetParticipantModel, RecyclerView.ViewHolder>(
    ParticipantDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatMessageBottomSheetListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_message_bottom_sheet_list_cell,
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
        val binding: ChatMessageBottomSheetListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(bottomSheetModel: MessageBottomSheetParticipantModel) {
            with(binding) {
                model = bottomSheetModel
                executePendingBindings()
            }
        }
    }

    private class ParticipantDiffCallback : DiffUtil.ItemCallback<MessageBottomSheetParticipantModel>() {
        override fun areItemsTheSame(
            oldItem: MessageBottomSheetParticipantModel,
            newItem: MessageBottomSheetParticipantModel
        ): Boolean {
            return oldItem.sipUri == newItem.sipUri && oldItem.value == newItem.value
        }

        override fun areContentsTheSame(
            oldItem: MessageBottomSheetParticipantModel,
            newItem: MessageBottomSheetParticipantModel
        ): Boolean {
            return true
        }
    }
}

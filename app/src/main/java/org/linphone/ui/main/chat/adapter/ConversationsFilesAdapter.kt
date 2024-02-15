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
import org.linphone.databinding.ChatDocumentContentListCellBinding
import org.linphone.databinding.ChatMediaContentGridCellBinding
import org.linphone.ui.main.chat.model.FileModel

class ConversationsFilesAdapter : ListAdapter<FileModel, RecyclerView.ViewHolder>(
    FilesDiffCallback()
) {
    companion object {
        const val MEDIA_FILE = 1
        const val DOCUMENT_FILE = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            MEDIA_FILE -> createMediaFileViewHolder(parent)
            else -> createDocumentFileViewHolder(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)
        if (data.isMedia) return MEDIA_FILE
        return DOCUMENT_FILE
    }

    private fun createMediaFileViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding: ChatMediaContentGridCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_media_content_grid_cell,
            parent,
            false
        )
        val viewHolder = MediaFileViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
        }
        return viewHolder
    }

    private fun createDocumentFileViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding: ChatDocumentContentListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_document_content_list_cell,
            parent,
            false
        )
        val viewHolder = DocumentFileViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val fileModel = getItem(position)
        when (holder) {
            is MediaFileViewHolder -> holder.bind(fileModel)
            is DocumentFileViewHolder -> holder.bind(fileModel)
        }
    }

    inner class MediaFileViewHolder(
        val binding: ChatMediaContentGridCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(fileModel: FileModel) {
            with(binding) {
                model = fileModel
                executePendingBindings()
            }
        }
    }

    inner class DocumentFileViewHolder(
        val binding: ChatDocumentContentListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(fileModel: FileModel) {
            with(binding) {
                model = fileModel
                executePendingBindings()
            }
        }
    }

    private class FilesDiffCallback : DiffUtil.ItemCallback<FileModel>() {
        override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem.file == newItem.file && oldItem.fileName == newItem.fileName
        }

        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem.mimeType == newItem.mimeType
        }
    }
}

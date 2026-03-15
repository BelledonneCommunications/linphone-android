/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.chat.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ChatBubbleSingleFileContentBinding
import org.linphone.databinding.ChatMediaContentGridCellBinding
import org.linphone.databinding.MeetingsListDecorationBinding
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.utils.AppUtils
import org.linphone.utils.HeaderAdapter

class ConversationsFilesAdapter :
    ListAdapter<FileModel, RecyclerView.ViewHolder>(
        FilesDiffCallback()
    ),
    HeaderAdapter {
    companion object {
        const val MEDIA_FILE = 1
        const val DOCUMENT_FILE = 2
    }

    private val topBottomPadding = AppUtils.getDimension(R.dimen.chat_documents_list_padding_top_bottom).toInt()
    private val startEndPadding = AppUtils.getDimension(R.dimen.chat_documents_list_padding_start_end).toInt()

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position == 0) return true

        val previous = getItem(position - 1)
        val item = getItem(position)
        return previous.month != item.month
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = MeetingsListDecorationBinding.inflate(LayoutInflater.from(context))
        val item = getItem(position)
        binding.header.text = item.month
        return binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            MEDIA_FILE -> createMediaFileViewHolder(parent)
            else -> createDocumentFileViewHolder(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)
        if (data.isMedia || data.isAudio) return MEDIA_FILE
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
        val binding: ChatBubbleSingleFileContentBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_single_file_content,
            parent,
            false
        )
        val viewHolder = DocumentFileViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
            root.setPadding(startEndPadding, topBottomPadding, startEndPadding, topBottomPadding)
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

    class MediaFileViewHolder(
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

    class DocumentFileViewHolder(
        val binding: ChatBubbleSingleFileContentBinding
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
            return oldItem.path == newItem.path && oldItem.fileName == newItem.fileName
        }

        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem.mimeType == newItem.mimeType
        }
    }
}

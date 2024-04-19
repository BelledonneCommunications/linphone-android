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

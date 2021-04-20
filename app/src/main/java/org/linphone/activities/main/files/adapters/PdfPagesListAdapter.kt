/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.files.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.files.viewmodels.PdfFileViewModel
import org.linphone.databinding.FilePdfViewerCellBinding

class PdfPagesListAdapter(private val pdfViewModel: PdfFileViewModel) : RecyclerView.Adapter<PdfPagesListAdapter.PdfPageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val binding: FilePdfViewerCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.file_pdf_viewer_cell, parent, false
        )
        return PdfPageViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return pdfViewModel.getPagesCount()
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class PdfPageViewHolder(private val binding: FilePdfViewerCellBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(index: Int) {
            with(binding) {
                pdfViewModel.loadPdfPageInto(index, pdfImage)
            }
        }
    }
}

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
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.activities.main.files.viewmodels.PdfFileViewModel

class PdfPagesListAdapter(private val pdfViewModel: PdfFileViewModel) : RecyclerView.Adapter<PdfPagesListAdapter.PdfPageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.file_pdf_viewer_cell, parent, false
        )
        return PdfPageViewHolder(view)
    }

    override fun getItemCount(): Int {
        return pdfViewModel.getPagesCount()
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class PdfPageViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(index: Int) {
            pdfViewModel.loadPdfPageInto(index, view.findViewById(R.id.pdf_image))
        }
    }
}

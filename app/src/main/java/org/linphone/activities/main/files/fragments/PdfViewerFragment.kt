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
package org.linphone.activities.main.files.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.files.adapters.PdfPagesListAdapter
import org.linphone.activities.main.files.viewmodels.PdfFileViewModel
import org.linphone.activities.main.files.viewmodels.PdfFileViewModelFactory
import org.linphone.core.tools.Log
import org.linphone.databinding.FilePdfViewerFragmentBinding

class PdfViewerFragment : GenericViewerFragment<FilePdfViewerFragmentBinding>() {
    private lateinit var viewModel: PdfFileViewModel
    private lateinit var adapter: PdfPagesListAdapter

    override fun getLayoutId(): Int = R.layout.file_pdf_viewer_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        val content = sharedViewModel.contentToOpen.value
        if (content == null) {
            Log.e("[PDF Viewer] Content is null, aborting!")
            findNavController().navigateUp()
            return
        }

        viewModel = ViewModelProvider(
            this,
            PdfFileViewModelFactory(content)
        )[PdfFileViewModel::class.java]
        binding.viewModel = viewModel

        adapter = PdfPagesListAdapter(viewModel)
        binding.pdfViewPager.adapter = adapter
    }
}

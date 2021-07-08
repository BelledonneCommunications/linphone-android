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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.files.viewmodels.ImageFileViewModel
import org.linphone.activities.main.files.viewmodels.ImageFileViewModelFactory
import org.linphone.core.tools.Log
import org.linphone.databinding.FileImageViewerFragmentBinding

class ImageViewerFragment : GenericViewerFragment<FileImageViewerFragmentBinding>() {
    private lateinit var viewModel: ImageFileViewModel

    override fun getLayoutId(): Int = R.layout.file_image_viewer_fragment

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        val content = sharedViewModel.contentToOpen.value
        if (content == null) {
            Log.e("[Image Viewer] Content is null, aborting!")
            // (activity as MainActivity).showSnackBar(R.string.error)
            findNavController().navigateUp()
            return
        }

        viewModel = ViewModelProvider(
            this,
            ImageFileViewModelFactory(content)
        )[ImageFileViewModel::class.java]
        binding.viewModel = viewModel
    }
}

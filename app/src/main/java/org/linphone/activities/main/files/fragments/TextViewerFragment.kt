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
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.files.viewmodels.TextFileViewModel
import org.linphone.activities.main.files.viewmodels.TextFileViewModelFactory
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.databinding.TextViewerFragmentBinding

class TextViewerFragment : GenericFragment<TextViewerFragmentBinding>() {
    private lateinit var viewModel: TextFileViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun getLayoutId(): Int = R.layout.text_viewer_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        val filePath = sharedViewModel.fileToOpen.value
        filePath ?: return

        viewModel = ViewModelProvider(
            this,
            TextFileViewModelFactory(filePath)
        )[TextFileViewModel::class.java]
        binding.viewModel = viewModel
    }
}

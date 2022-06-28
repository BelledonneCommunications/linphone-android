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
package org.linphone.activities.main.dialer.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.dialer.viewmodels.ConfigFileViewModel
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.core.tools.Log
import org.linphone.databinding.FileConfigViewerFragmentBinding

class ConfigViewerFragment : SecureFragment<FileConfigViewerFragmentBinding>() {
    private lateinit var viewModel: ConfigFileViewModel

    override fun getLayoutId(): Int = R.layout.file_config_viewer_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConfigFileViewModel::class.java]
        binding.viewModel = viewModel

        isSecure = arguments?.getBoolean("Secure") ?: false

        binding.setExportClickListener {
            shareConfig()
        }
    }

    private fun shareConfig() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, viewModel.text.value.orEmpty())
        intent.type = "text/plain"

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.app_name)))
        } catch (ex: ActivityNotFoundException) {
            Log.e(ex)
        }
    }
}

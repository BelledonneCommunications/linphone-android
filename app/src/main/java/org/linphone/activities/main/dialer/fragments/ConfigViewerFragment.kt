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

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.main.dialer.viewmodels.ConfigFileViewModel
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.databinding.FileConfigViewerFragmentBinding
import org.linphone.utils.FileUtils

class ConfigViewerFragment : SecureFragment<FileConfigViewerFragmentBinding>() {
    private lateinit var viewModel: ConfigFileViewModel

    override fun getLayoutId(): Int = R.layout.file_config_viewer_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this)[ConfigFileViewModel::class.java]
        binding.viewModel = viewModel

        isSecure = arguments?.getBoolean("Secure") ?: false

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setExportClickListener {
            if (!FileUtils.openFileInThirdPartyApp(requireActivity(), corePreferences.configPath)) {
                (requireActivity() as SnackBarActivity).showSnackBar(R.string.chat_message_no_app_found_to_handle_file_mime_type)
            }
        }
    }
}

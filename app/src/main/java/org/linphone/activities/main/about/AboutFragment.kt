/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.about

import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.databinding.AboutFragmentBinding
import org.linphone.utils.AppUtils

class AboutFragment : GenericFragment<AboutFragmentBinding>() {
    private lateinit var viewModel: AboutViewModel

    override fun getLayoutId(): Int = R.layout.about_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(AboutViewModel::class.java)
        binding.viewModel = viewModel

        binding.setBackClickListener { findNavController().popBackStack() }

        binding.setPrivacyPolicyClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_privacy_policy_link))
            )
            startActivity(browserIntent)
        }

        binding.setLicenseClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_license_link))
            )
            startActivity(browserIntent)
        }

        viewModel.uploadFinishedEvent.observe(viewLifecycleOwner, {
            it.consume { url ->
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Logs url", url)
                clipboard.setPrimaryClip(clip)

                val activity = requireActivity() as MainActivity
                activity.showSnackBar(R.string.logs_url_copied_to_clipboard)

                AppUtils.shareUploadedLogsUrl(activity, url)
            }
        })
    }
}

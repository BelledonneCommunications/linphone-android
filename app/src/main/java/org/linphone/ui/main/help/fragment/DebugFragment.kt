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
package org.linphone.ui.main.help.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.databinding.HelpDebugFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.help.viewmodel.HelpViewModel
import org.linphone.utils.AppUtils

class DebugFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Debug Fragment]"
    }

    private lateinit var binding: HelpDebugFragmentBinding

    val viewModel: HelpViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HelpDebugFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        viewModel.debugLogsCleanedEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.help_advanced_debug_logs_cleaned_toast_message),
                    R.drawable.info
                )
            }
        }

        viewModel.uploadDebugLogsFinishedEvent.observe(viewLifecycleOwner) {
            it.consume { url ->
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Logs url", url)
                clipboard.setPrimaryClip(clip)

                (requireActivity() as MainActivity).showGreenToast(
                    getString(
                        R.string.help_advanced_debug_logs_url_copied_into_clipboard_toast_message
                    ),
                    R.drawable.info
                )

                AppUtils.shareUploadedLogsUrl(requireActivity(), url)
            }
        }

        viewModel.uploadDebugLogsErrorEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).showRedToast(
                    getString(R.string.help_advanced_debug_logs_upload_error_toast_message),
                    R.drawable.warning_circle
                )
            }
        }
    }
}

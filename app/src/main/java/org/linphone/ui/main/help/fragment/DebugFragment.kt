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

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.HelpDebugFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.fileviewer.FileViewerActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.help.viewmodel.HelpViewModel

class DebugFragment : GenericMainFragment() {
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
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.setAppVersionClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val label = getString(R.string.help_troubleshooting_app_version_title)
            clipboard.setPrimaryClip(
                ClipData.newPlainText(label, viewModel.appVersion.value.orEmpty())
            )
        }

        binding.setSdkVersionClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val label = getString(R.string.help_troubleshooting_sdk_version_title)
            clipboard.setPrimaryClip(
                ClipData.newPlainText(label, viewModel.sdkVersion.value.orEmpty())
            )
        }

        viewModel.debugLogsCleanedEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.help_troubleshooting_debug_logs_cleaned_toast_message),
                    R.drawable.info
                )
            }
        }

        viewModel.uploadDebugLogsFinishedEvent.observe(viewLifecycleOwner) {
            it.consume { url ->
                val appName = requireContext().getString(R.string.app_name)
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(
                    Intent.EXTRA_EMAIL,
                    arrayOf(
                        requireContext().getString(
                            R.string.help_advanced_send_debug_logs_email_address
                        )
                    )
                )
                intent.putExtra(Intent.EXTRA_SUBJECT, "$appName Logs")
                intent.putExtra(Intent.EXTRA_TEXT, url)
                intent.type = "text/plain"

                try {
                    requireContext().startActivity(
                        Intent.createChooser(
                            intent,
                            requireContext().getString(
                                R.string.help_troubleshooting_share_logs_dialog_title
                            )
                        )
                    )
                } catch (ex: ActivityNotFoundException) {
                    Log.e(ex)
                }
            }
        }

        viewModel.uploadDebugLogsErrorEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as GenericActivity).showRedToast(
                    getString(R.string.help_troubleshooting_debug_logs_upload_error_toast_message),
                    R.drawable.warning_circle
                )
            }
        }

        viewModel.showConfigFileEvent.observe(viewLifecycleOwner) {
            it.consume { path ->
                if (findNavController().currentDestination?.id == R.id.debugFragment) {
                    val intent = Intent(requireActivity(), FileViewerActivity::class.java)
                    val bundle = Bundle()
                    bundle.putString("path", path)
                    val nowInSeconds = System.currentTimeMillis() / 1000
                    bundle.putLong("timestamp", nowInSeconds)
                    intent.putExtras(bundle)
                    startActivity(intent)
                }
            }
        }
    }
}

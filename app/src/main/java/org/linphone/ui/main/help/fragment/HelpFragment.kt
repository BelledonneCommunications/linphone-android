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
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.HelpFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.help.viewmodel.HelpViewModel
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils
import androidx.core.net.toUri

@UiThread
class HelpFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Help Fragment]"
    }

    private lateinit var binding: HelpFragmentBinding

    val viewModel: HelpViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HelpFragmentBinding.inflate(layoutInflater)
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

        binding.setDebugClickListener {
            if (findNavController().currentDestination?.id == R.id.helpFragment) {
                val action = HelpFragmentDirections.actionHelpFragmentToDebugFragment()
                findNavController().navigate(action)
            }
        }

        binding.setPrivacyPolicyClickListener {
            val url = getString(R.string.website_privacy_policy_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            } catch (anfe: ActivityNotFoundException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
                )
            } catch (e: Exception) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                )
            }
        }

        binding.setLicensesClickListener {
            val url = getString(R.string.website_open_source_licences_usage_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            } catch (anfe: ActivityNotFoundException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
                )
            } catch (e: Exception) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                )
            }
        }

        binding.setTranslateClickListener {
            val url = getString(R.string.website_translate_weblate_url)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                )
            } catch (anfe: ActivityNotFoundException) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
                )
            } catch (e: Exception) {
                Log.e(
                    "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                )
            }
        }

        viewModel.newVersionAvailableEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val version = pair.first
                val url = pair.second
                showUpdateAvailableDialog(version, url)
            }
        }

        viewModel.versionUpToDateEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.help_version_up_to_date_toast_message),
                    R.drawable.info
                )
            }
        }

        viewModel.errorEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as GenericActivity).showRedToast(
                    getString(R.string.help_error_checking_version_toast_message),
                    R.drawable.warning_circle
                )
            }
        }
    }

    private fun showUpdateAvailableDialog(version: String, url: String) {
        val message = getString(R.string.help_dialog_update_available_message, version)

        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getUpdateAvailableDialog(
            requireActivity(),
            model,
            message
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                    )
                } catch (anfe: ActivityNotFoundException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
                    )
                } catch (e: Exception) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                    )
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

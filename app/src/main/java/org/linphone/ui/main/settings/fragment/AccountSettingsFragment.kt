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
package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import java.util.Locale
import org.linphone.R
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountSettingsFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.settings.model.UpdatePasswordDialogModel
import org.linphone.ui.main.settings.viewmodel.AccountSettingsViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class AccountSettingsFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Account Settings Fragment]"
    }

    private lateinit var binding: AccountSettingsFragmentBinding

    private val args: AccountSettingsFragmentArgs by navArgs()

    private lateinit var viewModel: AccountSettingsViewModel

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val transport = viewModel.availableTransports[position]
            val transportType = when (transport) {
                TransportType.Tcp.name.uppercase(Locale.getDefault()) -> TransportType.Tcp
                TransportType.Tls.name.uppercase(Locale.getDefault()) -> TransportType.Tls
                else -> TransportType.Udp
            }
            Log.i("$TAG Selected transport updated [$transport] -> [${transportType.name}]")
            viewModel.selectedTransport.value = transportType
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountSettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[AccountSettingsViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        binding.setBackClickListener {
            goBack()
        }

        binding.setUpdatePasswordClickListener {
            showUpdatePasswordDialog()
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()

                        val adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.drop_down_item,
                            viewModel.availableTransports
                        )
                        adapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
                        val currentTransport = viewModel.selectedTransport.value?.name?.uppercase(
                            Locale.getDefault()
                        )
                        binding.accountAdvancedSettings.transportSpinner.adapter = adapter
                        binding.accountAdvancedSettings.transportSpinner.setSelection(
                            viewModel.availableTransports.indexOf(currentTransport)
                        )
                        binding.accountAdvancedSettings.transportSpinner.onItemSelectedListener = dropdownListener
                    }
                } else {
                    Log.e(
                        "$TAG Failed to find an account matching this identity address [$identity]"
                    )
                    val message = getString(R.string.account_failed_to_find_identity_toast)
                    val icon = R.drawable.warning_circle
                    (requireActivity() as GenericActivity).showRedToast(message, icon)
                    goBack()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.saveChanges()
        // It is possible some value have changed, causing some menu to appear or disappear
        sharedViewModel.forceUpdateAvailableNavigationItems.value = Event(true)
    }

    private fun showUpdatePasswordDialog() {
        val model = UpdatePasswordDialogModel()
        val dialog = DialogUtils.getUpdatePasswordDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume { password ->
                viewModel.updateAccountPassword(password)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountProfileFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.ui.main.settings.viewmodel.AccountProfileViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

@UiThread
class AccountProfileFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Account Profile Fragment]"
    }

    private lateinit var binding: AccountProfileFragmentBinding

    private val viewModel: AccountProfileViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    private val args: AccountProfileFragmentArgs by navArgs()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.i("$TAG Picture picked [$uri]")
            lifecycleScope.launch {
                val localFileName = FileUtils.getFilePath(requireContext(), uri, true)
                if (localFileName != null) {
                    Log.i("$TAG Picture will be locally stored as [$localFileName]")
                    val path = FileUtils.getProperFilePath(localFileName)
                    viewModel.setNewPicturePath(path)
                } else {
                    Log.e("$TAG Failed to copy [$uri] to local storage")
                }
            }
        } else {
            Log.w("$TAG No picture picked")
        }
    }

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (position == 0) {
                Log.i("$TAG Removing selected dial plan")
                viewModel.removeDialPlan()
                viewModel.selectedDialPlan.value = 0
            } else {
                val index = position - 1
                val dialPlan = viewModel.dialPlansList[index]
                Log.i(
                    "$TAG Selected dialplan updated [+${dialPlan.countryCallingCode}] / [${dialPlan.country}]"
                )
                viewModel.setDialPlan(dialPlan)
                viewModel.selectedDialPlan.value = index
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountProfileFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        binding.setBackClickListener {
            goBack()
        }

        binding.setPickImageClickListener {
            pickImage()
        }

        binding.setDeleteImageClickListener {
            viewModel.setNewPicturePath("")
        }

        binding.setCopySipUriClickListener {
            copyAddressToClipboard(viewModel.sipAddress.value.orEmpty())
        }

        binding.setCopyDeviceIdClickListener {
            copyAddressToClipboard(viewModel.deviceId.value.orEmpty())
        }

        binding.setPrefixTooltipClickListener {
            showHelpPopup()
        }

        binding.setChangeModeClickListener {
            goToAccountProfileModeFragment()
        }

        binding.setSettingsClickListener {
            if (findNavController().currentDestination?.id == R.id.accountProfileFragment) {
                val action =
                    AccountProfileFragmentDirections.actionAccountProfileFragmentToAccountSettingsFragment(
                        identity
                    )
                findNavController().navigate(action)
            }
        }

        binding.setDeleteAccountClickListener {
            val model = ConfirmationDialogModel()
            val dialog = DialogUtils.getConfirmAccountRemovalDialog(
                requireActivity(),
                model,
                viewModel.isOnDefaultDomain.value == true
            )

            model.dismissEvent.observe(viewLifecycleOwner) {
                it.consume {
                    dialog.dismiss()
                }
            }

            model.confirmEvent.observe(viewLifecycleOwner) {
                it.consume {
                    viewModel.deleteAccount()
                    dialog.dismiss()
                }
            }

            dialog.show()
        }

        viewModel.accountRemovedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Account has been removed, leaving profile")
                goBack()
            }
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    startPostponedEnterTransition()
                    setupDialPlanPicker()
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

        Log.i("$TAG Leaving account profile, saving changes")
        viewModel.saveChangesWhenLeaving()
        sharedViewModel.refreshDrawerMenuAccountsListEvent.value = Event(true)
    }

    private fun goToAccountProfileModeFragment() {
        if (findNavController().currentDestination?.id == R.id.accountProfileFragment) {
            val action =
                AccountProfileFragmentDirections.actionAccountProfileFragmentToAccountProfileModeFragment()
            findNavController().navigate(action)
        }
    }

    private fun pickImage() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun copyAddressToClipboard(value: String) {
        if (AppUtils.copyToClipboard(requireContext(), "SIP address", value)) {
            val message = getString(R.string.sip_address_copied_to_clipboard_toast)
            (requireActivity() as GenericActivity).showGreenToast(message, R.drawable.check)
        }
    }

    private fun setupDialPlanPicker() {
        val dialPlanIndex = viewModel.selectedDialPlan.value ?: 0
        Log.i("$TAG Setting default dial plan at index [$dialPlanIndex]")
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.dialPlansLabelList
        )
        adapter.setDropDownViewResource(
            R.layout.assistant_country_picker_dropdown_cell
        )
        binding.prefix.adapter = adapter
        binding.prefix.onItemSelectedListener = dropdownListener
        binding.prefix.setSelection(dialPlanIndex)
    }

    @UiThread
    private fun showHelpPopup() {
        val dialog = DialogUtils.getAccountInternationalPrefixHelpDialog(requireActivity())
        dialog.show()
    }
}

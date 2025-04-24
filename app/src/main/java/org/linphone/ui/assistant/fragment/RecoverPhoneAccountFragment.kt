/*
 * Copyright (c) 2010-2025 Belledonne Communications SARL.
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
package org.linphone.ui.assistant.fragment

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.ui.GenericFragment
import org.linphone.databinding.AssistantRecoverPhoneAccountFragmentBinding
import org.linphone.ui.assistant.viewmodel.RecoverPhoneAccountViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.PhoneNumberUtils
import kotlin.getValue

@UiThread
class RecoverPhoneAccountFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Recover Phone Account Fragment]"
    }

    private lateinit var binding: AssistantRecoverPhoneAccountFragmentBinding

    private val viewModel: RecoverPhoneAccountViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val dialPlan = viewModel.dialPlansList[position]
            Log.i(
                "$TAG Selected dialplan updated [+${dialPlan.countryCallingCode}] / [${dialPlan.country}]"
            )
            viewModel.selectedDialPlan.value = dialPlan
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantRecoverPhoneAccountFragmentBinding.inflate(layoutInflater)
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

        binding.phoneNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.phoneNumberError.value = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewModel.normalizedPhoneNumberEvent.observe(viewLifecycleOwner) {
            it.consume { number ->
                Log.i("$TAG Showing confirmation dialog for phone number [$number]")
                showPhoneNumberConfirmationDialog(number)
            }
        }

        viewModel.goToSmsValidationEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.recoverPhoneAccountFragment) {
                    Log.i("$TAG Going to SMS code validation fragment")
                    val action = RecoverPhoneAccountFragmentDirections.actionRecoverPhoneAccountFragmentToRecoverPhoneAccountCodeConfirmationFragment()
                    findNavController().navigate(action)
                }
            }
        }

        val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryIso = telephonyManager.networkCountryIso
        coreContext.postOnCoreThread {
            val fragmentContext = context ?: return@postOnCoreThread

            val adapter = object : ArrayAdapter<String>(
                fragmentContext,
                R.layout.drop_down_item,
                viewModel.dialPlansLabelList
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: super.getView(position, null, parent)
                    val label = viewModel.dialPlansShortLabelList[position]
                    (view as? AppCompatTextView)?.text = label
                    return view
                }
            }
            adapter.setDropDownViewResource(R.layout.assistant_country_picker_dropdown_cell)

            val dialPlan = PhoneNumberUtils.getDeviceDialPlan(countryIso)
            var default = 0
            if (dialPlan != null) {
                viewModel.selectedDialPlan.postValue(dialPlan)
                default = viewModel.dialPlansList.indexOf(dialPlan)
            }

            coreContext.postOnMainThread {
                binding.prefix.adapter = adapter
                binding.prefix.setSelection(default)
                binding.prefix.onItemSelectedListener = dropdownListener
            }
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }

    private fun showPhoneNumberConfirmationDialog(number: String) {
        val label  = AppUtils.getFormattedString(R.string.assistant_dialog_confirm_phone_number_message, number)
        val model = ConfirmationDialogModel(label)
        val dialog = DialogUtils.getAccountCreationPhoneNumberConfirmationDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG User dismissed the dialog, aborting recovery process")
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG User confirmed the phone number, requesting account creation token & SMS code")
                viewModel.startRecoveryProcess()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

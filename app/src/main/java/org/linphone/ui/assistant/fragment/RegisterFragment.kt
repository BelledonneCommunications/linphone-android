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
package org.linphone.ui.assistant.fragment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantRegisterFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.viewmodel.AccountCreationViewModel
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.PhoneNumberUtils
import androidx.core.net.toUri

@UiThread
class RegisterFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Register Fragment]"
    }

    private lateinit var binding: AssistantRegisterFragmentBinding

    private val viewModel: AccountCreationViewModel by navGraphViewModels(
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
        binding = AssistantRegisterFragmentBinding.inflate(layoutInflater)
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

        binding.setLoginClickListener {
            goBack()
        }

        binding.setOpenSubscribeWebPageClickListener {
            val url = getString(R.string.web_platform_register_email_url)
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
            }
        }

        binding.username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.usernameError.value = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.phoneNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.phoneNumberError.value = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewModel.pushNotificationsAvailable.observe(viewLifecycleOwner) { available ->
            if (!available) {
                val text = getString(R.string.assistant_account_register_unavailable_no_push_toast)
                (requireActivity() as GenericActivity).showRedToast(
                    text,
                    R.drawable.warning_circle
                )
            }
        }

        viewModel.normalizedPhoneNumberEvent.observe(viewLifecycleOwner) {
            it.consume { number ->
                showPhoneNumberConfirmationDialog(number)
            }
        }

        viewModel.showPassword.observe(viewLifecycleOwner) {
            lifecycleScope.launch {
                delay(50)
                binding.password.setSelection(binding.password.text?.length ?: 0)
            }
        }

        viewModel.goToSmsCodeConfirmationViewEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Going to SMS code confirmation fragment")
                if (findNavController().currentDestination?.id == R.id.registerFragment) {
                    val action =
                        RegisterFragmentDirections.actionRegisterFragmentToRegisterCodeConfirmationFragment()
                    findNavController().navigate(action)
                }
            }
        }

        viewModel.errorHappenedEvent.observe(viewLifecycleOwner) {
            it.consume { error ->
                (requireActivity() as GenericActivity).showRedToast(
                    error,
                    R.drawable.warning_circle
                )
            }
        }

        val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryIso = telephonyManager.networkCountryIso
        coreContext.postOnCoreThread {
            val adapter = object : ArrayAdapter<String>(
                requireContext(),
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
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.startAccountCreation()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

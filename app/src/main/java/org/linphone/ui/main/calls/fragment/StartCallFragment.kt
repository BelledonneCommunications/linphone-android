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
package org.linphone.ui.main.calls.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.databinding.CallStartFragmentBinding
import org.linphone.ui.main.calls.viewmodel.StartCallViewModel
import org.linphone.ui.main.calls.viewmodel.SuggestionsListViewModel
import org.linphone.ui.main.contacts.adapter.ContactsListAdapter
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.contacts.viewmodel.ContactsListViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.DialogUtils

class StartCallFragment : GenericFragment() {
    private lateinit var binding: CallStartFragmentBinding

    private val viewModel: StartCallViewModel by navGraphViewModels(
        R.id.startCallFragment
    )

    private val contactsListViewModel: ContactsListViewModel by navGraphViewModels(
        R.id.startCallFragment
    )

    private val suggestionsListViewModel: SuggestionsListViewModel by navGraphViewModels(
        R.id.startCallFragment
    )

    private lateinit var contactsAdapter: ContactsListAdapter
    private lateinit var suggestionsAdapter: ContactsListAdapter

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(address: Address?) {
            if (address != null) {
                coreContext.postOnCoreThread {
                    coreContext.startCall(address)
                }
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallStartFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        contactsAdapter = ContactsListAdapter(viewLifecycleOwner, false)
        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.adapter = contactsAdapter

        contactsAdapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                startCall(model)
            }
        }

        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())

        suggestionsAdapter = ContactsListAdapter(viewLifecycleOwner, false)
        binding.suggestionsList.setHasFixedSize(true)
        binding.suggestionsList.adapter = suggestionsAdapter

        suggestionsAdapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                startCall(model)
            }
        }

        binding.suggestionsList.layoutManager = LinearLayoutManager(requireContext())

        contactsListViewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            contactsAdapter.submitList(it)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        suggestionsListViewModel.suggestionsList.observe(viewLifecycleOwner) {
            suggestionsAdapter.submitList(it)
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            contactsListViewModel.applyFilter(filter)
            suggestionsListViewModel.applyFilter(filter)
        }
    }

    private fun startCall(model: ContactAvatarModel) {
        coreContext.postOnCoreThread { core ->
            val friend = model.friend
            val addressesCount = friend.addresses.size
            val numbersCount = friend.phoneNumbers.size
            if (addressesCount == 1 && numbersCount == 0) {
                val address = friend.addresses.first()
                coreContext.startCall(address)
            } else if (addressesCount == 1 && numbersCount == 0) {
                val number = friend.phoneNumbers.first()
                val address = core.interpretUrl(number, true)
                if (address != null) {
                    coreContext.startCall(address)
                }
            } else {
                val list = arrayListOf<ContactNumberOrAddressModel>()
                for (address in friend.addresses) {
                    val addressModel = ContactNumberOrAddressModel(
                        address,
                        address.asStringUriOnly(),
                        listener,
                        true
                    )
                    list.add(addressModel)
                }

                for (number in friend.phoneNumbersWithLabel) {
                    val address = core.interpretUrl(number.phoneNumber, true)
                    val addressModel = ContactNumberOrAddressModel(
                        address,
                        number.phoneNumber,
                        listener,
                        false,
                        number.label.orEmpty()
                    )
                    list.add(addressModel)
                }

                coreContext.postOnMainThread {
                    val model = NumberOrAddressPickerDialogModel(list)
                    val dialog =
                        DialogUtils.getNumberOrAddressPickerDialog(requireActivity(), model)

                    model.dismissEvent.observe(viewLifecycleOwner) { event ->
                        event.consume {
                            dialog.dismiss()
                        }
                    }

                    dialog.show()
                }
            }
        }
    }
}

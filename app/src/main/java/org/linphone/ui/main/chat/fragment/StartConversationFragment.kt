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
package org.linphone.ui.main.chat.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.tools.Log
import org.linphone.databinding.StartChatFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.viewmodel.StartConversationViewModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.history.adapter.ContactsAndSuggestionsListAdapter
import org.linphone.ui.main.history.model.ContactOrSuggestionModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class StartConversationFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Start Conversation Fragment]"
    }

    private lateinit var binding: StartChatFragmentBinding

    private val viewModel: StartConversationViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    private lateinit var adapter: ContactsAndSuggestionsListAdapter

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            coreContext.postOnCoreThread {
                if (address != null) {
                    Log.i(
                        "$TAG Creating a 1-1 conversation with [${model.address.asStringUriOnly()}]"
                    )
                    viewModel.createOneToOneChatRoomWith(model.address)
                }
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
        }
    }

    private var numberOrAddressPickerDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = StartChatFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        adapter = ContactsAndSuggestionsListAdapter(viewLifecycleOwner)
        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.adapter = adapter

        adapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                createChatRoom(model)
            }
        }

        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())

        viewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            val count = adapter.itemCount
            adapter.submitList(it)

            if (count == 0 && it.isNotEmpty()) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }
        }

        viewModel.chatRoomCreatedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                Log.i(
                    "$TAG Chat room [${pair.second}] for local address [${pair.first}] has been created, navigating to it"
                )
                sharedViewModel.showConversationEvent.value = Event(pair)
                goBack()
            }
        }

        viewModel.chatRoomCreationErrorEvent.observe(viewLifecycleOwner) {
            it.consume { error ->
                Log.i("$TAG Chat room creation error, showing red toast")
                (requireActivity() as MainActivity).showRedToast(error, R.drawable.warning_circle)
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            val trimmed = filter.trim()
            viewModel.applyFilter(trimmed)
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            // Do not consume it!
            viewModel.updateGroupChatButtonVisibility()
        }
    }

    override fun onPause() {
        super.onPause()

        numberOrAddressPickerDialog?.dismiss()
        numberOrAddressPickerDialog = null
    }

    private fun createChatRoom(model: ContactOrSuggestionModel) {
        coreContext.postOnCoreThread { core ->
            val friend = model.friend
            if (friend == null) {
                Log.i("$TAG Friend is null, creating conversation with [${model.address}]")
                viewModel.createOneToOneChatRoomWith(model.address)
                return@postOnCoreThread
            }

            val addressesCount = friend.addresses.size
            val numbersCount = friend.phoneNumbers.size

            // Do not consider phone numbers if default account is in secure mode
            val enablePhoneNumbers = core.defaultAccount?.isInSecureMode() != true

            if (addressesCount == 1 && (numbersCount == 0 || !enablePhoneNumbers)) {
                Log.i(
                    "$TAG Only 1 SIP address found for contact [${friend.name}], creating conversation directly"
                )
                val address = friend.addresses.first()
                viewModel.createOneToOneChatRoomWith(address)
            } else if (addressesCount == 0 && numbersCount == 1 && enablePhoneNumbers) {
                val number = friend.phoneNumbers.first()
                val address = core.interpretUrl(number, true)
                if (address != null) {
                    Log.i(
                        "$TAG Only 1 phone number found for contact [${friend.name}], creating conversation directly"
                    )
                    viewModel.createOneToOneChatRoomWith(address)
                } else {
                    Log.e("$TAG Failed to interpret phone number [$number] as SIP address")
                }
            } else {
                val list = friend.getListOfSipAddressesAndPhoneNumbers(listener)
                Log.i(
                    "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                )

                coreContext.postOnMainThread {
                    val numberOrAddressModel = NumberOrAddressPickerDialogModel(list)
                    val dialog =
                        DialogUtils.getNumberOrAddressPickerDialog(
                            requireActivity(),
                            numberOrAddressModel
                        )
                    numberOrAddressPickerDialog = dialog

                    numberOrAddressModel.dismissEvent.observe(viewLifecycleOwner) { event ->
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

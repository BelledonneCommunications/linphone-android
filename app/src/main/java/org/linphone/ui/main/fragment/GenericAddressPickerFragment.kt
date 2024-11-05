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
package org.linphone.ui.main.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.adapter.ConversationsContactsAndSuggestionsListAdapter
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.ui.main.model.SelectedAddressModel
import org.linphone.ui.main.viewmodel.AddressSelectionViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

@UiThread
abstract class GenericAddressPickerFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Generic Address Picker Fragment]"
    }

    private var numberOrAddressPickerDialog: Dialog? = null

    protected lateinit var adapter: ConversationsContactsAndSuggestionsListAdapter

    protected abstract val viewModel: AddressSelectionViewModel

    private lateinit var recyclerView: RecyclerView

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            coreContext.postOnCoreThread {
                if (address != null) {
                    Log.i(
                        "$TAG Selected address [${model.address.asStringUriOnly()}] from friend [${model.friend.name}]"
                    )
                    onAddressSelected(model.address, model.friend)
                }
            }

            numberOrAddressPickerDialog?.dismiss()
            numberOrAddressPickerDialog = null
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
        }
    }

    @WorkerThread
    abstract fun onSingleAddressSelected(address: Address, friend: Friend)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationsContactsAndSuggestionsListAdapter()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter.onClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                handleClickOnContactModel(model)
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            val trimmed = filter.trim()
            viewModel.applyFilter(trimmed)
        }
    }

    override fun onPause() {
        super.onPause()

        numberOrAddressPickerDialog?.dismiss()
        numberOrAddressPickerDialog = null
    }

    @UiThread
    protected fun setupRecyclerView(view: RecyclerView) {
        recyclerView = view
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        recyclerView.addItemDecoration(headerItemDecoration)
    }

    @UiThread
    protected fun attachAdapter() {
        if (::recyclerView.isInitialized) {
            if (recyclerView.adapter != adapter) {
                recyclerView.adapter = adapter
            }
        }
    }

    @WorkerThread
    fun onAddressSelected(address: Address, friend: Friend) {
        if (viewModel.multipleSelectionMode.value == true) {
            val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val model = SelectedAddressModel(address, avatarModel) {
                viewModel.removeAddressModelFromSelection(it)
            }
            viewModel.addAddressModelToSelection(model)
        } else {
            onSingleAddressSelected(address, friend)
        }

        // Clear filter after it was used
        coreContext.postOnMainThread {
            viewModel.clearFilter()
        }
    }

    private fun handleClickOnContactModel(model: ConversationContactOrSuggestionModel) {
        if (model.selected.value == true) {
            Log.i(
                "$TAG User clicked on already selected item [${model.name}], removing it from selection"
            )
            val found = viewModel.selection.value.orEmpty().find {
                it.address.weakEqual(model.address) || it.avatarModel?.friend == model.friend
            }
            if (found != null) {
                coreContext.postOnCoreThread {
                    viewModel.removeAddressModelFromSelection(found)
                }
                return
            } else {
                Log.e("$TAG Failed to find already selected entry matching the one clicked")
            }
        }

        coreContext.postOnCoreThread { core ->
            val friend = model.friend
            if (friend == null) {
                Log.i("$TAG Friend is null, using address [${model.address}]")
                val fakeFriend = core.createFriend()
                fakeFriend.addAddress(model.address)
                onAddressSelected(model.address, fakeFriend)
                return@postOnCoreThread
            }

            val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
            if (singleAvailableAddress != null) {
                Log.i(
                    "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], using it"
                )
                onAddressSelected(singleAvailableAddress, friend)
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

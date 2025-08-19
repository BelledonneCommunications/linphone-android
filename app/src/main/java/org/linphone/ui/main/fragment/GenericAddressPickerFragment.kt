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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.ui.main.adapter.ConversationsContactsAndSuggestionsListAdapter
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.viewmodel.AddressSelectionViewModel
import org.linphone.utils.DialogUtils
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationsContactsAndSuggestionsListAdapter()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter.onClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                viewModel.handleClickOnContactModel(model)
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            val trimmed = filter.trim()
            viewModel.applyFilter(trimmed)
        }

        viewModel.showNumberOrAddressPickerDialogEvent.observe(viewLifecycleOwner) {
            it.consume { list ->
                showNumbersOrAddressesDialog(list)
            }
        }

        viewModel.dismissNumberOrAddressPickerDialogEvent.observe(viewLifecycleOwner) {
            it.consume {
                numberOrAddressPickerDialog?.dismiss()
                numberOrAddressPickerDialog = null
            }
        }
    }

    override fun onPause() {
        super.onPause()

        numberOrAddressPickerDialog?.dismiss()
        numberOrAddressPickerDialog = null
    }

    protected fun setupRecyclerView(view: RecyclerView) {
        recyclerView = view
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        recyclerView.addItemDecoration(headerItemDecoration)
    }

    protected fun attachAdapter() {
        if (::recyclerView.isInitialized) {
            if (recyclerView.adapter != adapter) {
                recyclerView.adapter = adapter
            }
        }
    }

    private fun showNumbersOrAddressesDialog(list: List<ContactNumberOrAddressModel>) {
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

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
package org.linphone.ui.call.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.getValue
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.CallTransferFragmentBinding
import org.linphone.ui.call.adapter.CallsListAdapter
import org.linphone.ui.call.model.CallModel
import org.linphone.ui.call.viewmodel.CallsViewModel
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.ui.main.adapter.ConversationsContactsAndSuggestionsListAdapter
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.history.viewmodel.StartCallViewModel
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.ui.main.model.ConversationContactOrSuggestionModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.RecyclerViewHeaderDecoration
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

@UiThread
class TransferCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Transfer Call Fragment]"
    }

    private lateinit var binding: CallTransferFragmentBinding

    private val viewModel: StartCallViewModel by navGraphViewModels(
        R.id.call_nav_graph
    )

    private lateinit var callViewModel: CurrentCallViewModel

    private lateinit var callsViewModel: CallsViewModel

    private lateinit var callsAdapter: CallsListAdapter

    private lateinit var contactsAdapter: ConversationsContactsAndSuggestionsListAdapter

    private var numberOrAddressPickerDialog: Dialog? = null

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            if (address != null) {
                coreContext.postOnCoreThread {
                    // TODO FIXME: transfer call (blind)
                }
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        callsAdapter = CallsListAdapter()
        contactsAdapter = ConversationsContactsAndSuggestionsListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallTransferFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        callsViewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        binding.viewModel = viewModel
        binding.callsViewModel = callsViewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setHideNumpadClickListener {
            viewModel.hideNumpad()
        }

        binding.callsList.setHasFixedSize(true)
        binding.contactsAndSuggestionsList.setHasFixedSize(true)

        callsAdapter.callClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showConfirmAttendedTransferDialog(model)
            }
        }

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), contactsAdapter)
        binding.contactsAndSuggestionsList.addItemDecoration(headerItemDecoration)

        contactsAdapter.onClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showConfirmBlindTransferDialog(model)
            }
        }

        callsViewModel.callsExceptCurrentOne.observe(viewLifecycleOwner) {
            Log.i("$TAG Calls list updated with [${it.size}] items")
            callsAdapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.callsList.adapter != callsAdapter) {
                binding.callsList.adapter = callsAdapter
            }
        }

        binding.contactsAndSuggestionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.callsList.layoutManager = LinearLayoutManager(requireContext())

        viewModel.modelsList.observe(
            viewLifecycleOwner
        ) {
            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            val count = contactsAdapter.itemCount
            contactsAdapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.contactsAndSuggestionsList.adapter != contactsAdapter) {
                binding.contactsAndSuggestionsList.adapter = contactsAdapter
            }

            if (count == 0) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            val trimmed = filter.trim()
            viewModel.applyFilter(trimmed)
        }

        viewModel.removedCharacterAtCurrentPositionEvent.observe(viewLifecycleOwner) {
            it.consume {
                val selectionStart = binding.searchBar.selectionStart
                val selectionEnd = binding.searchBar.selectionEnd
                if (selectionStart > 0) {
                    binding.searchBar.text =
                        binding.searchBar.text?.delete(
                            selectionStart - 1,
                            selectionEnd
                        )
                    binding.searchBar.setSelection(selectionStart - 1)
                }
            }
        }

        viewModel.appendDigitToSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { digit ->
                val newValue = "${binding.searchBar.text}$digit"
                binding.searchBar.setText(newValue)
                binding.searchBar.setSelection(newValue.length)
            }
        }

        viewModel.requestKeyboardVisibilityChangedEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.searchBar.showKeyboard()
                } else {
                    binding.searchBar.requestFocus()
                    binding.searchBar.hideKeyboard()
                }
            }
        }

        viewModel.isNumpadVisible.observe(viewLifecycleOwner) { visible ->
            val standardBottomSheetBehavior = BottomSheetBehavior.from(binding.numpadLayout.root)
            if (visible) {
                standardBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                standardBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            if (keyboardVisible) {
                viewModel.isNumpadVisible.value = false
            }
        }
    }

    override fun onPause() {
        super.onPause()

        numberOrAddressPickerDialog?.dismiss()
        numberOrAddressPickerDialog = null
    }

    override fun onResume() {
        super.onResume()

        viewModel.title.value = getString(
            R.string.call_transfer_current_call_title,
            callViewModel.displayedName.value ?: callViewModel.displayedAddress.value
        )
    }

    private fun showConfirmAttendedTransferDialog(callModel: CallModel) {
        val label = AppUtils.getFormattedString(
            org.linphone.R.string.call_transfer_confirm_dialog_message,
            callViewModel.displayedName.value.orEmpty(),
            callModel.displayName.value.orEmpty()
        )
        val model = ConfirmationDialogModel(label)
        val dialog = DialogUtils.getConfirmCallTransferCallDialog(
            requireActivity(),
            model
        )

        model.cancelEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                coreContext.postOnCoreThread {
                    val call = callModel.call
                    Log.i(
                        "$TAG Transferring (attended) call to [${call.remoteAddress.asStringUriOnly()}]"
                    )
                    callViewModel.attendedTransferCallTo(call)
                }

                dialog.dismiss()
                findNavController().popBackStack()
            }
        }

        dialog.show()
    }

    private fun showConfirmBlindTransferDialog(contactModel: ConversationContactOrSuggestionModel) {
        val label = AppUtils.getFormattedString(
            org.linphone.R.string.call_transfer_confirm_dialog_message,
            callViewModel.displayedName.value.orEmpty(),
            contactModel.name
        )
        val model = ConfirmationDialogModel(label)
        val dialog = DialogUtils.getConfirmCallTransferCallDialog(
            requireActivity(),
            model
        )

        model.cancelEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                coreContext.postOnCoreThread {
                    val address = contactModel.address
                    Log.i("$TAG Transferring (blind) call to [${address.asStringUriOnly()}]")
                    callViewModel.blindTransferCallTo(address)
                }

                dialog.dismiss()
                findNavController().popBackStack()
            }
        }

        dialog.show()
    }
}

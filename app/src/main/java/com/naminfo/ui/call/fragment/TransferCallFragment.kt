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
package com.naminfo.ui.call.fragment

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
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.LinphoneApplication.Companion.corePreferences
import com.naminfo.R
import org.linphone.core.Address
import org.linphone.core.tools.Log
import com.naminfo.databinding.CallTransferFragmentBinding
import com.naminfo.ui.call.adapter.CallsListAdapter
import com.naminfo.ui.call.model.CallModel
import com.naminfo.ui.call.viewmodel.CallsViewModel
import com.naminfo.ui.call.viewmodel.CurrentCallViewModel
import com.naminfo.ui.main.adapter.ConversationsContactsAndSuggestionsListAdapter
import com.naminfo.ui.main.history.viewmodel.StartCallViewModel
import com.naminfo.utils.ConfirmationDialogModel
import com.naminfo.utils.AppUtils
import com.naminfo.utils.DialogUtils
import com.naminfo.utils.LinphoneUtils
import com.naminfo.utils.RecyclerViewHeaderDecoration
import com.naminfo.utils.hideKeyboard
import com.naminfo.utils.setKeyboardInsetListener
import com.naminfo.utils.showKeyboard

@UiThread
class TransferCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Transfer Call Fragment]"
    }

    private lateinit var binding: CallTransferFragmentBinding

    private val viewModel: StartCallViewModel by navGraphViewModels(
        R.id.call_nav_graph
    )

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                viewModel.isNumpadVisible.value = false
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) { }
    }

    private lateinit var callViewModel: CurrentCallViewModel

    private lateinit var callsViewModel: CallsViewModel

    private lateinit var callsAdapter: CallsListAdapter

    private lateinit var contactsAdapter: ConversationsContactsAndSuggestionsListAdapter

    private var numberOrAddressPickerDialog: Dialog? = null

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
        observeToastEvents(callViewModel)

        callsViewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }
        observeToastEvents(callsViewModel)

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

        binding.contactsAndSuggestionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.callsList.layoutManager = LinearLayoutManager(requireContext())

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), contactsAdapter)
        binding.contactsAndSuggestionsList.addItemDecoration(headerItemDecoration)

        callsAdapter.callClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showConfirmAttendedTransferDialog(model)
            }
        }

        contactsAdapter.onClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showConfirmBlindTransferDialog(model.address, model.name)
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

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.numpadLayout.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)

        viewModel.isNumpadVisible.observe(viewLifecycleOwner) { visible ->
            if (visible) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        viewModel.initiateBlindTransferEvent.observe(viewLifecycleOwner) {
            it.consume { address ->
                showConfirmBlindTransferDialog(address, LinphoneUtils.getDisplayName(address))
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

        coreContext.postOnCoreThread {
            if (corePreferences.automaticallyShowDialpad) {
                viewModel.isNumpadVisible.postValue(true)
            }
        }
    }

    private fun showConfirmAttendedTransferDialog(callModel: CallModel) {
        val from = callViewModel.displayedName.value.orEmpty()
        val to = callModel.displayName.value.orEmpty()
        Log.i("$TAG Asking user confirmation before doing attended transfer of call with [$from] to [$to](${callModel.call.remoteAddress.asStringUriOnly()})")
        val label = AppUtils.getFormattedString(
            R.string.call_transfer_confirm_dialog_message,
            from,
            to
        )
        val model = ConfirmationDialogModel(label)
        val dialog = DialogUtils.getConfirmCallTransferCallDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Attended transfer was cancelled by user")
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

    private fun showConfirmBlindTransferDialog(toAddress: Address, toDisplayName: String) {
        val from = callViewModel.displayedName.value.orEmpty()
        Log.i("$TAG Asking user confirmation before doing blind transfer of call with [$from] to [$toDisplayName](${toAddress.asStringUriOnly()})")
        val label = AppUtils.getFormattedString(
            R.string.call_transfer_confirm_dialog_message,
            from,
            toDisplayName
        )
        val model = ConfirmationDialogModel(label)
        val dialog = DialogUtils.getConfirmCallTransferCallDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Blind transfer was cancelled by user")
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                coreContext.postOnCoreThread {
                    val address = toAddress
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

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
package org.linphone.ui.main.history.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.databinding.StartCallFragmentBinding
import org.linphone.ui.main.fragment.GenericAddressPickerFragment
import org.linphone.ui.main.history.adapter.ContactsAndSuggestionsListAdapter
import org.linphone.ui.main.history.viewmodel.StartCallViewModel
import org.linphone.utils.RecyclerViewHeaderDecoration
import org.linphone.utils.addCharacterAtPosition
import org.linphone.utils.hideKeyboard
import org.linphone.utils.removeCharacterAtPosition
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

@UiThread
class StartCallFragment : GenericAddressPickerFragment() {
    companion object {
        private const val TAG = "[Start Call Fragment]"
    }

    private lateinit var binding: StartCallFragmentBinding

    private val viewModel: StartCallViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    private lateinit var adapter: ContactsAndSuggestionsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = StartCallFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.title.value = getString(R.string.history_call_start_title)
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        binding.setHideNumpadClickListener {
            viewModel.hideNumpad()
        }

        adapter = ContactsAndSuggestionsListAdapter(viewLifecycleOwner)
        binding.contactsAndSuggestionsList.setHasFixedSize(true)
        binding.contactsAndSuggestionsList.adapter = adapter

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter, true)
        binding.contactsAndSuggestionsList.addItemDecoration(headerItemDecoration)

        adapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                handleClickOnContactModel(model)
            }
        }

        binding.contactsAndSuggestionsList.layoutManager = LinearLayoutManager(requireContext())

        viewModel.contactsAndSuggestionsList.observe(
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

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            val trimmed = filter.trim()
            viewModel.applyFilter(trimmed)
        }

        viewModel.removedCharacterAtCurrentPositionEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.searchBar.removeCharacterAtPosition()
            }
        }

        viewModel.appendDigitToSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { digit ->
                binding.searchBar.addCharacterAtPosition(digit)
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

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            // Do not consume it!
            viewModel.updateGroupCallButtonVisibility()
        }
    }

    @WorkerThread
    override fun onAddressSelected(address: Address, friend: Friend) {
        coreContext.startCall(address)
    }

    override fun onPause() {
        super.onPause()

        viewModel.isNumpadVisible.value = false
    }
}

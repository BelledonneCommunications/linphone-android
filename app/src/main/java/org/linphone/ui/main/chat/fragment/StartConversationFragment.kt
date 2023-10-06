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
import org.linphone.core.tools.Log
import org.linphone.databinding.StartChatFragmentBinding
import org.linphone.ui.main.chat.viewmodel.StartConversationViewModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.history.adapter.ContactsAndSuggestionsListAdapter

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
            if (address != null) {
                coreContext.postOnCoreThread {
                    // TODO
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
                // TODO
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
}

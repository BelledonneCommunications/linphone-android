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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.databinding.TopSearchBarBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.ui.main.viewmodel.TopBarViewModel
import org.linphone.utils.Event
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

class TopBarFragment : Fragment() {
    private lateinit var binding: TopSearchBarBinding

    private lateinit var viewModel: TopBarViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TopSearchBarBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = requireActivity().run {
            ViewModelProvider(this)[TopBarViewModel::class.java]
        }

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        viewModel.openDrawerMenuEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).toggleDrawerMenu()
            }
        }

        viewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.search.showKeyboard(requireActivity().window)
                } else {
                    binding.search.hideKeyboard()
                }
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            sharedViewModel.searchFilter.value = Event(filter)
        }

        sharedViewModel.currentlyDisplayedFragment.observe(viewLifecycleOwner) {
            binding.title.text = when (it) {
                R.id.contactsFragment -> {
                    "Contacts"
                }
                R.id.callsFragment -> {
                    "Calls"
                }
                R.id.conversationsFragment -> {
                    "Conversations"
                }
                else -> {
                    ""
                }
            }
        }
    }
}

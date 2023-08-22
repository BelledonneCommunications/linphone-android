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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.databinding.BottomNavBarBinding
import org.linphone.ui.main.viewmodel.BottomNavBarViewModel
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.utils.Event
import org.linphone.utils.setKeyboardInsetListener

@UiThread
class BottomNavBarFragment : Fragment() {
    private lateinit var binding: BottomNavBarBinding

    private lateinit var viewModel: BottomNavBarViewModel

    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomNavBarBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = requireActivity().run {
            ViewModelProvider(this)[BottomNavBarViewModel::class.java]
        }
        binding.viewModel = viewModel

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            binding.bottomNavBar.visibility = if (!portraitOrientation || !keyboardVisible) View.VISIBLE else View.GONE
        }

        binding.setOnContactsClicked {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.contactsFragment) {
                sharedViewModel.navigateToContactsEvent.value = Event(true)
            }
        }

        binding.setOnCallsClicked {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.callsFragment) {
                sharedViewModel.navigateToCallsEvent.value = Event(true)
            }
        }

        binding.setOnConversationsClicked {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.conversationsFragment) {
                sharedViewModel.navigateToConversationsEvent.value = Event(true)
            }
        }

        binding.setOnMeetingsClicked {
            // TODO
        }

        sharedViewModel.currentlyDisplayedFragment.observe(viewLifecycleOwner) {
            viewModel.contactsSelected.value = it == R.id.contactsFragment
            viewModel.callsSelected.value = it == R.id.callsFragment
            viewModel.conversationsSelected.value = it == R.id.conversationsFragment
        }

        sharedViewModel.resetMissedCallsCountEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.resetMissedCallsCount()
            }
        }
    }
}

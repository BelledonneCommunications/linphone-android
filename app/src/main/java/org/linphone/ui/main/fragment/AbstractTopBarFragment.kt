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

import androidx.annotation.UiThread
import com.google.android.material.textfield.TextInputLayout
import org.linphone.R
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel
import org.linphone.utils.Event
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

@UiThread
abstract class AbstractTopBarFragment : GenericFragment() {
    fun setViewModelAndTitle(
        searchBar: TextInputLayout,
        viewModel: AbstractTopBarViewModel,
        title: String
    ) {
        viewModel.title.value = title

        viewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    searchBar.showKeyboard()
                } else {
                    searchBar.hideKeyboard()
                }
            }
        }

        viewModel.openDrawerMenuEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).toggleDrawerMenu()
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            viewModel.applyFilter(filter.trim())
        }

        viewModel.navigateToContactsEvent.observe(viewLifecycleOwner) {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.contactsFragment) {
                sharedViewModel.navigateToContactsEvent.value = Event(true)
            }
        }

        viewModel.navigateToHistoryEvent.observe(viewLifecycleOwner) {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.historyFragment) {
                sharedViewModel.navigateToHistoryEvent.value = Event(true)
            }
        }

        viewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.conversationsFragment) {
                sharedViewModel.navigateToConversationsEvent.value = Event(true)
            }
        }

        viewModel.navigateToMeetingsEvent.observe(viewLifecycleOwner) {
            if (sharedViewModel.currentlyDisplayedFragment.value != R.id.meetingsFragment) {
                sharedViewModel.navigateToMeetingsEvent.value = Event(true)
            }
        }

        sharedViewModel.currentlyDisplayedFragment.observe(viewLifecycleOwner) {
            viewModel.contactsSelected.value = it == R.id.contactsFragment
            viewModel.callsSelected.value = it == R.id.historyFragment
            viewModel.conversationsSelected.value = it == R.id.conversationsFragment
            viewModel.meetingsSelected.value = it == R.id.meetingsFragment
        }

        sharedViewModel.resetMissedCallsCountEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.resetMissedCallsCount()
            }
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            // Do not consume it!
            viewModel.updateAvailableMenus()
        }
    }
}

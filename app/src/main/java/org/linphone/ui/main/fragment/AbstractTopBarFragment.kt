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
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.textfield.TextInputLayout
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.fragment.ConversationsListFragmentDirections
import org.linphone.ui.main.contacts.fragment.ContactsListFragmentDirections
import org.linphone.ui.main.history.fragment.HistoryListFragmentDirections
import org.linphone.ui.main.meetings.fragment.MeetingsListFragmentDirections
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel
import org.linphone.utils.SlidingPaneBackPressedCallback
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

@UiThread
abstract class AbstractTopBarFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Abstract TobBar Fragment]"
    }

    private var currentFragmentId: Int = 0

    abstract fun onDefaultAccountChanged()

    fun initSlidingPane(slidingPane: SlidingPaneLayout) {
        val slidingPaneBackPressedCallback = SlidingPaneBackPressedCallback(slidingPane)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            slidingPaneBackPressedCallback
        )

        view?.doOnPreDraw {
            slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
            sharedViewModel.isSlidingPaneSlideable.value = slidingPane.isSlideable
            slidingPaneBackPressedCallback.isEnabled = slidingPane.isSlideable
        }

        sharedViewModel.closeSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (slidingPane.isOpen) {
                    Log.i("$TAG Closing sliding pane")
                    slidingPane.closePane()
                }
            }
        }

        sharedViewModel.openSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (!slidingPane.isOpen) {
                    Log.i("$TAG Opening sliding pane")
                    slidingPane.openPane()
                }
            }
        }
    }

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
            it.consume {
                if (currentFragmentId != R.id.contactsListFragment) {
                    goToContactsList()
                }
            }
        }

        viewModel.navigateToHistoryEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (currentFragmentId != R.id.historyListFragment) {
                    goToHistoryList()
                }
            }
        }

        viewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (currentFragmentId != R.id.conversationsListFragment) {
                    goToConversationsList()
                }
            }
        }

        viewModel.navigateToMeetingsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (currentFragmentId != R.id.meetingsListFragment) {
                    goToMeetingsList()
                }
            }
        }

        sharedViewModel.currentlyDisplayedFragment.observe(viewLifecycleOwner) {
            viewModel.contactsSelected.value = it == R.id.contactsListFragment
            viewModel.callsSelected.value = it == R.id.historyListFragment
            viewModel.conversationsSelected.value = it == R.id.conversationsListFragment
            viewModel.meetingsSelected.value = it == R.id.meetingsListFragment
        }

        sharedViewModel.resetMissedCallsCountEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.resetMissedCallsCount()
            }
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Default account changed")
                onDefaultAccountChanged()
            }
        }
    }

    fun initBottomNavBar(navBar: View) {
        view?.setKeyboardInsetListener { keyboardVisible ->
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            navBar.visibility = if (!portraitOrientation || !keyboardVisible) View.VISIBLE else View.GONE
        }
    }

    fun initNavigation(@IdRes fragmentId: Int) {
        currentFragmentId = fragmentId

        sharedViewModel.navigateToContactsEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToContactsList()
            }
        }

        sharedViewModel.navigateToHistoryEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToHistoryList()
            }
        }

        sharedViewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToConversationsList()
            }
        }

        sharedViewModel.navigateToMeetingsEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToMeetingsList()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (currentFragmentId > 0) {
            sharedViewModel.currentlyDisplayedFragment.value = currentFragmentId
        }
    }

    private fun goToContactsList() {
        Log.i("$TAG Navigating to contacts list")
        when (currentFragmentId) {
            R.id.conversationsListFragment -> {
                Log.i("$TAG Leaving conversations list")
                val action = ConversationsListFragmentDirections.actionConversationsListFragmentToContactsListFragment()
                navigateTo(action)
            }
            R.id.meetingsListFragment -> {
                Log.i("$TAG Leaving meetings list")
                val action = MeetingsListFragmentDirections.actionMeetingsListFragmentToContactsListFragment()
                navigateTo(action)
            }
            R.id.historyListFragment -> {
                Log.i("$TAG Leaving history list")
                val action = HistoryListFragmentDirections.actionHistoryListFragmentToContactsListFragment()
                navigateTo(action)
            }
        }
    }

    private fun goToHistoryList() {
        Log.i("$TAG Navigating to history list")
        when (currentFragmentId) {
            R.id.conversationsListFragment -> {
                Log.i("$TAG Leaving conversations list")
                val action = ConversationsListFragmentDirections.actionConversationsListFragmentToHistoryListFragment()
                navigateTo(action)
            }
            R.id.contactsListFragment -> {
                Log.i("$TAG Leaving contacts list")
                val action = ContactsListFragmentDirections.actionContactsListFragmentToHistoryListFragment()
                navigateTo(action)
            }
            R.id.meetingsListFragment -> {
                Log.i("$TAG Leaving meetings list")
                val action = MeetingsListFragmentDirections.actionMeetingsListFragmentToHistoryListFragment()
                navigateTo(action)
            }
        }
    }

    private fun goToConversationsList() {
        Log.i("$TAG Navigating to conversations list")
        when (currentFragmentId) {
            R.id.contactsListFragment -> {
                Log.i("$TAG Leaving contacts list")
                val action = ContactsListFragmentDirections.actionContactsListFragmentToConversationsListFragment()
                navigateTo(action)
            }
            R.id.meetingsListFragment -> {
                Log.i("$TAG Leaving meetings list")
                val action = MeetingsListFragmentDirections.actionMeetingsListFragmentToConversationsListFragment()
                navigateTo(action)
            }
            R.id.historyListFragment -> {
                Log.i("$TAG Leaving history list")
                val action = HistoryListFragmentDirections.actionHistoryListFragmentToConversationsListFragment()
                navigateTo(action)
            }
        }
    }

    private fun goToMeetingsList() {
        Log.i("$TAG Navigating to meetings list")
        when (currentFragmentId) {
            R.id.conversationsListFragment -> {
                Log.i("$TAG Leaving conversations list")
                val action = ConversationsListFragmentDirections.actionConversationsListFragmentToMeetingsListFragment()
                navigateTo(action)
            }
            R.id.contactsListFragment -> {
                Log.i("$TAG Leaving contacts list")
                val action = ContactsListFragmentDirections.actionContactsListFragmentToMeetingsListFragment()
                navigateTo(action)
            }
            R.id.historyListFragment -> {
                Log.i("$TAG Leaving history list")
                val action = HistoryListFragmentDirections.actionHistoryListFragmentToMeetingsListFragment()
                navigateTo(action)
            }
        }
    }

    private fun navigateTo(action: NavDirections) {
        try {
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e("$TAG Failed to navigate: $e")
        }
    }
}

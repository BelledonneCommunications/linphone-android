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
package org.linphone.ui.main.contacts.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactsFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.SlidingPaneBackPressedCallback

@UiThread
class ContactsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Contacts Fragment]"
    }

    private lateinit var binding: ContactsFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.newContactFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.root.doOnPreDraw {
            val slidingPane = binding.slidingPaneLayout
            slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

            sharedViewModel.isSlidingPaneSlideable.value = slidingPane.isSlideable

            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                SlidingPaneBackPressedCallback(slidingPane)
            )
        }

        sharedViewModel.closeSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (binding.slidingPaneLayout.isOpen) {
                    Log.i("$TAG Closing sliding pane")
                    binding.slidingPaneLayout.closePane()
                }
            }
        }

        sharedViewModel.openSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (!binding.slidingPaneLayout.isOpen) {
                    Log.i("$TAG Opening sliding pane")
                    binding.slidingPaneLayout.openPane()
                }
            }
        }

        sharedViewModel.showContactEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { refKey ->
                Log.i("$TAG Displaying contact with ref key [$refKey]")
                val navController = binding.contactsNavContainer.findNavController()
                val action = ContactFragmentDirections.actionGlobalContactFragment(
                    refKey
                )
                navController.navigate(action)
            }
        }

        sharedViewModel.showNewContactEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("$TAG Opening contact editor for creating new contact")
                val action = ContactsFragmentDirections.actionContactsFragmentToNewContactFragment()
                findNavController().navigate(action)
            }
        }

        sharedViewModel.navigateToHistoryEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                    // To prevent any previously seen contact to show up when navigating back to here later
                    binding.contactsNavContainer.findNavController().popBackStack()

                    val action = ContactsFragmentDirections.actionContactsFragmentToHistoryFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                    // To prevent any previously seen contact to show up when navigating back to here later
                    binding.contactsNavContainer.findNavController().popBackStack()

                    val action = ContactsFragmentDirections.actionContactsFragmentToConversationsFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToMeetingsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                    // To prevent any previously seen contact to show up when navigating back to here later
                    binding.contactsNavContainer.findNavController().popBackStack()

                    val action = ContactsFragmentDirections.actionContactsFragmentToMeetingsFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.currentlyDisplayedFragment.value = R.id.contactsFragment
    }
}

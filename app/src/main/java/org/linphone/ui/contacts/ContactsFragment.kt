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
package org.linphone.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.transition.AutoTransition
import org.linphone.R
import org.linphone.databinding.ContactsFragmentBinding
import org.linphone.ui.MainActivity
import org.linphone.ui.contacts.viewmodel.ContactsListViewModel
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

class ContactsFragment : Fragment() {
    private lateinit var binding: ContactsFragmentBinding
    private val listViewModel: ContactsListViewModel by navGraphViewModels(
        R.id.contactsFragment
    )

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.newContactFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactsFragmentBinding.inflate(layoutInflater)
        sharedElementEnterTransition = AutoTransition()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        // postponeEnterTransition()

        binding.setOnNewContactClicked {
            if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                findNavController().navigate(
                    R.id.action_contactsFragment_to_newContactFragment
                )
            }
        }

        binding.setOnConversationsClicked {
            if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                val extras = FragmentNavigatorExtras(
                    binding.bottomNavBar.root to "bottom_nav_bar"
                )
                val action = ContactsFragmentDirections.actionContactsFragmentToConversationsFragment()
                findNavController().navigate(action, extras)
            }
        }

        binding.setOnAvatarClickListener {
            (requireActivity() as MainActivity).toggleDrawerMenu()
        }

        listViewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { take ->
                if (take) {
                    // To automatically open keyboard
                    binding.topBar.search.showKeyboard(requireActivity().window)
                } else {
                    binding.topBar.search.hideKeyboard()
                }
            }
        }

        /*(view.parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
        }*/
    }
}

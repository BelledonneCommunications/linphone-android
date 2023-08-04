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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import androidx.transition.AutoTransition
import org.linphone.R
import org.linphone.databinding.ContactsFragmentBinding
import org.linphone.ui.MainActivity
import org.linphone.ui.contacts.adapter.ContactsListAdapter
import org.linphone.ui.contacts.viewmodel.ContactsListViewModel
import org.linphone.ui.viewmodel.SharedMainViewModel
import org.linphone.utils.SlidingPaneBackPressedCallback
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

class ContactsFragment : Fragment() {
    private lateinit var binding: ContactsFragmentBinding

    private lateinit var sharedViewModel: SharedMainViewModel

    private val listViewModel: ContactsListViewModel by navGraphViewModels(
        R.id.contactsFragment
    )

    private lateinit var adapter: ContactsListAdapter

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

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            listViewModel.bottomNavBarVisible.value = !portraitOrientation || !keyboardVisible
        }

        binding.root.doOnPreDraw {
            val slidingPane = binding.slidingPaneLayout

            sharedViewModel.isSlidingPaneSlideable.value = slidingPane.isSlideable

            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                SlidingPaneBackPressedCallback(slidingPane)
            )

            slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        }

        adapter = ContactsListAdapter(viewLifecycleOwner)
        binding.contactsView.contactsList.setHasFixedSize(true)
        binding.contactsView.contactsList.adapter = adapter

        adapter.contactLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ContactsListMenuDialogFragment(model.friend) {
                    adapter.resetSelection()
                }
                modalBottomSheet.show(parentFragmentManager, ContactsListMenuDialogFragment.TAG)
            }
        }

        adapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                    val navHostFragment = childFragmentManager.findFragmentById(
                        R.id.contacts_nav_container
                    ) as NavHostFragment
                    val action = ContactFragmentDirections.actionGlobalContactFragment(
                        model.id ?: ""
                    )
                    navHostFragment.navController.navigate(action)

                    if (!binding.slidingPaneLayout.isOpen) {
                        binding.slidingPaneLayout.openPane()
                    }
                }
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.contactsView.contactsList.layoutManager = layoutManager

        listViewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        listViewModel.searchFilter.observe(
            viewLifecycleOwner
        ) {
            listViewModel.applyFilter()
        }

        listViewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.topBar.search.showKeyboard(requireActivity().window)
                } else {
                    binding.topBar.search.hideKeyboard()
                }
            }
        }

        binding.setOnNewContactClicked {
            if (findNavController().currentDestination?.id == R.id.contactsFragment) {
                findNavController().navigate(R.id.action_contactsFragment_to_newContactFragment)
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
    }
}

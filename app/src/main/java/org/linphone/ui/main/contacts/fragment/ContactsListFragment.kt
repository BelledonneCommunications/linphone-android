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
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.ContactsListFragmentBinding
import org.linphone.ui.main.contacts.adapter.ContactsListAdapter
import org.linphone.ui.main.contacts.viewmodel.ContactsListViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.Event

@UiThread
class ContactsListFragment : GenericFragment() {
    private lateinit var binding: ContactsListFragmentBinding

    private val listViewModel: ContactsListViewModel by navGraphViewModels(
        R.id.contactsListFragment
    )

    private lateinit var adapter: ContactsListAdapter
    private lateinit var favouritesAdapter: ContactsListAdapter

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
        binding = ContactsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        adapter = ContactsListAdapter(viewLifecycleOwner)
        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.adapter = adapter
        configureAdapter(adapter)

        val layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.layoutManager = layoutManager

        favouritesAdapter = ContactsListAdapter(viewLifecycleOwner, favourites = true)
        binding.favouritesContactsList.setHasFixedSize(true)
        binding.favouritesContactsList.adapter = favouritesAdapter
        configureAdapter(favouritesAdapter)

        val favouritesLayoutManager = LinearLayoutManager(requireContext())
        favouritesLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
        binding.favouritesContactsList.layoutManager = favouritesLayoutManager

        listViewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        listViewModel.favourites.observe(
            viewLifecycleOwner
        ) {
            favouritesAdapter.submitList(it)
        }

        sharedViewModel.searchFilter.observe(viewLifecycleOwner) {
            it.consume { filter ->
                listViewModel.applyFilter(filter)
            }
        }

        binding.setOnNewContactClicked {
            sharedViewModel.showNewContactEvent.value = Event(true)
        }
    }

    private fun configureAdapter(adapter: ContactsListAdapter) {
        adapter.contactLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ContactsListMenuDialogFragment(model.friend.starred, {
                    adapter.resetSelection()
                }, {
                    // onFavourite
                    coreContext.postOnCoreThread {
                        model.friend.edit()
                        model.friend.starred = !model.friend.starred
                        model.friend.done()
                        coreContext.contactsManager.notifyContactsListChanged()
                    }
                }, {
                    // onShare
                    // TODO
                }, {
                    // onDelete
                    coreContext.postOnCoreThread {
                        model.friend.remove()
                        coreContext.contactsManager.notifyContactsListChanged()
                    }
                })
                modalBottomSheet.show(parentFragmentManager, ContactsListMenuDialogFragment.TAG)
            }
        }

        adapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                sharedViewModel.showContactEvent.value = Event(model.id ?: "")
            }
        }
    }
}

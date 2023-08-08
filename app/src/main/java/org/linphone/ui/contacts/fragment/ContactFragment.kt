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
package org.linphone.ui.contacts.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.transition.ChangeBounds
import org.linphone.databinding.ContactFragmentBinding
import org.linphone.ui.contacts.viewmodel.ContactViewModel
import org.linphone.ui.fragment.GenericFragment
import org.linphone.utils.Event

class ContactFragment : GenericFragment() {
    private lateinit var binding: ContactFragmentBinding

    private lateinit var viewModel: ContactViewModel

    private val args: ContactFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = ChangeBounds()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ContactViewModel::class.java]
        binding.viewModel = viewModel

        val refKey = args.contactRefKey
        viewModel.findContactByRefKey(refKey)

        binding.setBackClickListener {
            sharedViewModel.closeSlidingPaneEvent.value = Event(true)
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.contact.observe(viewLifecycleOwner) {
            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
                sharedViewModel.openSlidingPaneEvent.value = Event(true)
            }
        }
    }
}

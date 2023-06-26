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
package org.linphone.ui.conversations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.ContactsSelectionAdapter
import org.linphone.databinding.NewConversationFragmentBinding
import org.linphone.ui.conversations.viewmodel.NewConversationViewModel

class NewConversationFragment : Fragment() {
    private lateinit var binding: NewConversationFragmentBinding
    private lateinit var adapter: ContactsSelectionAdapter
    private val viewModel: NewConversationViewModel by navGraphViewModels(
        R.id.conversationsFragment
    )

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.conversationFragment) {
            // Holds fragment in place while created conversation fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = NewConversationFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        postponeEnterTransition()

        adapter = ContactsSelectionAdapter(viewLifecycleOwner)
        binding.contactsList.adapter = adapter
        binding.contactsList.setHasFixedSize(true)

        val layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.layoutManager = layoutManager

        viewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        viewModel.filter.observe(
            viewLifecycleOwner
        ) {
            val filter = it.orEmpty().trim()
            coreContext.postOnCoreThread {
                viewModel.applyFilter(filter)
            }
        }

        binding.setCancelClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        viewModel.goToChatRoom.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.newConversationFragment) {
                    findNavController().navigate(
                        R.id.action_newConversationFragment_to_conversationFragment
                    )
                }
            }
        }
    }
}

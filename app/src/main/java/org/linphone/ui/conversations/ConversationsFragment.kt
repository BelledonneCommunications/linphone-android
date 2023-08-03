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
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import org.linphone.R
import org.linphone.databinding.ConversationsFragmentBinding
import org.linphone.ui.conversations.adapter.ConversationsListAdapter
import org.linphone.ui.conversations.viewmodel.ConversationsListViewModel
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

class ConversationsFragment : Fragment() {
    private lateinit var binding: ConversationsFragmentBinding
    private val listViewModel: ConversationsListViewModel by navGraphViewModels(
        R.id.conversationsFragment
    )
    private lateinit var adapter: ConversationsListAdapter

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            scrollToTop()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == 0 && itemCount == 1) {
                scrollToTop()
            }
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            scrollToTop()
        }
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.newConversationFragment ||
            findNavController().currentDestination?.id == R.id.conversationFragment
        ) {
            // Holds fragment in place while (new) conversation fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onDestroyView() {
        binding.conversationsList.adapter = null
        adapter.unregisterAdapterDataObserver(observer)
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ConversationsFragmentBinding.inflate(layoutInflater)
        sharedElementEnterTransition = AutoTransition()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        postponeEnterTransition()

        adapter = ConversationsListAdapter(viewLifecycleOwner)
        adapter.registerAdapterDataObserver(observer)
        binding.conversationsList.setHasFixedSize(true)
        binding.conversationsList.adapter = adapter

        adapter.chatRoomClickedEvent.observe(viewLifecycleOwner) {
            it.consume { data ->
                val bundle = bundleOf()
                bundle.putString("localSipUri", data.localSipUri)
                bundle.putString("remoteSipUri", data.remoteSipUri)

                if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                    findNavController().navigate(
                        R.id.action_conversationsFragment_to_conversationFragment,
                        bundle
                    )
                }
            }
        }

        adapter.chatRoomLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { data ->
                val modalBottomSheet = ConversationMenuDialogFragment(data.chatRoom) {
                    adapter.resetSelection()
                }
                modalBottomSheet.show(parentFragmentManager, ConversationMenuDialogFragment.TAG)
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.conversationsList.layoutManager = layoutManager

        listViewModel.chatRoomsList.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        listViewModel.notifyItemChangedEvent.observe(viewLifecycleOwner) {
            it.consume { index ->
                adapter.notifyItemChanged(index)
            }
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

        binding.setOnNewConversationClicked {
            if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                val action = ConversationsFragmentDirections.actionConversationsFragmentToNewConversationFragment()
                findNavController().navigate(action)
            }
        }

        binding.setOnContactsClicked {
            if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                val extras = FragmentNavigatorExtras(
                    binding.bottomNavBar.root to "bottom_nav_bar"
                )
                val action = ConversationsFragmentDirections.actionConversationsFragmentToContactsFragment()
                findNavController().navigate(action, extras)
            }
        }
    }

    private fun scrollToTop() {
        binding.conversationsList.scrollToPosition(0)
    }
}

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
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ConversationsFragmentBinding

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
        return AnimationUtils.loadAnimation(activity, R.anim.hold)
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
                findNavController().navigate(
                    R.id.action_conversationsFragment_to_conversationFragment
                )
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

        binding.setOnNewConversationClicked {
            findNavController().navigate(
                R.id.action_conversationsFragment_to_newConversationFragment
            )
        }
    }

    private fun scrollToTop() {
        binding.conversationsList.scrollToPosition(0)
    }
}

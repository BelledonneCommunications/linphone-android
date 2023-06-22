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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.databinding.ConversationsFragmentBinding

class ConversationsFragment : Fragment() {
    private lateinit var binding: ConversationsFragmentBinding
    private val listViewModel: ConversationsListViewModel by navGraphViewModels(
        R.id.conversationsFragment
    )
    private lateinit var adapter: ConversationsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ConversationsFragmentBinding.inflate(layoutInflater)

        val window = requireActivity().window
        window.statusBarColor = ContextCompat.getColor(
            requireContext(),
            R.color.gray_1
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        adapter = ConversationsListAdapter(viewLifecycleOwner)
        binding.conversationsList.setHasFixedSize(true)
        binding.conversationsList.adapter = adapter

        adapter.chatRoomClickedEvent.observe(viewLifecycleOwner) {
            it.consume { data ->
            }
        }
        adapter.chatRoomMenuClickedEvent.observe(viewLifecycleOwner) {
            it.consume { data ->
                val modalBottomSheet = ConversationMenuDialogFragment(data.chatRoom) { muted ->
                    data.isMuted.postValue(muted)
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
        }

        listViewModel.notifyItemChangedEvent.observe(viewLifecycleOwner) {
            it.consume { index ->
                adapter.notifyItemChanged(index)
            }
        }
    }
}

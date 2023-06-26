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
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.databinding.ConversationFragmentBinding
import org.linphone.ui.conversations.adapter.ChatEventLogsListAdapter
import org.linphone.ui.conversations.viewmodel.ConversationViewModel

class ConversationFragment : Fragment() {
    private lateinit var binding: ConversationFragmentBinding
    private val viewModel: ConversationViewModel by navGraphViewModels(
        R.id.conversationFragment
    )
    private lateinit var adapter: ChatEventLogsListAdapter

    override fun onDestroyView() {
        binding.messagesList.adapter = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ConversationFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val localSipUri = arguments?.getString("localSipUri")
            ?: savedInstanceState?.getString("localSipUri")
        val remoteSipUri = arguments?.getString("remoteSipUri")
            ?: savedInstanceState?.getString("remoteSipUri")
        if (localSipUri != null && remoteSipUri != null) {
            viewModel.loadChatRoom(localSipUri, remoteSipUri)
        } else {
            // Chat room not found, going back
            // TODO FIXME : show error
            (view.parent as? ViewGroup)?.doOnPreDraw {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        arguments?.clear()

        postponeEnterTransition()

        adapter = ChatEventLogsListAdapter(viewLifecycleOwner)
        binding.messagesList.setHasFixedSize(false)
        binding.messagesList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.messagesList.layoutManager = layoutManager

        viewModel.events.observe(
            viewLifecycleOwner
        ) {
            adapter.submitList(it)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
                binding.messagesList.scrollToPosition(adapter.itemCount - 1)
            }
        }

        binding.setBackClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}

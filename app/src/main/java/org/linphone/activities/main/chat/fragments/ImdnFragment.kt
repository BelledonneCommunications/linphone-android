/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.activities.main.chat.adapters.ImdnAdapter
import org.linphone.activities.main.chat.viewmodels.ImdnViewModel
import org.linphone.activities.main.chat.viewmodels.ImdnViewModelFactory
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomImdnFragmentBinding
import org.linphone.utils.RecyclerViewHeaderDecoration

class ImdnFragment : Fragment() {
    private lateinit var binding: ChatRoomImdnFragmentBinding
    private lateinit var viewModel: ImdnViewModel
    private lateinit var adapter: ImdnAdapter
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatRoomImdnFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val chatRoom = sharedViewModel.selectedChatRoom.value
        chatRoom ?: return

        if (arguments != null) {
            val messageId = arguments?.getString("MessageId")
            val message = if (messageId != null) chatRoom.findMessage(messageId) else null
            if (message != null) {
                Log.i("[IMDN] Found message $message with id $messageId")
                viewModel = ViewModelProvider(
                    this,
                    ImdnViewModelFactory(message)
                )[ImdnViewModel::class.java]
                binding.viewModel = viewModel
            } else {
                Log.e("[IMDN] Couldn't find message with id $messageId in chat room $chatRoom")
                findNavController().popBackStack()
                return
            }
        } else {
            Log.e("[IMDN] Couldn't find message id in intent arguments")
            findNavController().popBackStack()
            return
        }

        adapter = ImdnAdapter()
        binding.participantsList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        binding.participantsList.layoutManager = layoutManager

        // Divider between items
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        binding.participantsList.addItemDecoration(dividerItemDecoration)

        // Displays state header
        val headerItemDecoration = RecyclerViewHeaderDecoration(adapter)
        binding.participantsList.addItemDecoration(headerItemDecoration)

        viewModel.participants.observe(viewLifecycleOwner, {
            adapter.submitList(it)
        })

        binding.setBackClickListener {
            findNavController().popBackStack()
        }
    }
}

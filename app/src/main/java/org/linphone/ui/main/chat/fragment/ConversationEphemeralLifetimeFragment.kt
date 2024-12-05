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
package org.linphone.ui.main.chat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatEphemeralLifetimeFragmentBinding
import org.linphone.ui.main.chat.viewmodel.ConversationEphemeralLifetimeViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.utils.Event

@UiThread
class ConversationEphemeralLifetimeFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Conversation Ephemeral Lifetime Fragment]"
    }

    private lateinit var binding: ChatEphemeralLifetimeFragmentBinding

    private lateinit var viewModel: ConversationEphemeralLifetimeViewModel

    private val args: ConversationEphemeralLifetimeFragmentArgs by navArgs()

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatEphemeralLifetimeFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationEphemeralLifetimeViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val lifetime = args.currentEphemeralLifetime
        Log.i("$TAG Current lifetime for ephemeral messages is [$lifetime]")
        viewModel.currentlySelectedValue.value = lifetime

        binding.setBackClickListener {
            goBack()
        }
    }

    override fun onPause() {
        sharedViewModel.newChatMessageEphemeralLifetimeToSet.value = Event(
            viewModel.currentlySelectedValue.value ?: 0L
        )
        super.onPause()
    }
}

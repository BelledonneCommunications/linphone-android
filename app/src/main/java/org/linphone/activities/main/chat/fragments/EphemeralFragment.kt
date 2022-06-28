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
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.chat.viewmodels.EphemeralViewModel
import org.linphone.activities.main.chat.viewmodels.EphemeralViewModelFactory
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomEphemeralFragmentBinding
import org.linphone.utils.Event

class EphemeralFragment : SecureFragment<ChatRoomEphemeralFragmentBinding>() {
    private lateinit var viewModel: EphemeralViewModel

    override fun getLayoutId(): Int {
        return R.layout.chat_room_ephemeral_fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isSecure = true
        binding.lifecycleOwner = viewLifecycleOwner

        val chatRoom = sharedViewModel.selectedChatRoom.value
        if (chatRoom == null) {
            Log.e("[Ephemeral] Chat room is null, aborting!")
            findNavController().navigateUp()
            return
        }

        viewModel = ViewModelProvider(
            this,
            EphemeralViewModelFactory(chatRoom)
        )[EphemeralViewModel::class.java]
        binding.viewModel = viewModel

        binding.setValidClickListener {
            viewModel.updateChatRoomEphemeralDuration()
            sharedViewModel.refreshChatRoomInListEvent.value = Event(true)
            goBack()
        }
    }
}
